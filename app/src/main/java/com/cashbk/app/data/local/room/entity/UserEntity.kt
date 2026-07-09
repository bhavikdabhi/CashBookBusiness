package com.cashbk.app.data.local.room.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.cashbk.app.partner.User

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey
    val uid: String,
    val phone: String,
    val name: String?,
    val email: String?,
    val profileImageUrl: String?,
    val membershipTier: String?,
    val createdAt: Long?,
    val googleDriveEmail: String?,
    val updatedAt: Long,
    val deviceId: String,
    val version: Int
) {
    fun toDomain(): User = User(
        uid = uid,
        phone = phone,
        name = name,
        email = email,
        profileImageUrl = profileImageUrl,
        membershipTier = membershipTier,
        createdAt = createdAt,
        googleDriveEmail = googleDriveEmail
    )
}

fun User.toEntity(updatedAt: Long, deviceId: String, version: Int): UserEntity {
    val timeVal = when (val time = createdAt) {
        is Long -> time
        is Number -> time.toLong()
        is String -> time.toLongOrNull()
        else -> null
    }
    return UserEntity(
        uid = uid,
        phone = phone,
        name = name,
        email = email,
        profileImageUrl = profileImageUrl,
        membershipTier = membershipTier,
        createdAt = timeVal,
        googleDriveEmail = googleDriveEmail,
        updatedAt = updatedAt,
        deviceId = deviceId,
        version = version
    )
}
