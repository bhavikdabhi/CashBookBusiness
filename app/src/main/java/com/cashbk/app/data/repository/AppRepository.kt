package com.cashbk.app.data.repository

import com.cashbk.app.business._bean.Business
import com.cashbk.app.category._bean.Category
import com.cashbk.app.data.local.room.AppDatabase
import com.cashbk.app.data.local.room.entity.*
import com.cashbk.app.data.sync.manager.SyncManager
import com.cashbk.app.notebook._bean.Notebook
import com.cashbk.app.partner.User
import com.cashbk.app.party._bean.Party
import com.cashbk.app.transaction._bean.Transaction
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AppRepository(
    private val database: AppDatabase,
    private val syncManager: SyncManager
) {
    private val gson = Gson()
    private val deviceId = "local_device"

    // ==========================================
    // BUSINESS OPERATIONS
    // ==========================================
    fun getBusinessesFlow(): Flow<List<Business>> {
        return database.businessDao().getAllFlow().map { list ->
            list.map { it.toDomain() }
        }
    }

    suspend fun getBusinessById(id: String): Business? {
        return database.businessDao().getById(id)?.toDomain()
    }

    suspend fun saveBusiness(business: Business) {
        val now = System.currentTimeMillis()
        val entity = business.toEntity(updatedAt = now, deviceId = deviceId, version = 1)
        database.businessDao().insertOrUpdate(entity)
        syncManager.enqueueSync("business", business.id, "UPDATE", gson.toJson(business))
    }

    suspend fun deleteBusiness(business: Business) {
        database.businessDao().deleteById(business.id)
        syncManager.enqueueSync("business", business.id, "DELETE", gson.toJson(business))
    }

    // ==========================================
    // NOTEBOOK OPERATIONS
    // ==========================================
    fun getNotebooksFlow(businessId: String): Flow<List<Notebook>> {
        return database.notebookDao().getByBusinessIdFlow(businessId).map { list ->
            list.map { it.toDomain() }
        }
    }

    suspend fun getNotebookById(id: String): Notebook? {
        return database.notebookDao().getById(id)?.toDomain()
    }

    suspend fun saveNotebook(notebook: Notebook) {
        val now = System.currentTimeMillis()
        val entity = notebook.toEntity(updatedAt = now, deviceId = deviceId, version = 1)
        database.notebookDao().insertOrUpdate(entity)
        syncManager.enqueueSync("notebook", notebook.id, "UPDATE", gson.toJson(notebook))
    }

    suspend fun deleteNotebook(notebook: Notebook) {
        database.notebookDao().deleteById(notebook.id)
        syncManager.enqueueSync("notebook", notebook.id, "DELETE", gson.toJson(notebook))
    }

    // ==========================================
    // TRANSACTION OPERATIONS
    // ==========================================
    fun getTransactionsFlow(notebookId: String): Flow<List<Transaction>> {
        return database.transactionDao().getByNotebookIdFlow(notebookId).map { list ->
            list.map { it.toDomain() }
        }
    }

    suspend fun saveTransaction(notebookId: String, transaction: Transaction) {
        val now = System.currentTimeMillis()
        val entity = transaction.toEntity(notebookId, updatedAt = now, deviceId = deviceId, version = 1)
        database.transactionDao().insertOrUpdate(entity)
        syncManager.enqueueSync("transaction", transaction.id, "UPDATE", gson.toJson(entity))
    }

    suspend fun deleteTransaction(notebookId: String, transaction: Transaction) {
        database.transactionDao().deleteById(transaction.id)
        val entity = transaction.toEntity(notebookId, updatedAt = System.currentTimeMillis(), deviceId = deviceId, version = 1)
        syncManager.enqueueSync("transaction", transaction.id, "DELETE", gson.toJson(entity))
    }

    // ==========================================
    // CATEGORY OPERATIONS
    // ==========================================
    fun getCategoriesFlow(notebookId: String): Flow<List<Category>> {
        return database.categoryDao().getByNotebookIdFlow(notebookId).map { list ->
            list.map { it.toDomain() }
        }
    }

    suspend fun saveCategory(notebookId: String, category: Category) {
        val now = System.currentTimeMillis()
        val entity = category.toEntity(notebookId, createdAt = category.utilization.toLong(), updatedAt = now, deviceId = deviceId, version = 1)
        database.categoryDao().insertOrUpdate(entity)
        syncManager.enqueueSync("category", category.id, "UPDATE", gson.toJson(entity))
    }

    suspend fun deleteCategory(notebookId: String, category: Category) {
        database.categoryDao().deleteById(category.id)
        val entity = category.toEntity(notebookId, createdAt = 0L, updatedAt = System.currentTimeMillis(), deviceId = deviceId, version = 1)
        syncManager.enqueueSync("category", category.id, "DELETE", gson.toJson(entity))
    }

    // ==========================================
    // PARTY OPERATIONS
    // ==========================================
    fun getPartiesFlow(notebookId: String): Flow<List<Party>> {
        return database.partyDao().getByNotebookIdFlow(notebookId).map { list ->
            list.map { it.toDomain() }
        }
    }

    suspend fun saveParty(notebookId: String, party: Party) {
        val now = System.currentTimeMillis()
        val entity = party.toEntity(notebookId, createdAt = now, updatedAt = now, deviceId = deviceId, version = 1)
        database.partyDao().insertOrUpdate(entity)
        syncManager.enqueueSync("party", party.id, "UPDATE", gson.toJson(entity))
    }

    suspend fun deleteParty(notebookId: String, party: Party) {
        database.partyDao().deleteById(party.id)
        val entity = party.toEntity(notebookId, createdAt = 0L, updatedAt = System.currentTimeMillis(), deviceId = deviceId, version = 1)
        syncManager.enqueueSync("party", party.id, "DELETE", gson.toJson(entity))
    }

    // ==========================================
    // USER OPERATIONS
    // ==========================================
    fun getUserFlow(uid: String): Flow<User?> {
        return database.userDao().getByUidFlow(uid).map { it?.toDomain() }
    }

    suspend fun saveUser(user: User) {
        val now = System.currentTimeMillis()
        val entity = user.toEntity(updatedAt = now, deviceId = deviceId, version = 1)
        database.userDao().insertOrUpdate(entity)
        syncManager.enqueueSync("user", user.uid, "UPDATE", gson.toJson(user))
    }

    // ==========================================
    // SETTINGS OPERATIONS
    // ==========================================
    suspend fun getSetting(key: String): String? {
        return database.settingsDao().getByKey(key)?.value
    }

    suspend fun saveSetting(key: String, value: String) {
        database.settingsDao().insertOrUpdate(SettingsEntity(key, value))
    }
}
