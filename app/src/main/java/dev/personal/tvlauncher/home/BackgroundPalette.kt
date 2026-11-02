package dev.personal.tvlauncher.home

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import dev.personal.tvlauncher.R
import dev.personal.tvlauncher.settings.BackgroundTheme

internal val BackgroundTheme.titleRes: Int get() = when (this) {
    BackgroundTheme.MIDNIGHT -> R.string.background_midnight
    BackgroundTheme.OCEAN -> R.string.background_ocean
    BackgroundTheme.FOREST -> R.string.background_forest
    BackgroundTheme.PLUM -> R.string.background_plum
    BackgroundTheme.BURGUNDY -> R.string.background_burgundy
    BackgroundTheme.GRAPHITE -> R.string.background_graphite
}

internal fun backgroundGradient(theme: BackgroundTheme): GradientDrawable {
    val (ambient, base) = when (theme) {
        BackgroundTheme.MIDNIGHT -> TvColors.ambient to TvColors.background
        BackgroundTheme.OCEAN -> Color.rgb(18, 63, 78) to Color.rgb(7, 24, 32)
        BackgroundTheme.FOREST -> Color.rgb(25, 61, 45) to Color.rgb(9, 24, 18)
        BackgroundTheme.PLUM -> Color.rgb(59, 37, 76) to Color.rgb(22, 14, 31)
        BackgroundTheme.BURGUNDY -> Color.rgb(72, 32, 46) to Color.rgb(29, 12, 19)
        BackgroundTheme.GRAPHITE -> Color.rgb(48, 53, 61) to Color.rgb(16, 18, 22)
    }
    return GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(ambient, base, base))
}
