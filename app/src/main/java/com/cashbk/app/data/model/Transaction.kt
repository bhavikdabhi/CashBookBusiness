package com.cashbk.app.data.model

import com.google.firebase.database.Exclude

data class Transaction(
    var id: String = "",
    val type: String = "",
    val amount: Any = 0.0,
    val remark: String = "",
    val date: String = "",
    val time: String = "",
    val createdBy: String = "",
    val createdAt: Long = 0,
    val categoryId: String = "",
    val partyId: String = ""
) {
    @Exclude
    fun getAmountAsDouble(): Double {
        return when (amount) {
            is Long -> amount.toDouble()
            is Double -> amount
            is String -> amount.toDoubleOrNull() ?: 0.0
            else -> 0.0
        }
    }
}
