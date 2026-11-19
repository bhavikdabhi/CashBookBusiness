package com.cashbk.app.utils

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.*
import android.os.Build
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import kotlin.math.hypot

object ThemeAnimationHelper {
    private var snapshotBitmap: Bitmap? = null
    private var originX: Int = 0
    private var originY: Int = 0
    private var isTargetDark: Boolean = false

    val hasPendingAnimation: Boolean
        get() = snapshotBitmap != null

    /**
     * Seamlessly transitions the theme without ANY blink or screen flash:
     * 1. Captures full window screenshot with windowBackground.
     * 2. Saves theme preference synchronously.
     * 3. Starts new Activity instance with FLAG_ACTIVITY_NO_ANIMATION and 0ms window transitions.
     * 4. Finishes old Activity with 0ms transition.
     * 5. The new Activity creates directly in the new theme with localNightMode,
     *    and immediately mounts the screenshot overlay so there is ZERO blank frame.
     * 6. Smoothly expands circular wave revealing the new theme over 750ms.
     */
    fun switchThemeWithAnimation(
        activity: AppCompatActivity,
        sourceView: View?,
        newNightMode: Int,
        themePrefValue: String,
        currentBusinessId: String? = null,
        selectedTabId: Int = 0
    ) {
        if (activity.isFinishing || activity.isDestroyed) return

        isTargetDark = when (newNightMode) {
            AppCompatDelegate.MODE_NIGHT_YES -> true
            AppCompatDelegate.MODE_NIGHT_NO -> false
            else -> {
                val currentNightMode = activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                currentNightMode != Configuration.UI_MODE_NIGHT_YES
            }
        }

        // 1. Commit theme preference synchronously so new Activity reads it immediately in onCreate
        activity.getSharedPreferences("theme_prefs", AppCompatActivity.MODE_PRIVATE)
            .edit()
            .putString("app_theme", themePrefValue)
            .commit()

        // 2. Capture screenshot of the full window
        val decorView = activity.window.decorView
        if (decorView.width > 0 && decorView.height > 0) {
            try {
                clearBitmap()
                val bitmap = Bitmap.createBitmap(
                    decorView.width,
                    decorView.height,
                    Bitmap.Config.ARGB_8888
                )
                val canvas = Canvas(bitmap)

                // Fill window background color first to avoid any black/transparent artifacts
                val isCurrentDark = (activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                val fallbackColor = if (isCurrentDark) 0xFF121212.toInt() else 0xFFF8F9FA.toInt()

                try {
                    val attrs = intArrayOf(android.R.attr.windowBackground)
                    val ta = activity.theme.obtainStyledAttributes(attrs)
                    val bgDrawable = ta.getDrawable(0)
                    ta.recycle()
                    if (bgDrawable != null) {
                        bgDrawable.setBounds(0, 0, decorView.width, decorView.height)
                        bgDrawable.draw(canvas)
                    } else {
                        canvas.drawColor(fallbackColor)
                    }
                } catch (_: Throwable) {
                    canvas.drawColor(fallbackColor)
                }

                decorView.draw(canvas)
                snapshotBitmap = bitmap

                if (sourceView != null && sourceView.isAttachedToWindow) {
                    val location = IntArray(2)
                    sourceView.getLocationInWindow(location)
                    originX = location[0] + (sourceView.width / 2)
                    originY = location[1] + (sourceView.height / 2)
                } else {
                    originX = decorView.width / 2
                    originY = decorView.height / 2
                }
            } catch (t: Throwable) {
                clearBitmap()
            }
        }

        // 3. Launch new instance of Activity with NO_ANIMATION to prevent Activity.recreate() window blink
        val intent = Intent(activity, activity.javaClass).apply {
            activity.intent.extras?.let { putExtras(it) }
            if (!currentBusinessId.isNullOrEmpty()) {
                putExtra("businessId", currentBusinessId)
            }
            if (selectedTabId != 0) {
                putExtra("selected_nav_tab", selectedTabId)
            }
            putExtra("is_theme_transition", true)
            addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
        }

        activity.startActivity(intent)

        if (Build.VERSION.SDK_INT >= 34) {
            activity.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN, 0, 0)
            activity.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            activity.overridePendingTransition(0, 0)
        }

        activity.finish()

        if (Build.VERSION.SDK_INT >= 34) {
            activity.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            activity.overridePendingTransition(0, 0)
        }
    }

    /**
     * Executes the circular reveal animation on top of the newly loaded theme.
     * Places the old screenshot on top and expands a circular hole from (originX, originY)
     * revealing the new theme underneath smoothly over 750ms.
     */
    fun applyPendingThemeAnimation(
        activity: AppCompatActivity,
        rootView: View,
        onAnimationComplete: (() -> Unit)? = null
    ) {
        val bitmap = snapshotBitmap ?: return
        val decorView = activity.window.decorView as? ViewGroup ?: return

        val overlay = ThemeHoleOverlayView(
            context = activity,
            snapshot = bitmap,
            cx = originX,
            cy = originY,
            isTargetDark = isTargetDark
        )

        decorView.addView(
            overlay,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        overlay.post {
            val dx = maxOf(originX, overlay.width - originX)
            val dy = maxOf(originY, overlay.height - originY)
            val maxRadius = hypot(dx.toDouble(), dy.toDouble()).toFloat()

            val animator = ValueAnimator.ofFloat(0f, maxRadius).apply {
                duration = 750L
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener { va ->
                    overlay.revealRadius = va.animatedValue as Float
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        decorView.removeView(overlay)
                        clear()
                        // Update global night mode in background after transition finishes
                        val nightMode = if (isTargetDark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
                        AppCompatDelegate.setDefaultNightMode(nightMode)
                        onAnimationComplete?.invoke()
                    }

                    override fun onAnimationCancel(animation: Animator) {
                        decorView.removeView(overlay)
                        clear()
                        onAnimationComplete?.invoke()
                    }
                })
            }
            animator.start()
        }
    }

    fun clear() {
        clearBitmap()
        originX = 0
        originY = 0
    }

    private fun clearBitmap() {
        try {
            snapshotBitmap?.recycle()
        } catch (_: Throwable) {}
        snapshotBitmap = null
    }

    @SuppressLint("ViewConstructor")
    private class ThemeHoleOverlayView(
        context: Context,
        private val snapshot: Bitmap,
        private val cx: Int,
        private val cy: Int,
        isTargetDark: Boolean
    ) : View(context) {

        var revealRadius: Float = 0f
            set(value) {
                field = value
                invalidate()
            }

        private val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        }

        private val waveRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f * resources.displayMetrics.density
            color = if (isTargetDark) Color.parseColor("#8000C6FF") else Color.parseColor("#80FF9800")
        }

        init {
            setLayerType(LAYER_TYPE_HARDWARE, null)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (snapshot.isRecycled) return

            val saveCount = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
            canvas.drawBitmap(snapshot, 0f, 0f, null)

            if (revealRadius > 0f) {
                canvas.drawCircle(cx.toFloat(), cy.toFloat(), revealRadius, clearPaint)
                canvas.drawCircle(cx.toFloat(), cy.toFloat(), revealRadius, waveRingPaint)
            }

            canvas.restoreToCount(saveCount)
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean = true
    }
}
