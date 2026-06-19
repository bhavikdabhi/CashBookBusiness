package com.cashbk.app.utils

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.cashbk.app.R
import com.cashbk.app.databinding.DialogCustomAlertBinding

class CustomAlertDialog(private val context: Context) {

    private val dialog: AlertDialog
    private val binding: DialogCustomAlertBinding

    init {
        binding = DialogCustomAlertBinding.inflate(LayoutInflater.from(context))
        dialog = AlertDialog.Builder(context)
            .setView(binding.root)
            .setCancelable(true)
            .create()

        // Make background transparent so custom rounded corners and stroke are visible
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        
        // Apply smooth slide bottom-to-center / center-to-bottom transition animations
        dialog.window?.attributes?.windowAnimations = R.style.DialogAnimation

        binding.btnNeutral.visibility = View.GONE
    }

    fun setTitle(title: String): CustomAlertDialog {
        binding.tvDialogTitle.text = title
        binding.tvDialogTitle.visibility = if (title.isEmpty()) View.GONE else View.VISIBLE
        return this
    }

    fun setMessage(message: String): CustomAlertDialog {
        binding.tvDialogMessage.text = message
        binding.tvDialogMessage.visibility = if (message.isEmpty()) View.GONE else View.VISIBLE
        return this
    }

    fun setIcon(@DrawableRes iconRes: Int, tintColor: Int? = null): CustomAlertDialog {
        binding.ivDialogIcon.setImageResource(iconRes)
        binding.layoutIconContainer.visibility = View.VISIBLE
        if (tintColor != null) {
            binding.ivDialogIcon.setColorFilter(tintColor)
            binding.viewIconGlow.backgroundTintList = android.content.res.ColorStateList.valueOf(tintColor)
        } else {
            // Default tint
            val primaryColor = ContextCompat.getColor(context, R.color.primary_color)
            binding.ivDialogIcon.setColorFilter(primaryColor)
            binding.viewIconGlow.backgroundTintList = android.content.res.ColorStateList.valueOf(primaryColor)
        }
        return this
    }

    fun setPositiveButton(text: String, onClickListener: (() -> Unit)? = null): CustomAlertDialog {
        binding.btnPositive.text = text
        binding.btnPositive.visibility = View.VISIBLE
        binding.btnPositive.setOnClickListener {
            onClickListener?.invoke()
            dialog.dismiss()
        }
        return this
    }

    fun setNegativeButton(text: String, onClickListener: (() -> Unit)? = null): CustomAlertDialog {
        binding.btnNegative.text = text
        binding.btnNegative.visibility = View.VISIBLE
        binding.btnNegative.setOnClickListener {
            onClickListener?.invoke()
            dialog.dismiss()
        }
        return this
    }

    fun setNeutralButton(text: String, onClickListener: (() -> Unit)? = null): CustomAlertDialog {
        binding.btnNeutral.text = text
        binding.btnNeutral.visibility = View.VISIBLE
        binding.btnNeutral.setOnClickListener {
            onClickListener?.invoke()
            dialog.dismiss()
        }
        return this
    }

    fun setCancelable(cancelable: Boolean): CustomAlertDialog {
        dialog.setCancelable(cancelable)
        return this
    }

    fun show(): AlertDialog {
        // Adjust button layouts visibility
        val hasPositive = binding.btnPositive.visibility == View.VISIBLE
        val hasNegative = binding.btnNegative.visibility == View.VISIBLE
        
        if (hasPositive || hasNegative) {
            binding.layoutButtons.visibility = View.VISIBLE
            if (hasPositive && hasNegative) {
                binding.btnSpacer.visibility = View.VISIBLE
            } else {
                binding.btnSpacer.visibility = View.GONE
            }
        } else {
            binding.layoutButtons.visibility = View.GONE
        }

        dialog.show()

        // Force appropriate dialog width for responsive scaling on different screen sizes
        val width = (context.resources.displayMetrics.widthPixels * 0.85).toInt()
        dialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        return dialog
    }

    fun dismiss() {
        dialog.dismiss()
    }
}
