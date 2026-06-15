package com.cashbk.app.dataclass

data class User(
    val uid: String = "",
    val phone: String = "",
    val name: String? = null,
    val email: String? = null,
    val profileImageUrl: String? = null,
    val membershipTier: String? = "GOLD MEMBER",
    val createdAt: Any? = null,
    val googleDriveEmail: String? = null
)
