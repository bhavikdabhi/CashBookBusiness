package com.cashbk.app.data.model

data class Member(
    var id: String = "",
    val name: String = "",
    val phone: String = "",
    val role: String = "", // "admin", "partner", "writer", "reader"
    val addedAt: Long = 0
)
