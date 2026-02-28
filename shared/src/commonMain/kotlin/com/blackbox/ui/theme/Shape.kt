package com.blackbox.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * BlackBox shape system — cyberpunk aesthetic.
 *
 * Sharp, angular corners reinforce the terminal/hacker visual language.
 * Minimal rounding keeps the UI feeling precise and technical.
 */
val BlackBoxShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(4.dp),
    large = RoundedCornerShape(6.dp),
    extraLarge = RoundedCornerShape(8.dp),
)
