package com.cashbk.app.utils

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.cashbk.app.dataclass.Transaction
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.util.Locale

object PdfGenerator {

    fun generatePdf(
        context: Context,
        notebookName: String,
        transactions: List<Transaction>,
        netBalance: Double
    ) {
        val document = PdfDocument()

        val pageWidth = 595
        val pageHeight = 842 // A4 Size

        val titlePaint = Paint().apply {
            color = Color.BLACK
            textSize = 24f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }

        val headerPaint = Paint().apply {
            color = Color.BLACK
            textSize = 14f
            isFakeBoldText = true
        }

        val textPaint = Paint().apply {
            color = Color.DKGRAY
            textSize = 12f
        }

        var pageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        var page = document.startPage(pageInfo)
        var canvas: Canvas = page.canvas

        var yPosition = 50f

        // Draw Title
        canvas.drawText("Statement: $notebookName", pageWidth / 2f, yPosition, titlePaint)
        yPosition += 40f

        // Draw Summary
        val summaryText = "Net Balance: ${formatCurrency(netBalance)} | Total Entries: ${transactions.size}"
        canvas.drawText(summaryText, 40f, yPosition, headerPaint)
        yPosition += 40f

        // Draw Table Header
        val colDate = 40f
        val colDetails = 140f
        val colType = 320f
        val colAmount = 400f
        val colBal = 490f
        
        canvas.drawLine(40f, yPosition - 15f, pageWidth - 40f, yPosition - 15f, headerPaint)
        canvas.drawText("Date", colDate, yPosition, headerPaint)
        canvas.drawText("Details", colDetails, yPosition, headerPaint)
        canvas.drawText("Type", colType, yPosition, headerPaint)
        canvas.drawText("Amount", colAmount, yPosition, headerPaint)
        canvas.drawText("Balance", colBal, yPosition, headerPaint)
        yPosition += 10f
        canvas.drawLine(40f, yPosition, pageWidth - 40f, yPosition, headerPaint)
        yPosition += 30f

        // Draw Transactions
        for (transaction in transactions) {
            if (yPosition > pageHeight - 60f) {
                // Next Page
                document.finishPage(page)
                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                page = document.startPage(pageInfo)
                canvas = page.canvas
                yPosition = 50f
            }

            canvas.drawText(transaction.date, colDate, yPosition, textPaint)
            
            val details = if (transaction.partyName.isNotEmpty()) transaction.partyName 
                          else if (transaction.categoryName.isNotEmpty()) transaction.categoryName 
                          else "Cash"
            
            val remark = if (transaction.remark.isNotEmpty()) " - ${transaction.remark}" else ""
            var detailsText = details + remark
            if (detailsText.length > 25) {
                detailsText = detailsText.substring(0, 22) + "..."
            }
            
            canvas.drawText(detailsText, colDetails, yPosition, textPaint)
            
            val typeText = if (transaction.type == "in") "IN (+)" else "OUT (-)"
            canvas.drawText(typeText, colType, yPosition, textPaint)
            
            canvas.drawText(formatCurrency(transaction.amount), colAmount, yPosition, textPaint)
            canvas.drawText(formatCurrency(transaction.runningBalance), colBal, yPosition, textPaint)
            
            yPosition += 25f
        }

        document.finishPage(page)

        // Save PDF
        val reportsDir = File(context.cacheDir, "reports")
        if (!reportsDir.exists()) reportsDir.mkdirs()
        
        val fileName = "Statement_${notebookName.replace(" ", "_").take(15)}_${System.currentTimeMillis()}.pdf"
        val pdfFile = File(reportsDir, fileName)

        try {
            document.writeTo(FileOutputStream(pdfFile))
            document.close()
            openPdf(context, pdfFile)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Error generating PDF: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun openPdf(context: Context, file: File) {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            
            val chooser = Intent.createChooser(intent, "Open PDF Report")
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "No PDF viewer installed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun formatCurrency(amount: Double): String {
        return NumberFormat.getNumberInstance(Locale("en", "IN")).format(amount)
    }
}
