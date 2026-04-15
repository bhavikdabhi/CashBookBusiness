package com.cashbk.app.dataclass

data class Party(
    var id: String = "",
    val name: String = "",
    val role: String = "VENDOR",
    val contact: String = "",
    val email: String = "",
    val website: String = "",
    val priorityTag: String = "" // e.g., INTERNAL, TECH, PRIMARY
)
