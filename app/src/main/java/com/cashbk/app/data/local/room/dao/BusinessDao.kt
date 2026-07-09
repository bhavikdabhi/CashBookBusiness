package com.cashbk.app.data.local.room.dao

import androidx.room.*
import com.cashbk.app.data.local.room.entity.BusinessEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BusinessDao {
    @Query("SELECT * FROM businesses WHERE id = :id")
    suspend fun getById(id: String): BusinessEntity?

    @Query("SELECT * FROM businesses ORDER BY createdAt DESC")
    fun getAllFlow(): Flow<List<BusinessEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(business: BusinessEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(businesses: List<BusinessEntity>)

    @Delete
    suspend fun delete(business: BusinessEntity)

    @Query("DELETE FROM businesses WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM businesses")
    suspend fun deleteAll()
}
