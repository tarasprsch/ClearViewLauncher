package dev.personal.tvlauncher.home

import android.content.Context
import android.graphics.Typeface
import android.text.TextPaint
import android.util.TypedValue
import kotlin.math.ceil

internal const val CARD_FOCUS_SCALE = 1.24f

internal data class CardGeometry(
    val width: Int,
    val imageHeight: Int,
    val titleHeight: Int,
    val height: Int,
    val rowPadding: Int,
    val rowHeight: Int,
)

internal fun Context.cardGeometry(presentation: CardPresentation, ratio: Float = 16f / 9f): CardGeometry {
    val compact = presentation == CardPresentation.COMPACT_APP
    val app = presentation != CardPresentation.CONTENT
    val imageHeight = dp(when { compact -> 64; app -> 72; else -> 104 })
    val width = dp(when { compact -> 120; app -> 136; else -> (104f * ratio.coerceIn(0.5f, 2.4f)).toInt() + 16 })
    val paint = TextPaint().apply {
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 14f, resources.displayMetrics)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    val font = paint.fontMetricsInt
    val lines = if (app) 1 else 2
    val lineHeight = (font.descent - font.ascent + font.leading).coerceAtLeast(1)
    val textHeight = font.bottom - font.top + (lines - 1) * lineHeight
    val titleHeight = maxOf(dp(if (app) 28 else 44), textHeight + dp(8))
    val height = imageHeight + titleHeight + dp(8)
    val growth = ceil(height * (CARD_FOCUS_SCALE - 1f) / 2f).toInt()
    val rowPadding = maxOf(dp(6), growth - dp(6))
    return CardGeometry(width, imageHeight, titleHeight, height, rowPadding, height + 2 * (dp(6) + rowPadding))
}
