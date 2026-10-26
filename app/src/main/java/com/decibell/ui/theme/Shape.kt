package com.decibell.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes

val DecibellShapes = Shapes(
    extraSmall = RoundedCornerShape(DecibellDimens.RadiusInput),
    small = RoundedCornerShape(DecibellDimens.RadiusButton),
    medium = RoundedCornerShape(DecibellDimens.RadiusCard),
    large = RoundedCornerShape(DecibellDimens.RadiusSheet),
    extraLarge = RoundedCornerShape(DecibellDimens.RadiusSheet)
)
