package com.aradrotem.spendwise.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.aradrotem.spendwise.ui.format.formatAmountInCents
import com.aradrotem.spendwise.ui.format.formatMonthShort
import java.time.YearMonth

// One donut chart segment. amountCents/percentage are display-only here - the exact monetary
// source of truth lives in the caller's CategorySpendingPoint list; this type only carries what
// the Canvas drawing needs, kept separate from the domain model so this file has no data-layer
// import beyond simple primitives.
data class DonutSegment(val label: String, val amountCents: Long, val percentage: Float, val color: Color)

// Custom Canvas drawing: the only shape here (a ring of arcs) genuinely needs drawArc - there is
// no equivalent plain-layout composable for a pie/donut, unlike the bar charts below.
@Composable
fun DonutChart(segments: List<DonutSegment>, modifier: Modifier = Modifier) {
    val totalDescription = segments.joinToString("; ") { "${it.label}: ${formatAmountInCents(it.amountCents)}, ${it.percentage.toInt()} percent" }
    Canvas(
        modifier = modifier
            .size(180.dp)
            .semantics { contentDescription = if (segments.isEmpty()) "No expense data" else totalDescription }
    ) {
        if (segments.isEmpty()) {
            // Neutral empty-state ring rather than blank space, so the chart's presence is never
            // ambiguous with a loading/broken state.
            drawArc(
                color = Color.LightGray,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = size.minDimension * 0.2f)
            )
            return@Canvas
        }

        val strokeWidth = size.minDimension * 0.28f
        val diameter = size.minDimension - strokeWidth
        val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
        var startAngle = -90f
        segments.forEach { segment ->
            val sweep = (360f * (segment.percentage / 100f)).coerceIn(0f, 360f)
            if (sweep > 0f) {
                drawArc(
                    color = segment.color,
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = Size(diameter, diameter),
                    style = Stroke(width = strokeWidth)
                )
            }
            startAngle += sweep
        }
    }
}

// Grouped bar chart for income vs expenses across months. Built from plain Compose layout
// (Box with a proportional fillMaxHeight fraction) rather than Canvas.drawRect - a proportional
// box already IS a bar, so this stays simpler and just as maintainable without custom drawing.
@Composable
fun IncomeExpenseBarChart(
    months: List<YearMonth>,
    incomeCents: List<Long>,
    expenseCents: List<Long>,
    modifier: Modifier = Modifier
) {
    val maxValue = (incomeCents + expenseCents).maxOrNull()?.coerceAtLeast(1L) ?: 1L

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(160.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom
        ) {
            months.indices.forEach { index ->
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    ChartBar(
                        fraction = incomeCents[index].toFloat() / maxValue.toFloat(),
                        color = MaterialTheme.colorScheme.primary,
                        contentDescription = "${formatMonthShort(months[index])} income ${formatAmountInCents(incomeCents[index])}"
                    )
                    ChartBar(
                        fraction = expenseCents[index].toFloat() / maxValue.toFloat(),
                        color = MaterialTheme.colorScheme.error,
                        contentDescription = "${formatMonthShort(months[index])} expenses ${formatAmountInCents(expenseCents[index])}"
                    )
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            months.forEach { month -> Text(formatMonthShort(month), style = MaterialTheme.typography.labelSmall) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LegendDot(color = MaterialTheme.colorScheme.primary, label = "Income")
            LegendDot(color = MaterialTheme.colorScheme.error, label = "Expenses")
        }
    }
}

// Single-series bar chart, reused for both the monthly spending trend and a selected category's
// trend - both are exactly "one value per month".
@Composable
fun SingleSeriesBarChart(
    months: List<YearMonth>,
    valuesCents: List<Long>,
    barColor: Color,
    modifier: Modifier = Modifier
) {
    val maxValue = valuesCents.maxOrNull()?.coerceAtLeast(1L) ?: 1L

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(140.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom
        ) {
            months.indices.forEach { index ->
                ChartBar(
                    fraction = valuesCents[index].toFloat() / maxValue.toFloat(),
                    color = barColor,
                    contentDescription = "${formatMonthShort(months[index])}: ${formatAmountInCents(valuesCents[index])}",
                    width = 20.dp
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            months.forEach { month -> Text(formatMonthShort(month), style = MaterialTheme.typography.labelSmall) }
        }
    }
}

// A single bar: a fixed-width Box whose height is a fraction of its parent's height, floored to a
// thin visible sliver so a genuine zero value is still represented as "present, but empty" rather
// than invisible. Carries its own content description so the exact value is always available to
// accessibility services even though the bar itself is only a colored rectangle.
@Composable
private fun ChartBar(fraction: Float, color: Color, contentDescription: String, width: androidx.compose.ui.unit.Dp = 14.dp) {
    val clamped = fraction.coerceIn(0f, 1f)
    Box(
        modifier = Modifier
            .width(width)
            .fillMaxHeight(fraction = clamped)
            .heightIn(min = 2.dp)
            .background(color, shape = RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
            .semantics { this.contentDescription = contentDescription }
    )
}

@Composable
private fun LegendDot(color: Color, label: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(modifier = Modifier.size(10.dp).background(color, shape = RoundedCornerShape(2.dp)))
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}
