package com.cashbk.app.adapter

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.cashbk.app.R
import com.cashbk.app.dataclass.Transaction
import com.cashbk.app.databinding.ItemTransactionBinding
import java.text.NumberFormat
import java.util.Locale

class TransactionAdapter(
    private val transactions: List<Transaction>,
    private val onClick: (Transaction) -> Unit,
    private val onLongClick: (View, Transaction) -> Unit
) : RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder>() {

    inner class TransactionViewHolder(val binding: ItemTransactionBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val binding = ItemTransactionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TransactionViewHolder(binding)
    }

    private fun formatCurrency(amount: Double): String {
        return NumberFormat.getNumberInstance(Locale("en", "IN")).format(amount)
    }

    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        val transaction = transactions[position]
        val context = holder.itemView.context

        // DATE HEADER logic
        val showHeader = position == 0 || transaction.date != transactions[position - 1].date
        if (showHeader) {
            holder.binding.tvDateHeader.visibility = View.VISIBLE
            holder.binding.tvDateHeader.text = transaction.date
        } else {
            holder.binding.tvDateHeader.visibility = View.GONE
        }

        // REMARK
        holder.binding.tvRemark.text = if (transaction.remark.isNotEmpty()) transaction.remark else "Transaction"

        // PARTY NAME logic
        if (transaction.partyName.isNotEmpty()) {
            holder.binding.tvParty.text = transaction.partyName
            holder.binding.tvParty.visibility = View.VISIBLE
        } else {
            holder.binding.tvParty.visibility = View.GONE
        }

        // CATEGORY CHIP logic
        if (transaction.categoryName.isNotEmpty()) {
            holder.binding.tvCategoryChip.text = transaction.categoryName
            holder.binding.tvCategoryChip.visibility = View.VISIBLE
        } else {
            holder.binding.tvCategoryChip.visibility = View.GONE
        }

        // AMOUNT COLORING
        val amount = transaction.amount
        holder.binding.tvAmount.text = formatCurrency(amount)
        holder.binding.tvAmount.setTextColor(
            ContextCompat.getColor(context, if (transaction.type == "in") R.color.success else R.color.danger)
        )

        // RUNNING BALANCE
        holder.binding.tvBalanceAfter.text = "Balance: ${formatCurrency(transaction.runningBalance)}"

        // ENTRY INFO
        val entryBy = if (transaction.createdByName.isNotEmpty()) transaction.createdByName else "You"
        holder.binding.tvEntryInfo.text = "Entry by $entryBy at ${transaction.time}"

        // RECEIPT ATTACHMENT
        val url = transaction.receiptUrl
        if (url.isNotEmpty()) {
            holder.binding.receiptRow.visibility = View.VISIBLE

            // ── Filename: Use stored name if available, otherwise decode from URL ──
            val rawSegment = if (transaction.receiptName.isNotEmpty()) {
                transaction.receiptName
            } else {
                try {
                Uri.decode(Uri.parse(url).lastPathSegment ?: "")
                    .substringAfterLast("/")  // remove any remaining folder prefix
                    .substringBefore("?")
                    .uppercase(Locale.getDefault())
                    .let { if (it.length > 28) it.take(26) + "\u2026" else it }
                    .ifEmpty { "RECEIPT" }
                } catch (e: Exception) { "RECEIPT" }
            }

            holder.binding.tvReceiptName.text = rawSegment.uppercase(Locale.getDefault())

            // ── Image detection & URL Transformation for Google Drive ──
            val lowerDecoded = rawSegment.lowercase(Locale.getDefault())
            val lowerUrl     = url.lowercase(Locale.getDefault())
            
            val isDriveUrl = lowerUrl.contains("drive.google.com")
            var displayUrl = url

            if (isDriveUrl) {
                // Extract file ID from drive link and use thumbnail URL for Glide
                val fileId = extractDriveFileId(url)
                if (fileId != null) {
                    displayUrl = "https://drive.google.com/thumbnail?id=$fileId&sz=w300"
                }
            }

            val isImage = isDriveUrl || listOf(".jpg", ".jpeg", ".png", ".webp", ".gif", ".bmp")
                .any { lowerDecoded.endsWith(it) || lowerUrl.contains(it) }

            val thumb = holder.binding.ivReceiptThumb

            // Always clear any ImageView tint/src set previously (important for RecyclerView recycling)
            thumb.clearColorFilter()
            thumb.imageTintList = null

            if (isImage) {
                // ── Load real image thumbnail ──
                Glide.with(context)
                    .load(displayUrl)
                    .transition(DrawableTransitionOptions.withCrossFade(200))
                    .placeholder(R.drawable.bg_glass_field)   // subtle bg while loading
                    .error(R.drawable.ic_book) // fallback to icon if image fails to load
                    .centerCrop()
                    .into(thumb)
            } else {
                // ── PDF or unknown — show document icon ──
                Glide.with(context).clear(thumb)
                thumb.setImageResource(R.drawable.ic_book)
                thumb.setColorFilter(
                    ContextCompat.getColor(context, R.color.stitch_primary)
                )
            }

            holder.binding.receiptRow.setOnClickListener {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                it.context.startActivity(Intent.createChooser(intent, "Open receipt with\u2026"))
            }
        } else {
            holder.binding.receiptRow.visibility = View.GONE
            // Clear Glide binding when hidden to avoid showing stale image after recycle
            Glide.with(context).clear(holder.binding.ivReceiptThumb)
        }

        // CLICKS
        holder.itemView.setOnClickListener { onClick(transaction) }
        holder.itemView.setOnLongClickListener {
            onLongClick(it, transaction)
            true
        }
    }

    override fun getItemCount() = transactions.size

    private fun extractDriveFileId(url: String): String? {
        return try {
            val uri = Uri.parse(url)
            if (url.contains("/d/")) {
                url.substringAfter("/d/").substringBefore("/")
            } else {
                uri.getQueryParameter("id")
            }
        } catch (e: Exception) {
            null
        }
    }
}
