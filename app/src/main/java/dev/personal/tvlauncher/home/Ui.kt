package dev.personal.tvlauncher.home

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.content.res.ColorStateList
import android.animation.ValueAnimator
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.View
import android.view.KeyEvent
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
object TvTypography {
    const val pageTitle = 26f
    const val section = 18f
    const val group = 14f
    const val item = 16f
    const val cardCaption = 12f
    const val description = 13f
}

object TvColors {
    val background = Color.rgb(12, 20, 33)
    val ambient = Color.rgb(27, 52, 81)
    val surface = Color.rgb(26, 40, 59)
    val focused = Color.rgb(43, 66, 90)
    val text = Color.rgb(244, 247, 252)
    val secondary = Color.rgb(168, 184, 203)
    val accent = Color.rgb(192, 230, 255)
    val artwork = Color.rgb(32, 50, 71)
}

fun Context.roundedSurface(color: Int, radius: Int = 16): GradientDrawable = GradientDrawable().apply {
    setColor(color); cornerRadius = dp(radius).toFloat()
}

fun Context.dateFocusBackground() = StateListDrawable().apply {
    addState(intArrayOf(android.R.attr.state_focused), roundedSurface(Color.argb(20, 192, 230, 255), 8))
    addState(intArrayOf(), roundedSurface(Color.TRANSPARENT, 8))
}

fun focusBackground(context: Context, control: Boolean = false): StateListDrawable {
    fun shape(color: Int, focused: Boolean) = context.roundedSurface(color, if (control) 24 else 16).apply {
        if (focused && !control) setStroke(context.dp(2), TvColors.accent)
    }
    return StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_pressed), shape(if (control) TvColors.accent else TvColors.focused, true))
        addState(intArrayOf(android.R.attr.state_focused), shape(if (control) TvColors.accent else TvColors.focused, true))
        addState(intArrayOf(), shape(TvColors.surface, false))
    }
}
fun Context.label(text: String, size: Float = 18f): TextView = TextView(this).apply {
    this.text = text; textSize = size; setTextColor(if (size >= 18f) TvColors.text else TvColors.secondary)
    typeface = Typeface.create(if (size >= 18f) "sans-serif-medium" else "sans-serif", Typeface.NORMAL)
    includeFontPadding = false
    setPadding(0, dp(3), 0, dp(3))
}
internal fun controlTextColors() = ColorStateList(arrayOf(
        intArrayOf(-android.R.attr.state_enabled), intArrayOf(android.R.attr.state_pressed),
        intArrayOf(android.R.attr.state_focused), intArrayOf(),
    ), intArrayOf(TvColors.secondary, TvColors.background, TvColors.background, TvColors.text))

fun Context.action(text: String, key: String, icon: Int? = null, block: () -> Unit): Button = Button(this).apply {
    this.text = text; tag = key; isAllCaps = false; textSize = TvTypography.item
    val colors = controlTextColors()
    setTextColor(colors); background = focusBackground(context, control = true)
    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    stateListAnimator = null
    setPadding(dp(18), dp(8), dp(18), dp(8)); minHeight = dp(46)
    if (icon != null) {
        setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, 0, 0)
        compoundDrawableTintList = colors; compoundDrawablePadding = dp(10)
    }
    isFocusable = true; setOnClickListener { block() }
    layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) }
    focusVisuals()
}

fun View.applyFocusEffect(focused: Boolean, scale: Float = 1.05f, animate: Boolean = true) {
    animate().cancel()
    val targetScale = if (focused) scale else 1f
    val targetElevation = if (focused) context.dp(8).toFloat() else 0f
    if (animate && ValueAnimator.areAnimatorsEnabled()) {
        animate().scaleX(targetScale).scaleY(targetScale).translationZ(targetElevation)
            .setDuration(150).setInterpolator(DecelerateInterpolator()).start()
    } else {
        scaleX = targetScale; scaleY = targetScale; translationZ = targetElevation
    }
}

fun View.resetFocusEffect() {
    animate().cancel(); scaleX = 1f; scaleY = 1f; translationZ = 0f; elevation = 0f
}

fun View.focusVisuals(scale: Float = 1f) {
    setOnFocusChangeListener { view, focused ->
        view.alpha = if (view.isEnabled) 1f else 0.4f
        view.applyFocusEffect(focused, scale)
    }
}

fun Context.notice(text: String): TextView = label(text, TvTypography.description).apply {
    background = roundedSurface(TvColors.surface, 12)
    setPadding(dp(16), dp(12), dp(16), dp(12))
    setLineSpacing(dp(3).toFloat(), 1f)
    layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(8), 0, dp(8)) }
}

/** Observe completed D-pad moves in the view tree, including moves handled by child rows. */
class RemoteRoot(context: Context, private val navigated: (View?) -> Unit) : LinearLayout(context) {
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val handled = super.dispatchKeyEvent(event)
        if (event.action == KeyEvent.ACTION_DOWN && event.keyCode in listOf(
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_TAB,
            )) navigated(rootView.findFocus())
        return handled
    }
}
