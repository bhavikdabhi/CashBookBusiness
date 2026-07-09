package com.cashbk.app.data.local.room.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.cashbk.app.party._bean.Party

@Entity(tableName = "parties")
data class PartyEntity(
    @PrimaryKey
    val id: String,
    val notebookId: String,
    val name: String,
    val role: String,
    val contact: String,
    val email: String,
    val website: String,
    val priorityTag: String,
    val colorHex: String?,
    val iconResName: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deviceId: String,
    val version: Int
) {
    fun toDomain(): Party = Party(
        id = id,
        name = name,
        role = role,
        contact = contact,
        email = email,
        website = website,
        priorityTag = priorityTag,
        colorHex = colorHex,
        iconResName = iconResName
    )
}

fun Party.toEntity(notebookId: String, createdAt: Long, updatedAt: Long, deviceId: String, version: Int): PartyEntity = PartyEntity(
    id = id,
    notebookId = notebookId,
    name = name,
    role = role,
    contact = contact,
    email = email,
    website = website,
    priorityTag = priorityTag,
    colorHex = colorHex,
    iconResName = iconResName,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deviceId = deviceId,
    version = version
)
