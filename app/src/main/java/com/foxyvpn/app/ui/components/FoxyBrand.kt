package com.foxyvpn.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val FoxyGradient = Brush.linearGradient(listOf(Color(0xFFFFB347), Color(0xFFFF7139), Color(0xFFE0245E)))

/** "Foxy Pro" wordmark: cursive gradient lettering plus a PRO badge. */
@Composable
fun FoxyWordmark(modifier: Modifier = Modifier, size: TextUnit = 32.sp) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Foxy",
            style = TextStyle(
                brush = FoxyGradient,
                fontFamily = FontFamily.Cursive,
                fontWeight = FontWeight.Bold,
                fontSize = size,
                letterSpacing = 0.5.sp,
            ),
        )
        Spacer(Modifier.width(6.dp))
        Box(
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(FoxyGradient)
                .padding(horizontal = 7.dp, vertical = 2.dp),
        ) {
            Text(
                "PRO",
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = (size.value * 0.4f).sp,
                letterSpacing = 1.5.sp,
            )
        }
    }
}

/** Soft orange-tinted gradient used behind the main screens. */
@Composable
fun Modifier.foxyBackground(): Modifier {
    val cs = MaterialTheme.colorScheme
    return this.background(
        Brush.verticalGradient(listOf(cs.primaryContainer.copy(alpha = 0.55f), cs.background, cs.background)),
    )
}

/** Rounded card that groups a block of settings rows. */
@Composable
fun FoxyCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 1.dp,
    ) {
        Column(Modifier.padding(vertical = 4.dp), content = content)
    }
}
