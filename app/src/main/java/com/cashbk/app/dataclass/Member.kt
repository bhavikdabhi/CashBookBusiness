package com.cashbk.app.dataclass

data class Member(
    var id: String = "",
    val name: String = "",
    val phone: String = "",
    val role: String = "", // "admin", "partner", "writer", "reader"
    val addedAt: Long = 0
)
