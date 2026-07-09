package com.cashbk.app.data.local.room.dao

import androidx.room.*
import com.cashbk.app.data.local.room.entity.SettingsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingsDao {
    @Query("SELECT * FROM settings WHERE `key` = :key")
    suspend fun getByKey(key: String): SettingsEntity?

    @Query("SELECT * FROM settings WHERE `key` = :key")
    fun getByKeyFlow(key: String): Flow<SettingsEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(setting: SettingsEntity)

    @Query("DELETE FROM settings WHERE `key` = :key")
    suspend fun deleteByKey(key: String)
}
