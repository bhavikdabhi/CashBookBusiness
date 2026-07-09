package com.cashbk.app.data.local.room.dao

import androidx.room.*
import com.cashbk.app.data.local.room.entity.NotebookEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NotebookDao {
    @Query("SELECT * FROM notebooks WHERE id = :id")
    suspend fun getById(id: String): NotebookEntity?

    @Query("SELECT * FROM notebooks WHERE businessId = :businessId ORDER BY createdAt DESC")
    fun getByBusinessIdFlow(businessId: String): Flow<List<NotebookEntity>>

    @Query("SELECT * FROM notebooks ORDER BY createdAt DESC")
    fun getAllFlow(): Flow<List<NotebookEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(notebook: NotebookEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(notebooks: List<NotebookEntity>)

    @Delete
    suspend fun delete(notebook: NotebookEntity)

    @Query("DELETE FROM notebooks WHERE id = :id")
    suspend fun deleteById(id: String)
}
