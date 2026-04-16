package com.cashbk.app.dataclass

data class Category(
    var id: String = "",
    var name: String = "",
    var description: String = "",
    var utilization: Int = 0, // Percentage 0-100
    var type: String = "expense", // income or expense
    var colorHex: String = "#90A4AE", // Default slate color
    var iconResName: String = "ic_cat_money" // Default icon
)
