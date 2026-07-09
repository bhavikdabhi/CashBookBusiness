package com.cashbk.app.data.local.room.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.cashbk.app.transaction._bean.Transaction

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey
    val id: String,
    val notebookId: String,
    val amount: Double,
    val type: String,
    val remark: String,
    val createdBy: String,
    val date: String,
    val time: String,
    val createdAt: Long,
    val categoryId: String,
    val categoryName: String,
    val partyId: String,
    val partyName: String,
    val receiptUrl: String,
    val receiptName: String,
    val updatedAt: Long,
    val deviceId: String,
    val version: Int
) {
    fun toDomain(): Transaction = Transaction(
        id = id,
        amount = amount,
        type = type,
        remark = remark,
        createdBy = createdBy,
        date = date,
        time = time,
        createdAt = createdAt,
        categoryId = categoryId,
        categoryName = categoryName,
        partyId = partyId,
        partyName = partyName,
        receiptUrl = receiptUrl,
        receiptName = receiptName
    )
}

fun Transaction.toEntity(notebookId: String, updatedAt: Long, deviceId: String, version: Int): TransactionEntity = TransactionEntity(
    id = id,
    notebookId = notebookId,
    amount = amount,
    type = type,
    remark = remark,
    createdBy = createdBy,
    date = date,
    time = time,
    createdAt = createdAt,
    categoryId = categoryId,
    categoryName = categoryName,
    partyId = partyId,
    partyName = partyName,
    receiptUrl = receiptUrl,
    receiptName = receiptName,
    updatedAt = updatedAt,
    deviceId = deviceId,
    version = version
)
