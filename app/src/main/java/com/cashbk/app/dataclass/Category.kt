package com.cashbk.app.dataclass

data class Category(
    var id: String = "",
    val name: String = "",
    val description: String = "",
    val utilization: Int = 0 // Percentage 0-100
)
