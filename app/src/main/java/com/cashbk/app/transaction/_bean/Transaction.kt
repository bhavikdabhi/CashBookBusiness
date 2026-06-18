package com.cashbk.app.transaction._bean

import com.google.firebase.database.Exclude

data class Transaction(
    var id: String = "",
    var amount: Double = 0.0,
    var type: String = "",
    var remark: String = "",
    var createdBy: String = "",
    var date: String = "",
    var time: String = "",
    var createdAt: Long = System.currentTimeMillis(),

    var categoryId: String = "",
    var categoryName: String = "",

    var partyId: String = "",
    var partyName: String = "",
    var receiptUrl: String = "",
    var receiptName: String = ""
) {
    @get:Exclude
    var createdByName: String = ""
    @get:Exclude
    var runningBalance: Double = 0.0
}