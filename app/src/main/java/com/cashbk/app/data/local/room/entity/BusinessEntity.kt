package com.cashbk.app.data.local.room.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.cashbk.app.business._bean.Business

@Entity(tableName = "businesses")
data class BusinessEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val ownerId: String,
    val createdAt: Long,
    val isPartner: Boolean,
    val updatedAt: Long,
    val deviceId: String,
    val version: Int
) {
    fun toDomain(): Business = Business(
        id = id,
        name = name,
        ownerId = ownerId,
        createdAt = createdAt,
        isPartner = isPartner
    )
}

fun Business.toEntity(updatedAt: Long, deviceId: String, version: Int): BusinessEntity = BusinessEntity(
    id = id,
    name = name,
    ownerId = ownerId,
    createdAt = createdAt,
    isPartner = isPartner,
    updatedAt = updatedAt,
    deviceId = deviceId,
    version = version
)
