package ir.pingscore.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import ir.pingscore.app.data.TestEntity

/** نمودار ساده‌ی نمره‌ها؛ قدیمی‌ترین نقطه سمت چپ و جدیدترین سمت راست. */
@Composable
fun ScoreChart(tests: List<TestEntity>, modifier: Modifier = Modifier) {
    val data = tests.sortedBy { it.timeMillis }.takeLast(30)
    val grid = MaterialTheme.colorScheme.outlineVariant
    val cOverall = MaterialTheme.colorScheme.primary
    val cAi = Color(0xFF7C3AED)
    val cVideo = Color(0xFFEA580C)
    val cChat = Color(0xFF2563EB)

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (data.size < 2) {
            Text("برای رسم نمودار حداقل دو تست لازم است.", style = MaterialTheme.typography.bodySmall)
            return@Column
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
        ) {
            val w = size.width
            val h = size.height
            listOf(0, 50, 75, 100).forEach { v ->
                val y = h - h * v / 100f
                drawLine(grid, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
            }
            fun line(color: Color, value: (TestEntity) -> Int, width: Float) {
                val path = Path()
                data.forEachIndexed { i, t ->
                    val x = w * i / (data.size - 1)
                    val y = h - h * value(t) / 100f
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, color, style = Stroke(width = width))
            }
            line(cAi, { it.aiScore }, 3f)
            line(cVideo, { it.videoScore }, 3f)
            line(cChat, { it.chatScore }, 3f)
            line(cOverall, { it.overall }, 6f)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Legend(cOverall, "کل")
            Legend(cAi, "هوش مصنوعی")
            Legend(cVideo, "ویدیو")
            Legend(cChat, "چت")
        }
    }
}

@Composable
private fun Legend(color: Color, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(modifier = Modifier.size(10.dp)) {
            drawCircle(color, radius = size.minDimension / 2f)
        }
        Text(text, style = MaterialTheme.typography.labelSmall)
    }
}
