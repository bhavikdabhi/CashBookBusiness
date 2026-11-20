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
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import kotlin.math.hypot

object ThemeAnimationHelper {
    private var snapshotBitmap: Bitmap? = null
    private var originX: Int = 0
    private var originY: Int = 0
    private var isTargetDark: Boolean = false

    fun hasPendingAnimation(activity: Activity): Boolean {
        return activity.intent.getBooleanExtra("is_theme_transition", false) && snapshotBitmap != null
    }

    /**
     * Seamlessly transitions between themes with a smooth circular ripple reveal:
     * 1. Captures snapshot of the full window.
     * 2. Overlays snapshot on the current activity to eliminate any blank gap.
     * 3. Seamlessly starts the new Activity instance with 0ms transitions.
     * 4. Mounts snapshot on the new Activity and smoothly expands the circular wave
     *    from the button clicked, revealing the new theme underneath with zero blink.
     * 5. No progress bars, no text — pure visual wave animation.
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

        val decorView = activity.window.decorView as? ViewGroup
        val width = decorView?.width ?: 0
        val height = decorView?.height ?: 0

        if (decorView != null && width > 0 && height > 0) {
            try {
                clearBitmap()
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)

                // Draw fallback window background color
                val isCurrentDark = (activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                val fallbackColor = if (isCurrentDark) 0xFF192626.toInt() else 0xFFF5FAFA.toInt()
                canvas.drawColor(fallbackColor)

                decorView.draw(canvas)
                snapshotBitmap = bitmap

                // Calculate origin from clicked source view
                if (sourceView != null && sourceView.isAttachedToWindow) {
                    val location = IntArray(2)
                    sourceView.getLocationInWindow(location)
                    originX = location[0] + sourceView.width / 2
                    originY = location[1] + sourceView.height / 2
                } else {
                    originX = width / 2
                    originY = height / 2
                }

                // Freeze the current screen with snapshot overlay to guarantee zero blink while launching
                val oldOverlay = ImageView(activity).apply {
                    setImageBitmap(bitmap)
                    scaleType = ImageView.ScaleType.FIT_XY
                    elevation = 9999f
                }
                decorView.addView(
                    oldOverlay,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
            } catch (_: Throwable) {
                // If screenshot fails, clear snapshot
                clearBitmap()
            }
        }

        // 2. Launch new Activity seamlessly with 0ms transition
        (sourceView ?: activity.window.decorView).postDelayed({
            val intent = Intent(activity, activity.javaClass).apply {
                activity.intent.extras?.let { putExtras(it) }
                if (!currentBusinessId.isNullOrEmpty()) {
                    putExtra("businessId", currentBusinessId)
                }
                if (selectedTabId != 0) {
                    putExtra("selected_nav_tab", selectedTabId)
                }
                putExtra("is_theme_transition", true)
                putExtra("is_target_dark", isTargetDark)
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
        }, 50)
    }

    /**
     * Called in Activity's onCreate() after setContentView.
     * Executes circular wave reveal expanding from origin across the screen.
     */
    fun applyPendingThemeAnimation(
        activity: AppCompatActivity,
        rootView: View,
        onAnimationComplete: (() -> Unit)? = null
    ) {
        val bitmap = snapshotBitmap
        if (bitmap == null || !hasPendingAnimation(activity)) {
            clear()
            return
        }

        val decorView = activity.window.decorView as? ViewGroup ?: run {
            clear()
            return
        }

        val cx = if (originX > 0) originX else decorView.width / 2
        val cy = if (originY > 0) originY else decorView.height / 2

        val overlay = ThemeHoleOverlayView(
            context = activity,
            snapshot = bitmap,
            cx = cx,
            cy = cy,
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
            val viewWidth = overlay.width.takeIf { it > 0 } ?: decorView.width
            val viewHeight = overlay.height.takeIf { it > 0 } ?: decorView.height

            val dx = maxOf(cx, viewWidth - cx)
            val dy = maxOf(cy, viewHeight - cy)
            val maxRadius = hypot(dx.toDouble(), dy.toDouble()).toFloat()

            val animator = ValueAnimator.ofFloat(0f, maxRadius).apply {
                duration = 600L
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener { va ->
                    overlay.revealRadius = va.animatedValue as Float
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        decorView.removeView(overlay)
                        clear()
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

        private val fullRect = Path()
        private val circlePath = Path()
        private val holePath = Path()

        private val waveRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f * resources.displayMetrics.density
            color = if (isTargetDark) Color.parseColor("#8002929A") else Color.parseColor("#8000C6FF")
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            fullRect.reset()
            fullRect.addRect(0f, 0f, w.toFloat(), h.toFloat(), Path.Direction.CW)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (snapshot.isRecycled) return

            if (revealRadius <= 0f) {
                canvas.drawBitmap(snapshot, 0f, 0f, null)
            } else {
                circlePath.reset()
                circlePath.addCircle(cx.toFloat(), cy.toFloat(), revealRadius, Path.Direction.CW)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    canvas.save()
                    canvas.clipOutPath(circlePath)
                    canvas.drawBitmap(snapshot, 0f, 0f, null)
                    canvas.restore()
                } else {
                    canvas.save()
                    holePath.reset()
                    holePath.op(fullRect, circlePath, Path.Op.DIFFERENCE)
                    canvas.clipPath(holePath)
                    canvas.drawBitmap(snapshot, 0f, 0f, null)
                    canvas.restore()
                }

                // Glowing wave ripple border
                canvas.drawCircle(cx.toFloat(), cy.toFloat(), revealRadius, waveRingPaint)
            }
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean = true
    }
}
