package com.cashbk.app.data.di

import android.content.Context
import com.cashbk.app.data.local.room.AppDatabase
import com.cashbk.app.data.repository.AppRepository
import com.cashbk.app.data.sync.manager.SyncManager
import com.cashbk.app.data.sync.network.NetworkMonitor

object AppContainer {
    @Volatile
    private var database: AppDatabase? = null

    @Volatile
    private var networkMonitor: NetworkMonitor? = null

    @Volatile
    private var syncManager: SyncManager? = null

    @Volatile
    private var repository: AppRepository? = null

    fun getDatabase(context: Context): AppDatabase {
        return database ?: synchronized(this) {
            val instance = AppDatabase.getDatabase(context)
            database = instance
            instance
        }
    }

    fun getNetworkMonitor(context: Context): NetworkMonitor {
        return networkMonitor ?: synchronized(this) {
            val instance = NetworkMonitor(context.applicationContext)
            networkMonitor = instance
            instance
        }
    }

    fun getSyncManager(context: Context): SyncManager {
        return syncManager ?: synchronized(this) {
            val instance = SyncManager(
                context.applicationContext,
                getDatabase(context),
                getNetworkMonitor(context)
            )
            syncManager = instance
            instance
        }
    }

    fun getRepository(context: Context): AppRepository {
        return repository ?: synchronized(this) {
            val instance = AppRepository(
                getDatabase(context),
                getSyncManager(context)
            )
            repository = instance
            instance
        }
    }
}
