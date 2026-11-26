// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.dashboard.home

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.Zoom
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModel
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.ColumnCartesianLayerModel
import com.patrykandpatrick.vico.compose.cartesian.data.LineCartesianLayerModel
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.component.ShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import io.reddevs.tidemark.data.model.Metric
import io.reddevs.tidemark.ui.components.HighlightColumnProvider
import io.reddevs.tidemark.ui.components.IN_PROGRESS_ALPHA

/** Cumulative metrics (steps, calories, distance, sleep) draw columns; the others draw a line. */
internal fun Metric.sparklineAsColumns(): Boolean = this != Metric.RESTING_HEART_RATE && this != Metric.WEIGHT

/**
 * A small axis-free trend drawn with Vico. Purely visual: the tile carries the spoken summary.
 * Draws nothing when [data] has no values.
 */
@Composable
internal fun Sparkline(data: SparklineData, columns: Boolean, modifier: Modifier = Modifier) {
    if (!data.hasData) return
    val color = MaterialTheme.colorScheme.primary
    val lastIndex = (data.values.size - 1).toDouble()
    val present = data.values.withIndex().filter { it.value != null }

    val layer = if (columns) {
        val max = present.maxOf { it.value!! }
        val bar = rememberLineComponent(fill = Fill(color), thickness = 6.dp, shape = RoundedCornerShape(2.dp))
        val today = rememberLineComponent(
            fill = Fill(color.copy(alpha = IN_PROGRESS_ALPHA)),
            thickness = 6.dp,
            shape = RoundedCornerShape(2.dp),
        )
        rememberColumnCartesianLayer(
            // The last bar is today, still adding up, so it is drawn lighter.
            columnProvider = remember(bar, today, lastIndex) { HighlightColumnProvider(bar, today, lastIndex) },
            columnCollectionSpacing = 4.dp,
            rangeProvider = CartesianLayerRangeProvider.fixed(minX = 0.0, maxX = lastIndex, minY = 0.0, maxY = max),
        )
    } else {
        val min = present.minOf { it.value!! }
        val max = present.maxOf { it.value!! }
        // Pad the range so a flat line sits in the middle instead of on an edge.
        val pad = maxOf((max - min) * 0.15, 0.5)
        // A line needs two points, so a single reading is drawn as a dot.
        val dot = if (present.size == 1) {
            LineCartesianLayer.PointProvider.single(
                LineCartesianLayer.Point(ShapeComponent(Fill(color), CircleShape), size = 6.dp),
            )
        } else {
            null
        }
        rememberLineCartesianLayer(
            lineProvider = LineCartesianLayer.LineProvider.series(
                LineCartesianLayer.rememberLine(
                    fill = LineCartesianLayer.LineFill.single(Fill(color)),
                    stroke = LineCartesianLayer.LineStroke.Continuous(thickness = 2.dp),
                    areaFill = null,
                    pointProvider = dot,
                ),
            ),
            rangeProvider = CartesianLayerRangeProvider.fixed(
                minX = 0.0,
                maxX = lastIndex,
                minY = min - pad,
                maxY = max + pad,
            ),
        )
    }

    val model = remember(data, columns) {
        if (columns) {
            CartesianChartModel(
                ColumnCartesianLayerModel.build {
                    series(x = data.values.indices.toList(), y = data.values.map { it ?: 0.0 })
                },
            )
        } else {
            CartesianChartModel(
                LineCartesianLayerModel.build {
                    series(x = present.map { it.index }, y = present.map { it.value!! })
                },
            )
        }
    }

    CartesianChartHost(
        chart = rememberCartesianChart(layer),
        model = model,
        modifier = modifier.fillMaxWidth().height(SparklineHeight),
        scrollState = rememberVicoScrollState(scrollEnabled = false),
        zoomState = rememberVicoZoomState(zoomEnabled = false, initialZoom = Zoom.Content),
    )
}

internal val SparklineHeight = 40.dp
