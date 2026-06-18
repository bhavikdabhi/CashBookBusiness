package com.cashbk.app.members._bean

data class Member(
    var id: String = "",
    val name: String = "",
    val phone: String = "",
    val role: String = "", // "admin", "partner", "writer", "reader"
    val addedAt: Long = 0
)