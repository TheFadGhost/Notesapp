package com.fadghost.notesapp.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Depth helpers for the Aura 3-plane surface model (V2-SPEC #9, visual.md §2). Warm
 * tinted paper shadows + a hairline top highlight — never a hard black drop shadow.
 *
 * Perf: [Modifier.shadow] rasterises once and is cheap on static sheets. We map our
 * blur radius to Compose elevation empirically (blur 10 ≈ elev 6, blur 22 ≈ elev 14)
 * and pass the pre-alpha'd tint as both ambient + spot colour so the cast stays warm.
 * `clip = false` lets the shadow escape the rounded rect.
 */

private fun ThemeShadow.elevation(): Dp = (blur.value * 0.6f).dp

/** Soft contact shadow for the Sheet plane: cards, search bar, section cards. */
@Composable
fun Modifier.auraSheetShadow(shape: Shape): Modifier {
    val s = Aura.tokens.shadows.sheet
    return this.shadow(
        elevation = s.elevation(),
        shape = shape,
        clip = false,
        ambientColor = s.color,
        spotColor = s.color
    )
}

/** Ambient lift shadow for the Float plane: capture popup, dialogs, banners. */
@Composable
fun Modifier.auraFloatShadow(shape: Shape): Modifier {
    val s = Aura.tokens.shadows.float
    return this.shadow(
        elevation = s.elevation(),
        shape = shape,
        clip = false,
        ambientColor = s.color,
        spotColor = s.color
    )
}

/**
 * A 1-dp top-edge inner highlight — the paper bezel that carries the lift on dark
 * themes (visual.md §2.3). Apply AFTER the surface fill so it sits on top of it.
 * [cornerRadius] insets the highlight so it doesn't overshoot the rounded corners.
 */
@Composable
fun Modifier.auraTopHighlight(cornerRadius: Dp): Modifier {
    val color: Color = Aura.tokens.innerHighlight
    return this.drawWithContent {
        drawContent()
        val inset = cornerRadius.toPx()
        val yy = 0.75.dp.toPx()
        drawLine(
            color = color,
            start = Offset(inset, yy),
            end = Offset(size.width - inset, yy),
            strokeWidth = 1.dp.toPx()
        )
    }
}
