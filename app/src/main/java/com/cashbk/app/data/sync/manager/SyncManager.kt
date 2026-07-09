package com.cashbk.app.data.sync.manager

import android.content.Context
import android.util.Log
import com.cashbk.app.data.local.room.AppDatabase
import com.cashbk.app.data.local.room.entity.*
import com.cashbk.app.data.sync.network.NetworkMonitor
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import java.util.concurrent.ConcurrentHashMap

class SyncManager(
    private val context: Context,
    private val database: AppDatabase,
    private val networkMonitor: NetworkMonitor
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val gson = Gson()
    private val firebaseDb = FirebaseDatabase.getInstance().reference
    private val currentUid: String?
        get() = FirebaseAuth.getInstance().currentUser?.uid

    private val firebaseListeners = ConcurrentHashMap<String, ValueEventListener>()

    init {
        // Observe network state to trigger sync when online
        scope.launch {
            networkMonitor.isOnline.collectLatest { isOnline ->
                if (isOnline && currentUid != null) {
                    syncPendingQueue()
                }
            }
        }
    }

    /**
     * Pushes all PENDING or FAILED local changes in the queue to Firebase.
     */
    suspend fun syncPendingQueue() = withContext(Dispatchers.IO) {
        val pendingItems = database.syncQueueDao().getPendingAndFailed()
        Log.d("SyncManager", "Processing ${pendingItems.size} pending items in sync queue")

        for (item in pendingItems) {
            database.syncQueueDao().update(item.copy(status = "SYNCING", lastAttempt = System.currentTimeMillis()))
            val success = try {
                uploadEntity(item)
                true
            } catch (e: Exception) {
                Log.e("SyncManager", "Failed to sync queue item ${item.id}: ${e.message}")
                false
            }

            if (success) {
                database.syncQueueDao().delete(item)
            } else {
                val newRetryCount = item.retryCount + 1
                val newStatus = if (newRetryCount >= 3) "FAILED" else "PENDING"
                database.syncQueueDao().update(
                    item.copy(
                        status = newStatus,
                        retryCount = newRetryCount,
                        lastAttempt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    private suspend fun uploadEntity(item: SyncQueueEntity) {
        val path = getFirebasePath(item.entityType, item.entityId, item.payload)
        val dataMap = gson.fromJson(item.payload, Map::class.java) as Map<String, Any>

        if (item.operation == "DELETE") {
            firebaseDb.child(path).removeValue().await()
        } else {
            firebaseDb.child(path).setValue(dataMap).await()
        }
    }

    private fun getFirebasePath(entityType: String, entityId: String, payload: String): String {
        return when (entityType) {
            "business" -> "businesses/$entityId"
            "notebook" -> "notebooks/$entityId"
            "transaction" -> {
                val notebookId = gson.fromJson(payload, Map::class.java)["notebookId"] as? String ?: ""
                "transactions/$notebookId/$entityId"
            }
            "category" -> {
                val notebookId = gson.fromJson(payload, Map::class.java)["notebookId"] as? String ?: ""
                "categories/$notebookId/$entityId"
            }
            "party" -> {
                val notebookId = gson.fromJson(payload, Map::class.java)["notebookId"] as? String ?: ""
                "parties/$notebookId/$entityId"
            }
            "user" -> "users/$entityId"
            else -> throw IllegalArgumentException("Unknown entity type: $entityType")
        }
    }

    /**
     * Starts listening to Firebase Realtime Database for updates, merging them into Room.
     */
    fun startRealtimeSync() {
        val uid = currentUid ?: return
        Log.d("SyncManager", "Starting Realtime sync for user: $uid")

        // 1. Listen to user profiles
        listenToPath("users/$uid") { snapshot ->
            scope.launch {
                val userMap = snapshot.value as? Map<*, *> ?: return@launch
                val remoteUser = snapshot.getValue(com.cashbk.app.partner.User::class.java) ?: return@launch
                val local = database.userDao().getByUid(uid)

                val remoteUpdatedAt = (userMap["updatedAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
                if (local == null || remoteUpdatedAt > local.updatedAt) {
                    database.userDao().insertOrUpdate(remoteUser.toEntity(remoteUpdatedAt, "firebase", 1))
                }
            }
        }

        // 2. Listen to businesses belonging to the user
        val businessesQuery = firebaseDb.child("businesses").orderByChild("ownerId").equalTo(uid)
        val businessListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                scope.launch {
                    val remoteEntities = mutableListOf<BusinessEntity>()
                    for (child in snapshot.children) {
                        val bizMap = child.value as? Map<*, *> ?: continue
                        val id = child.key ?: continue
                        val name = bizMap["name"] as? String ?: ""
                        val ownerId = bizMap["ownerId"] as? String ?: ""
                        val createdAt = (bizMap["createdAt"] as? Number)?.toLong() ?: 0L
                        val isPartner = bizMap["isPartner"] as? Boolean ?: false
                        val remoteUpdatedAt = (bizMap["updatedAt"] as? Number)?.toLong() ?: createdAt

                        val local = database.businessDao().getById(id)
                        if (local == null || remoteUpdatedAt > local.updatedAt) {
                            val entity = BusinessEntity(id, name, ownerId, createdAt, isPartner, remoteUpdatedAt, "firebase", 1)
                            remoteEntities.add(entity)
                            // Start listening to notebooks for this business
                            syncNotebooksForBusiness(id)
                        } else if (local.updatedAt > remoteUpdatedAt) {
                            // Local has newer data, enqueue upload
                            enqueueSync("business", id, "UPDATE", gson.toJson(local.toDomain()))
                        }
                    }
                    if (remoteEntities.isNotEmpty()) {
                        database.businessDao().insertOrUpdate(remoteEntities)
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        }
        businessesQuery.addValueEventListener(businessListener)
        firebaseListeners["businesses"] = businessListener
    }

    private fun syncNotebooksForBusiness(businessId: String) {
        val notebooksQuery = firebaseDb.child("notebooks").orderByChild("businessId").equalTo(businessId)
        val notebookListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                scope.launch {
                    val remoteEntities = mutableListOf<NotebookEntity>()
                    for (child in snapshot.children) {
                        val nbMap = child.value as? Map<*, *> ?: continue
                        val id = child.key ?: continue
                        val name = nbMap["name"] as? String ?: ""
                        val createdBy = nbMap["createdBy"] as? String ?: ""
                        val createdAt = (nbMap["createdAt"] as? Number)?.toLong() ?: 0L
                        val remoteUpdatedAt = (nbMap["updatedAt"] as? Number)?.toLong() ?: createdAt

                        val local = database.notebookDao().getById(id)
                        if (local == null || remoteUpdatedAt > local.updatedAt) {
                            val entity = NotebookEntity(id, name, businessId, createdBy, createdAt, remoteUpdatedAt, "firebase", 1)
                            remoteEntities.add(entity)
                            
                            // Start listening to child nodes for this notebook
                            syncNotebookContents(id)
                        } else if (local.updatedAt > remoteUpdatedAt) {
                            enqueueSync("notebook", id, "UPDATE", gson.toJson(local.toDomain()))
                        }
                    }
                    if (remoteEntities.isNotEmpty()) {
                        database.notebookDao().insertOrUpdate(remoteEntities)
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        }
        notebooksQuery.addValueEventListener(notebookListener)
        firebaseListeners["notebooks_$businessId"] = notebookListener
    }

    private fun syncNotebookContents(notebookId: String) {
        // A. Listen to transactions
        listenToNotebookChild(notebookId, "transactions") { child ->
            val transMap = child.value as? Map<*, *> ?: return@listenToNotebookChild
            val id = child.key ?: return@listenToNotebookChild
            val amount = (transMap["amount"] as? Number)?.toDouble() ?: 0.0
            val type = transMap["type"] as? String ?: ""
            val remark = transMap["remark"] as? String ?: ""
            val createdBy = transMap["createdBy"] as? String ?: ""
            val date = transMap["date"] as? String ?: ""
            val time = transMap["time"] as? String ?: ""
            val createdAt = (transMap["createdAt"] as? Number)?.toLong() ?: 0L
            val categoryId = transMap["categoryId"] as? String ?: ""
            val categoryName = transMap["categoryName"] as? String ?: ""
            val partyId = transMap["partyId"] as? String ?: ""
            val partyName = transMap["partyName"] as? String ?: ""
            val receiptUrl = transMap["receiptUrl"] as? String ?: ""
            val receiptName = transMap["receiptName"] as? String ?: ""
            val remoteUpdatedAt = (transMap["updatedAt"] as? Number)?.toLong() ?: createdAt

            scope.launch {
                val local = database.transactionDao().getById(id)
                if (local == null || remoteUpdatedAt > local.updatedAt) {
                    val entity = TransactionEntity(
                        id, notebookId, amount, type, remark, createdBy, date, time, createdAt,
                        categoryId, categoryName, partyId, partyName, receiptUrl, receiptName,
                        remoteUpdatedAt, "firebase", 1
                    )
                    database.transactionDao().insertOrUpdate(entity)
                } else if (local.updatedAt > remoteUpdatedAt) {
                    enqueueSync("transaction", id, "UPDATE", gson.toJson(local.toDomain().apply { this.id = id }))
                }
            }
        }

        // B. Listen to categories
        listenToNotebookChild(notebookId, "categories") { child ->
            val catMap = child.value as? Map<*, *> ?: return@listenToNotebookChild
            val id = child.key ?: return@listenToNotebookChild
            val name = catMap["name"] as? String ?: ""
            val description = catMap["description"] as? String ?: ""
            val utilization = (catMap["utilization"] as? Number)?.toInt() ?: 0
            val type = catMap["type"] as? String ?: "expense"
            val colorHex = catMap["colorHex"] as? String
            val iconResName = catMap["iconResName"] as? String ?: "ic_cat_money"
            val createdAt = (catMap["createdAt"] as? Number)?.toLong() ?: 0L
            val remoteUpdatedAt = (catMap["updatedAt"] as? Number)?.toLong() ?: createdAt

            scope.launch {
                val local = database.categoryDao().getById(id)
                if (local == null || remoteUpdatedAt > local.updatedAt) {
                    val entity = CategoryEntity(
                        id, notebookId, name, description, utilization, type, colorHex, iconResName,
                        createdAt, remoteUpdatedAt, "firebase", 1
                    )
                    database.categoryDao().insertOrUpdate(entity)
                } else if (local.updatedAt > remoteUpdatedAt) {
                    enqueueSync("category", id, "UPDATE", gson.toJson(local.toDomain().apply { this.id = id }))
                }
            }
        }

        // C. Listen to parties
        listenToNotebookChild(notebookId, "parties") { child ->
            val partyMap = child.value as? Map<*, *> ?: return@listenToNotebookChild
            val id = child.key ?: return@listenToNotebookChild
            val name = partyMap["name"] as? String ?: ""
            val role = partyMap["role"] as? String ?: "VENDOR"
            val contact = partyMap["contact"] as? String ?: ""
            val email = partyMap["email"] as? String ?: ""
            val website = partyMap["website"] as? String ?: ""
            val priorityTag = partyMap["priorityTag"] as? String ?: ""
            val colorHex = partyMap["colorHex"] as? String
            val iconResName = partyMap["iconResName"] as? String ?: "ic_party_person"
            val createdAt = (partyMap["createdAt"] as? Number)?.toLong() ?: 0L
            val remoteUpdatedAt = (partyMap["updatedAt"] as? Number)?.toLong() ?: createdAt

            scope.launch {
                val local = database.partyDao().getById(id)
                if (local == null || remoteUpdatedAt > local.updatedAt) {
                    val entity = PartyEntity(
                        id, notebookId, name, role, contact, email, website, priorityTag, colorHex, iconResName,
                        createdAt, remoteUpdatedAt, "firebase", 1
                    )
                    database.partyDao().insertOrUpdate(entity)
                } else if (local.updatedAt > remoteUpdatedAt) {
                    enqueueSync("party", id, "UPDATE", gson.toJson(local.toDomain().apply { this.id = id }))
                }
            }
        }
    }

    private fun listenToPath(path: String, onUpdate: (DataSnapshot) -> Unit) {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    onUpdate(snapshot)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        firebaseDb.child(path).addValueEventListener(listener)
        firebaseListeners[path] = listener
    }

    private fun listenToNotebookChild(notebookId: String, nodeName: String, onChildUpdate: (DataSnapshot) -> Unit) {
        val path = "$nodeName/$notebookId"
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (child in snapshot.children) {
                    onChildUpdate(child)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        firebaseDb.child(path).addValueEventListener(listener)
        firebaseListeners["$path/$notebookId"] = listener
    }

    /**
     * Enqueues a sync operation locally to Room.
     */
    suspend fun enqueueSync(
        entityType: String,
        entityId: String,
        operation: String,
        payload: String
    ) {
        val item = SyncQueueEntity(
            entityId = entityId,
            entityType = entityType,
            operation = operation,
            payload = payload,
            status = "PENDING"
        )
        database.syncQueueDao().insert(item)
        if (networkMonitor.isOnline.value) {
            syncPendingQueue()
        }
    }

    fun stopRealtimeSync() {
        firebaseListeners.forEach { (path, listener) ->
            if (path == "businesses" || path == "notebooks") {
                // query based listeners
                // firebaseDb.child(path).removeEventListener(listener)
            } else {
                firebaseDb.child(path).removeEventListener(listener)
            }
        }
        firebaseListeners.clear()
    }

    // Helper extension for awaiting firebase tasks
    private suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T {
        return suspendCancellableCoroutine { cont ->
            addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    cont.resume(task.result, {})
                } else {
                    cont.resumeWith(Result.failure(task.exception ?: Exception("Firebase Task Failed")))
                }
            }
        }
    }
}
