package com.familyguard.app.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * Эмблема FamilyGuard: ребёнок (точка в центре) внутри щита, родитель — точка
 * на контуре. С [animated] точка бежит по контуру и «рисует» щит, на следующем
 * круге стирает его — используется вместо индикатора загрузки.
 * [cut] — цвет фона под знаком, им рисуется зазор вокруг точки-родителя.
 */
@Composable
fun FgLogo(
    modifier: Modifier = Modifier,
    animated: Boolean = false,
    color: Color = MaterialTheme.colorScheme.primary,
    cut: Color = MaterialTheme.colorScheme.background,
) {
    val progress = if (animated) {
        rememberInfiniteTransition(label = "logo").animateFloat(
            initialValue = 0f,
            targetValue = 2f,
            animationSpec = infiniteRepeatable(tween(3600, easing = LinearEasing)),
            label = "lap",
        ).value
    } else 0f

    Canvas(modifier) {
        val s = size.minDimension / 64f
        val shield = android.graphics.Path().apply {
            moveTo(32f, 8f)
            lineTo(51f, 15f)
            lineTo(51f, 31f)
            cubicTo(51f, 44f, 43f, 52f, 32f, 57f)
            cubicTo(21f, 52f, 13f, 44f, 13f, 31f)
            lineTo(13f, 15f)
            close()
            transform(android.graphics.Matrix().apply { setScale(s, s) })
        }
        val stroke = Stroke(width = 5f * s, join = StrokeJoin.Round)
        val parent: Offset
        if (animated) {
            val pm = android.graphics.PathMeasure(shield, true)
            val len = pm.length
            val drawn = android.graphics.Path()
            // Первый круг рисует контур, второй — стирает его
            if (progress < 1f) pm.getSegment(0f, progress * len, drawn, true)
            else pm.getSegment((progress - 1f) * len, len, drawn, true)
            drawPath(drawn.asComposePath(), color, style = stroke)
            val pos = FloatArray(2)
            pm.getPosTan((progress % 1f) * len, pos, null)
            parent = Offset(pos[0], pos[1])
        } else {
            drawPath(shield.asComposePath(), color, style = stroke)
            parent = Offset(51f * s, 15f * s)
        }
        drawCircle(color, 9f * s, Offset(32f * s, 32f * s))
        drawCircle(cut, 7.75f * s, parent)
        drawCircle(color, 6f * s, parent)
    }
}
