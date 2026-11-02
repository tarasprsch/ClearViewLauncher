package dev.personal.tvlauncher.home

import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.Typeface
import android.graphics.Rect
import android.text.TextUtils
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.FrameLayout
import android.widget.TextView
import coil.load
import coil.request.Disposable
import dev.personal.tvlauncher.R

enum class CardPresentation { COMPACT_APP, APP, CONTENT }

class ContentCard(context: Context) : LinearLayout(context) {
    val art = ImageView(context)
    val title = TextView(context)
    private val artFrame = FrameLayout(context)
    private val placeholder = ImageView(context).apply {
        setImageResource(R.drawable.ic_artwork)
        imageTintList = android.content.res.ColorStateList.valueOf(TvColors.secondary)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private var request: Disposable? = null
    private var binding = 0L
    private var focusScale = CARD_FOCUS_SCALE
    init {
        orientation = VERTICAL; gravity = Gravity.CENTER
        clipChildren = false; clipToPadding = false
        isFocusable = true; isClickable = true; isLongClickable = false
        setPadding(context.dp(4), context.dp(4), context.dp(4), context.dp(4))
        art.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        artFrame.background = focusBackground(context)
        // Foreground is drawn after opaque posters and banners, keeping focus visible.
        artFrame.foreground = context.roundedSurface(android.graphics.Color.TRANSPARENT).apply {
            setStroke(context.dp(2), TvColors.accent); alpha = 0
        }
        artFrame.isDuplicateParentStateEnabled = true
        artFrame.clipToOutline = true
        artFrame.addView(placeholder, FrameLayout.LayoutParams(context.dp(26), context.dp(26), Gravity.CENTER))
        artFrame.addView(art, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))
        addView(artFrame, LayoutParams(-1, context.dp(128)))
        title.apply {
            textSize = TvTypography.cardCaption; setTextColor(TvColors.text); gravity = Gravity.CENTER_VERTICAL
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            maxLines = 2; ellipsize = TextUtils.TruncateAt.END
        }
        addView(title, LayoutParams(-1, context.dp(48)))
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        showFocus(animate = true)
    }

    private fun showFocus(animate: Boolean) {
        title.visibility = if (isFocused) VISIBLE else INVISIBLE
        artFrame.foreground.alpha = if (isFocused) 255 else 0
        applyFocusEffect(isFocused, scale = focusScale, animate = animate)
    }

    fun bind(label: String, uri: String?, drawable: Drawable?, logo: Boolean, ratio: Float, presentation: CardPresentation = CardPresentation.CONTENT) {
        recycle()
        val currentBinding = binding
        val geometry = context.cardGeometry(presentation, ratio)
        focusScale = geometry.focusScale
        val isApp = presentation != CardPresentation.CONTENT
        val iconOnly = isApp && logo
        val sidePadding = context.dp(if (presentation == CardPresentation.APP) 4 else 2)
        setPadding(sidePadding, context.dp(4), sidePadding, context.dp(4))
        val imageHeight = geometry.imageHeight
        val iconSize = context.dp(48)
        val width = geometry.width
        title.text = label; contentDescription = label
        title.maxLines = if (isApp) 1 else 2
        title.textSize = TvTypography.cardCaption
        title.gravity = if (isApp) Gravity.CENTER else (Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        title.includeFontPadding = true
        title.setPadding(context.dp(2), context.dp(6), context.dp(2), context.dp(2))
        title.layoutParams = LayoutParams(-1, geometry.titleHeight)
        artFrame.layoutParams = LayoutParams(-1, imageHeight)
        art.layoutParams = FrameLayout.LayoutParams(if (iconOnly) iconSize else -1, if (iconOnly) iconSize else imageHeight, Gravity.CENTER)
        art.scaleType = if (logo) ImageView.ScaleType.FIT_CENTER else ImageView.ScaleType.CENTER_CROP
        layoutParams = androidx.recyclerview.widget.RecyclerView.LayoutParams(width, geometry.height).apply {
            val sideMargin = context.dp(if (presentation == CardPresentation.APP) 6 else 2)
            setMargins(sideMargin, context.dp(6), sideMargin, context.dp(6))
        }
        placeholder.visibility = if (drawable == null) VISIBLE else GONE
        if (drawable != null) art.setImageDrawable(drawable)
        else if (uri != null) request = art.load(uri) {
            size(if (isApp) imageHeight else (width - context.dp(16)).coerceAtLeast(1), imageHeight)
            crossfade(false)
            placeholder(null); error(null); fallback(null)
            listener(
                onSuccess = { _, _ -> if (binding == currentBinding) placeholder.visibility = GONE },
                onError = { _, _ -> if (binding == currentBinding) placeholder.visibility = VISIBLE },
            )
        }
        showFocus(animate = false)
    }

    fun recycle() {
        binding++
        request?.dispose(); request = null
        resetFocusEffect()
        artFrame.foreground.alpha = 0
        art.setImageDrawable(null); title.text = ""; contentDescription = null
        title.visibility = INVISIBLE
        placeholder.visibility = VISIBLE
    }
}
