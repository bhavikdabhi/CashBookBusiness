package com.cashbk.app.utils

import android.content.Context
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.fragment.app.Fragment

/**
 * Theme utilities for resolving Material Design 3 theme attribute colors
 * at runtime. This follows the same pattern documented in the GK Quiz app's
 * theme architecture and avoids hardcoding colors in Kotlin/Java code.
 *
 * Usage (in a Fragment):
 *   val primaryColor = resolveThemeColor(com.google.android.material.R.attr.colorPrimary)
 *   myView.setBackgroundColor(primaryColor)
 *
 * Usage (in an Activity or any Context):
 *   val onSurface = context.resolveThemeColor(com.google.android.material.R.attr.colorOnSurface)
 */

/**
 * Resolves a theme color attribute from the given [Context]'s current theme.
 *
 * @param attrRes The attribute resource ID (e.g., [com.google.android.material.R.attr.colorPrimary])
 * @return The resolved color as a packed ARGB [Int]
 */
@ColorInt
fun Context.resolveThemeColor(@AttrRes attrRes: Int): Int {
    val typedValue = TypedValue()
    theme.resolveAttribute(attrRes, typedValue, true)
    return typedValue.data
}

/**
 * Resolves a theme color attribute from a [Fragment]'s hosting context.
 *
 * @param attrRes The attribute resource ID
 * @return The resolved color as a packed ARGB [Int]
 */
@ColorInt
fun Fragment.resolveThemeColor(@AttrRes attrRes: Int): Int =
    requireContext().resolveThemeColor(attrRes)

/**
 * Checks whether the app is currently in night (dark) mode.
 *
 * @return `true` if dark mode is active, `false` for light mode
 */
fun Context.isDarkMode(): Boolean {
    val nightModeFlags = resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK
    return nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES
}

/**
 * Checks whether the app is currently in night (dark) mode from a Fragment.
 *
 * @return `true` if dark mode is active, `false` for light mode
 */
fun Fragment.isDarkMode(): Boolean = requireContext().isDarkMode()

/**
 * Dynamically controls status bar icon appearance.
 *
 * Call this in [Fragment.onResume] and revert in [Fragment.onPause] when
 * a fragment has a dark-tinted header that requires white status bar icons
 * regardless of system theme.
 *
 * @param lightIcons `true` for dark icons (suitable for light backgrounds),
 *                   `false` for light icons (suitable for dark backgrounds)
 */
fun Fragment.setStatusBarIconAppearance(lightIcons: Boolean) {
    activity?.window?.let { window ->
        val controller = androidx.core.view.WindowInsetsControllerCompat(
            window, window.decorView
        )
        controller.isAppearanceLightStatusBars = lightIcons
    }
}
