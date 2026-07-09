package com.cashbk.app.data.local.room.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.cashbk.app.notebook._bean.Notebook

@Entity(tableName = "notebooks")
data class NotebookEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val businessId: String,
    val createdBy: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deviceId: String,
    val version: Int
) {
    fun toDomain(): Notebook = Notebook(
        id = id,
        name = name,
        businessId = businessId,
        createdBy = createdBy,
        createdAt = createdAt
    )
}

fun Notebook.toEntity(updatedAt: Long, deviceId: String, version: Int): NotebookEntity = NotebookEntity(
    id = id,
    name = name,
    businessId = businessId,
    createdBy = createdBy,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deviceId = deviceId,
    version = version
)
