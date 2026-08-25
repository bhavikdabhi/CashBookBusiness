package com.cashbk.app.utils

import android.content.Context
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.fragment.app.Fragment
import com.cashbk.app.R

object CustomToast {

    fun show(
        context: Context?,
        message: CharSequence?,
        duration: Int = Toast.LENGTH_SHORT,
        @DrawableRes iconResId: Int = R.drawable.ic_cash_volate
    ) {
        if (context == null || message.isNullOrEmpty()) return

        try {
            val inflater = LayoutInflater.from(context)
            val layout: View = inflater.inflate(R.layout.layout_custom_toast, null)

            val tvMessage = layout.findViewById<TextView>(R.id.toast_message)
            val imgIcon = layout.findViewById<ImageView>(R.id.toast_icon)

            tvMessage.text = message
            if (iconResId != 0) {
                imgIcon.setImageResource(iconResId)
                imgIcon.visibility = View.VISIBLE
            } else {
                imgIcon.visibility = View.GONE
            }

            @Suppress("DEPRECATION")
            val toast = Toast(context.applicationContext).apply {
                this.duration = duration
                this.view = layout
                setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, 150)
            }
            toast.show()
        } catch (e: Exception) {
            Toast.makeText(context, message, duration).show()
        }
    }
}

fun Context.showToast(message: CharSequence?, duration: Int = Toast.LENGTH_SHORT) {
    CustomToast.show(this, message, duration)
}

fun Fragment.showToast(message: CharSequence?, duration: Int = Toast.LENGTH_SHORT) {
    context?.showToast(message, duration)
}
