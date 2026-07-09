package com.cashbk.app.data.local.room.dao

import androidx.room.*
import com.cashbk.app.data.local.room.entity.PartyEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PartyDao {
    @Query("SELECT * FROM parties WHERE id = :id")
    suspend fun getById(id: String): PartyEntity?

    @Query("SELECT * FROM parties WHERE notebookId = :notebookId ORDER BY name ASC")
    fun getByNotebookIdFlow(notebookId: String): Flow<List<PartyEntity>>

    @Query("SELECT * FROM parties WHERE notebookId = :notebookId")
    suspend fun getByNotebookId(notebookId: String): List<PartyEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(party: PartyEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(parties: List<PartyEntity>)

    @Delete
    suspend fun delete(party: PartyEntity)

    @Query("DELETE FROM parties WHERE id = :id")
    suspend fun deleteById(id: String)
}
