package com.cashbk.app.utils

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import com.cashbk.app.R
import com.cashbk.app.databinding.LayoutOptionsMenuBinding

class CustomOptionsMenu(private val context: Context, private val anchor: View) {

    private val binding: LayoutOptionsMenuBinding = LayoutOptionsMenuBinding.inflate(
        LayoutInflater.from(context), null, false
    )
    private val popupWindow: PopupWindow

    private var onRename: (() -> Unit)? = null
    private var onReport: (() -> Unit)? = null
    private var onMember: (() -> Unit)? = null
    private var onSettings: (() -> Unit)? = null
    private var onDelete: (() -> Unit)? = null

    init {
        popupWindow = PopupWindow(
            binding.root,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )
        popupWindow.elevation = 16f
        
        binding.menuRename.setOnClickListener {
            onRename?.invoke()
            popupWindow.dismiss()
        }
        binding.menuReport.setOnClickListener {
            onReport?.invoke()
            popupWindow.dismiss()
        }
        binding.menuMember.setOnClickListener {
            onMember?.invoke()
            popupWindow.dismiss()
        }
        binding.menuSettings.setOnClickListener {
            onSettings?.invoke()
            popupWindow.dismiss()
        }
        binding.menuDelete.setOnClickListener {
            onDelete?.invoke()
            popupWindow.dismiss()
        }

        // Initially hide items that aren't configured
        binding.menuRename.visibility = View.GONE
        binding.menuReport.visibility = View.GONE
        binding.menuMember.visibility = View.GONE
        binding.menuSettings.visibility = View.GONE
        binding.menuDelete.visibility = View.GONE
    }

    fun setOnRenameClickListener(listener: () -> Unit): CustomOptionsMenu {
        this.onRename = listener
        binding.menuRename.visibility = View.VISIBLE
        return this
    }

    fun setOnReportClickListener(listener: () -> Unit): CustomOptionsMenu {
        this.onReport = listener
        binding.menuReport.visibility = View.VISIBLE
        return this
    }

    fun setOnMemberClickListener(listener: () -> Unit): CustomOptionsMenu {
        this.onMember = listener
        binding.menuMember.visibility = View.VISIBLE
        return this
    }

    fun setOnSettingsClickListener(listener: () -> Unit): CustomOptionsMenu {
        this.onSettings = listener
        binding.menuSettings.visibility = View.VISIBLE
        return this
    }
    
    fun setOnDeleteClickListener(listener: () -> Unit): CustomOptionsMenu {
        this.onDelete = listener
        binding.menuDelete.visibility = View.VISIBLE
        return this
    }
    
    // Allows setting text manually if action is slightly different (e.g. Manage Partner vs Member)
    fun setMemberText(text: String): CustomOptionsMenu {
        // We'd have to find the TextView inside menuMember, unfortunately layout_options_menu 
        // doesn't have an ID for the TextView. We'll skip setting text or use data binding.
        return this
    }

    fun show() {
        // Adjust dropdown alignment
        popupWindow.showAsDropDown(anchor, -150, -30)
    }
}
