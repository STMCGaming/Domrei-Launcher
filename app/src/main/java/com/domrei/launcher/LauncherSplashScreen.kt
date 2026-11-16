package com.domrei.launcher

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.domrei.launcher.ui.theme.LauncherColorTokens
import com.domrei.launcher.ui.theme.LauncherTheme

/**
 * Branded splash shown while [LauncherPreloader] reads the saved content and warms the
 * version-list caches, so the launcher UI opens fully populated instead of loading piecemeal.
 */
@Composable
fun LauncherSplashScreen(modifier: Modifier = Modifier) {
    val colors = LauncherTheme.colors
    val transition = rememberInfiniteTransition(label = "splash")

    val emblemScale by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "splashEmblemScale",
    )
    val arcRotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
        ),
        label = "splashArcRotation",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to LauncherColorTokens.Background.SkyStop0,
                    0.55f to LauncherColorTokens.Background.SkyStop1,
                    1f to LauncherColorTokens.Background.SkyStop2,
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .graphicsLayer {
                        scaleX = emblemScale
                        scaleY = emblemScale
                    },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val stroke = 4.dp.toPx()
                    drawCircle(
                        color = colors.accent.copy(alpha = 0.16f),
                        radius = size.minDimension / 2f - stroke / 2f,
                        style = Stroke(width = stroke),
                    )
                    drawArc(
                        color = colors.accent,
                        startAngle = arcRotation,
                        sweepAngle = 110f,
                        useCenter = false,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                        topLeft = Offset(stroke / 2f, stroke / 2f),
                        size = Size(size.width - stroke, size.height - stroke),
                    )
                }
                Text(
                    text = "D",
                    color = colors.accent,
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Black,
                    ),
                )
            }

            Spacer(Modifier.height(26.dp))

            Text(
                text = "DOMREI",
                color = colors.textPrimary,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 9.sp,
                ),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "LAUNCHER",
                color = colors.accent,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 7.sp,
                ),
            )

            Spacer(Modifier.height(30.dp))

            LinearProgressIndicator(
                modifier = Modifier
                    .width(150.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = colors.accent,
                trackColor = colors.accent.copy(alpha = 0.18f),
            )

            Spacer(Modifier.height(14.dp))

            Text(
                text = stringResource(R.string.splash_preparing),
                color = colors.textSubtle,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
            )
        }
    }
}
