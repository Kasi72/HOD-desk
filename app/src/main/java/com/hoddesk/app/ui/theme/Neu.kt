package com.hoddesk.app.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Named depth presets. They intentionally avoid Material elevation/ripples. */
object Neu {
    fun card(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(24.dp)): Modifier =
        modifier.shadow(7.dp, shape, ambientColor = Color(0x26544A3C), spotColor = Color(0x26544A3C))
            .border(BorderStroke(1.dp, MeridianColors.porcelainBorder), shape)

    fun raisedSmall(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(16.dp)): Modifier =
        modifier.shadow(4.dp, shape, ambientColor = Color(0x24544A3C), spotColor = Color(0x24544A3C))
            .border(BorderStroke(1.dp, MeridianColors.porcelainBorder), shape)

    fun inset(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(18.dp)): Modifier = modifier
        .border(BorderStroke(1.dp, MeridianColors.hairline), shape)

    fun darkHero(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(28.dp)): Modifier =
        modifier.shadow(10.dp, shape, ambientColor = Color(0x5215181E), spotColor = Color(0x5215181E))

    fun darkWell(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(16.dp)): Modifier = modifier
        .border(BorderStroke(1.dp, Color(0x33000000)), shape)
}
