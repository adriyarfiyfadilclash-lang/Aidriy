package com.aidriy.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aidriy.app.R

/** Brand splash: feather mark, name, tagline and soft blue waves (as in the reference design). */
@Composable
fun SplashScreen() {
    val primary = MaterialTheme.colorScheme.primary
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.primaryContainer)))
            .testTag("screen_splash"),
    ) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(260.dp)
                .align(Alignment.BottomCenter),
        ) {
            fun wave(offset: Float, alpha: Float) {
                val path = Path().apply {
                    moveTo(0f, size.height * offset)
                    cubicTo(size.width * 0.3f, size.height * (offset - 0.25f), size.width * 0.6f, size.height * (offset + 0.2f), size.width, size.height * (offset - 0.1f))
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    close()
                }
                drawPath(path, Brush.verticalGradient(listOf(primary.copy(alpha = alpha), primary.copy(alpha = alpha * 0.4f)), startY = 0f, endY = size.height))
            }
            wave(0.35f, 0.12f)
            wave(0.55f, 0.18f)
            wave(0.75f, 0.25f)
        }
        Column(
            Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_feather),
                contentDescription = null,
                tint = primary,
                modifier = Modifier.size(96.dp),
            )
            Text(
                stringResource(R.string.app_name),
                color = primary,
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.tagline),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
