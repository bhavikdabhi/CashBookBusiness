package com.cashbk.app.category._bean

data class Category(
    var id: String = "",
    var name: String = "",
    var description: String = "",
    var utilization: Int = 0, // Percentage 0-100
    var type: String = "expense", // income or expense
    var colorHex: String? = null, // Dynamically loaded default in UI
    var iconResName: String = "ic_cat_money" // Default icon
)