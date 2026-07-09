package com.cashbk.app.data.local.room.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.cashbk.app.category._bean.Category

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey
    val id: String,
    val notebookId: String,
    val name: String,
    val description: String,
    val utilization: Int,
    val type: String,
    val colorHex: String?,
    val iconResName: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deviceId: String,
    val version: Int
) {
    fun toDomain(): Category = Category(
        id = id,
        name = name,
        description = description,
        utilization = utilization,
        type = type,
        colorHex = colorHex,
        iconResName = iconResName
    )
}

fun Category.toEntity(notebookId: String, createdAt: Long, updatedAt: Long, deviceId: String, version: Int): CategoryEntity = CategoryEntity(
    id = id,
    notebookId = notebookId,
    name = name,
    description = description,
    utilization = utilization,
    type = type,
    colorHex = colorHex,
    iconResName = iconResName,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deviceId = deviceId,
    version = version
)
