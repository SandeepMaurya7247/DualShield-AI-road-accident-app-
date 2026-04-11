package com.team404.dualshield.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.team404.dualshield.ui.theme.*

/**
 * A tactical grid background that provides a futuristic "Mission Control" look.
 */
@Composable
fun TacticalGrid(
    modifier: Modifier = Modifier,
    gridSize: Dp = 40.dp,
    gridColor: Color? = null
) {
    val actualGridColor = gridColor ?: MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f)
    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val pxSize = gridSize.toPx()

        // Vertical lines
        var x = 0f
        while (x < width) {
            drawLine(
                color = actualGridColor,
                start = Offset(x, 0f),
                end = Offset(x, height),
                strokeWidth = 1f
            )
            x += pxSize
        }

        // Horizontal lines
        var y = 0f
        while (y < height) {
            drawLine(
                color = actualGridColor,
                start = Offset(0f, y),
                end = Offset(width, y),
                strokeWidth = 1f
            )
            y += pxSize
        }
    }
}

/**
 * A premium glassmorphic card with tactical borders.
 */
@Composable
fun SentinelCard(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(24.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp, 
            Brush.linearGradient(
                listOf(MaterialTheme.colorScheme.outline, Color.Transparent, MaterialTheme.colorScheme.outline)
            )
        )
    ) {
        content()
    }
}

/**
 * Advanced Rotating Pulse Ring for the Sentinel Hub.
 */
@Composable
fun RotatingStatusRing(
    modifier: Modifier = Modifier,
    baseColor: Color = sentinelGreen,
    layerCount: Int = 3
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ring")
    
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(10000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(modifier = modifier, contentAlignment = androidx.compose.ui.Alignment.Center) {
        for (i in 0 until layerCount) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val radius = (size.minDimension / 2) * (1f - (i * 0.15f))
                val animatedRadius = radius * if (i == 0) pulse else 1f
                
                // Draw partial arc for tactical look
                drawArc(
                    color = baseColor.copy(alpha = 0.4f / (i + 1)),
                    startAngle = rotation * (if (i % 2 == 0) 1f else -1.5f),
                    sweepAngle = 120f,
                    useCenter = false,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 4f, 
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                    ),
                    topLeft = Offset(center.x - animatedRadius, center.y - animatedRadius),
                    size = androidx.compose.ui.geometry.Size(animatedRadius * 2, animatedRadius * 2)
                )
            }
        }
    }
}
