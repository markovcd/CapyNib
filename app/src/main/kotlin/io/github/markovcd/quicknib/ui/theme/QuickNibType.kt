package io.github.markovcd.quicknib.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.markovcd.quicknib.R

/** Figtree (OFL, licenses/Figtree-OFL.txt) on every Material text style, with QuickNib's sizes where they differ. */
object QuickNibType {
    private val figtree = FontFamily(
        Font(R.font.figtree_regular, FontWeight.Normal),
        Font(R.font.figtree_medium, FontWeight.Medium),
        Font(R.font.figtree_semibold, FontWeight.SemiBold),
    )

    private fun TextStyle.sized(size: Int, line: Int, weight: FontWeight, spacing: Double = 0.0) =
        copy(fontSize = size.sp, lineHeight = line.sp, fontWeight = weight, letterSpacing = spacing.sp)

    val typography: Typography = Typography().run {
        Typography(
            displayLarge = displayLarge.copy(fontFamily = figtree),
            displayMedium = displayMedium.copy(fontFamily = figtree),
            displaySmall = displaySmall.copy(fontFamily = figtree),
            headlineLarge = headlineLarge.copy(fontFamily = figtree),
            headlineMedium = headlineMedium.copy(fontFamily = figtree),
            headlineSmall = headlineSmall.copy(fontFamily = figtree).sized(24, 32, FontWeight.SemiBold),
            titleLarge = titleLarge.copy(fontFamily = figtree).sized(22, 28, FontWeight.SemiBold),
            titleMedium = titleMedium.copy(fontFamily = figtree).sized(16, 22, FontWeight.SemiBold),
            titleSmall = titleSmall.copy(fontFamily = figtree).sized(15, 20, FontWeight.SemiBold),
            bodyLarge = bodyLarge.copy(fontFamily = figtree).sized(17, 26, FontWeight.Normal),
            bodyMedium = bodyMedium.copy(fontFamily = figtree).sized(14, 20, FontWeight.Normal),
            bodySmall = bodySmall.copy(fontFamily = figtree),
            labelLarge = labelLarge.copy(fontFamily = figtree).sized(15, 20, FontWeight.SemiBold),
            labelMedium = labelMedium.copy(fontFamily = figtree).sized(12, 16, FontWeight.SemiBold, spacing = 0.6),
            labelSmall = labelSmall.copy(fontFamily = figtree),
        )
    }
}
