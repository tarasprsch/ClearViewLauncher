package dev.personal.tvlauncher.home

import android.content.Context
import android.graphics.Typeface
import android.text.TextPaint
import android.util.TypedValue
import kotlin.math.ceil

internal const val CARD_FOCUS_SCALE = 1.24f
private const val COMPACT_APP_WIDTH_DP = 120

internal data class CardGeometry(
    val width: Int,
    val imageHeight: Int,
    val titleHeight: Int,
    val height: Int,
    val rowPadding: Int,
    val rowHeight: Int,
    val focusScale: Float,
)

internal fun Context.cardGeometry(presentation: CardPresentation, ratio: Float = 16f / 9f): CardGeometry {
    val compact = presentation == CardPresentation.COMPACT_APP
    val app = presentation != CardPresentation.CONTENT
    val imageHeight = dp(when { compact -> 64; app -> 72; else -> 104 })
    val width = dp(when { compact -> COMPACT_APP_WIDTH_DP; app -> 136; else -> (104f * ratio.coerceIn(0.5f, 2.4f)).toInt() + 16 })
    val paint = TextPaint().apply {
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, TvTypography.cardCaption, resources.displayMetrics)
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
    val font = paint.fontMetricsInt
    val lines = if (app) 1 else 2
    val lineHeight = (font.descent - font.ascent + font.leading).coerceAtLeast(1)
    val textHeight = font.bottom - font.top + (lines - 1) * lineHeight
    val titleHeight = maxOf(dp(if (app) 28 else 44), textHeight + dp(8))
    val height = imageHeight + titleHeight + dp(8)
    // Wide content grows by the same horizontal distance as a compact app.
    // Cap the scale so portrait panels never become disproportionately large.
    val focusScale = if (app) CARD_FOCUS_SCALE else
        1f + (CARD_FOCUS_SCALE - 1f) * minOf(1f, dp(COMPACT_APP_WIDTH_DP).toFloat() / width)
    // A row can mix artwork widths: always reserve clearance for the maximum scale.
    val growth = ceil(height * (CARD_FOCUS_SCALE - 1f) / 2f).toInt()
    val rowPadding = maxOf(dp(6), growth - dp(6))
    return CardGeometry(width, imageHeight, titleHeight, height, rowPadding, height + 2 * (dp(6) + rowPadding), focusScale)
}
