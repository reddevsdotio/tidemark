// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.dashboard.detail

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.Zoom
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModel
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.CandlestickCartesianLayerModel
import com.patrykandpatrick.vico.compose.cartesian.data.ColumnCartesianLayerModel
import com.patrykandpatrick.vico.compose.cartesian.data.LineCartesianLayerModel
import com.patrykandpatrick.vico.compose.cartesian.decoration.HorizontalLine
import com.patrykandpatrick.vico.compose.cartesian.layer.CandlestickCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.CartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.absolute
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberCandlestickCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarkerController
import com.patrykandpatrick.vico.compose.cartesian.marker.Interaction
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.Insets
import com.patrykandpatrick.vico.compose.common.ProvideVicoTheme
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.common.data.ExtraStore
import com.patrykandpatrick.vico.compose.m3.common.rememberM3VicoTheme
import io.reddevs.tidemark.ui.components.HighlightColumnProvider
import io.reddevs.tidemark.ui.components.IN_PROGRESS_ALPHA
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

/** One plotted point in display units. [x] is the bucket index; [min] and [max] draw the range band. */
internal data class ChartPoint(val x: Int, val y: Double, val min: Double? = null, val max: Double? = null)

/** Everything a detail chart needs, already converted to display units. */
internal data class ChartSpec(
    val points: List<ChartPoint>,
    /** Number of x slots in the window (24 hours, 7 days, ...). */
    val slots: Int,
    val xLabel: (Int) -> String,
    val labelSpacing: Int,
    val yLabel: (Double) -> String,
    val referenceY: Double? = null,
    val referenceLabel: String? = null,
    /** The x slot of the period still in progress (today, this week), drawn lighter. */
    val inProgressX: Int? = null,
)

private val ChartHeight = 220.dp

/** Bars for cumulative metrics and sleep. Tapping a bar reports its x slot. */
@Composable
internal fun DetailBarChart(spec: ChartSpec, description: String, onTap: (Int) -> Unit, modifier: Modifier = Modifier) {
    ChartTheme {
        val range = remember(spec) { barRange(spec) }
        val primary = MaterialTheme.colorScheme.primary
        val thickness = barThickness(spec.slots)
        val shape = RoundedCornerShape(topStart = barRadius(thickness), topEnd = barRadius(thickness))
        val column = rememberLineComponent(fill = Fill(primary), thickness = thickness, shape = shape)
        val inProgress = rememberLineComponent(
            fill = Fill(primary.copy(alpha = IN_PROGRESS_ALPHA)),
            thickness = thickness,
            shape = shape,
        )
        val highlightX = spec.inProgressX?.toDouble()
        val layer = rememberColumnCartesianLayer(
            columnProvider = remember(column, inProgress, highlightX) {
                HighlightColumnProvider(column, inProgress, highlightX)
            },
            columnCollectionSpacing = 4.dp,
            rangeProvider = range,
        )
        val model = remember(spec) {
            CartesianChartModel(
                ColumnCartesianLayerModel.build { series(x = spec.points.map { it.x }, y = spec.points.map { it.y }) },
            )
        }
        DetailChartHost(spec, range, listOf(layer), model, description, onTap, modifier)
    }
}

/**
 * A line for resting heart rate and weight, with a min-to-max band drawn as floating candles when
 * buckets have a range, and an optional dashed reference line.
 */
@Composable
internal fun DetailLineChart(
    spec: ChartSpec,
    description: String,
    onTap: (Int) -> Unit,
    modifier: Modifier = Modifier,
    showPoints: Boolean = true,
) {
    ChartTheme {
        val colors = MaterialTheme.colorScheme
        val withBand = spec.points.filter { it.min != null && it.max != null && it.max > it.min }
        val range = remember(spec) { lineRange(spec) }
        val point = rememberShapeComponent(fill = Fill(colors.primary), shape = CircleShape)
        val line = LineCartesianLayer.rememberLine(
            fill = LineCartesianLayer.LineFill.single(Fill(colors.primary)),
            areaFill = null,
            pointProvider = if (showPoints) {
                LineCartesianLayer.PointProvider.single(LineCartesianLayer.Point(point, 6.dp))
            } else {
                null
            },
        )
        val lineLayer = rememberLineCartesianLayer(
            lineProvider = LineCartesianLayer.LineProvider.series(line),
            rangeProvider = range,
        )
        val bandBody = rememberLineComponent(fill = Fill(colors.primary.copy(alpha = 0.2f)), thickness = barThickness(spec.slots))
        val noWick = rememberLineComponent(fill = Fill.Transparent, thickness = 0.dp)
        val candle = remember(bandBody, noWick) { CandlestickCartesianLayer.Candle(bandBody, noWick, noWick) }
        val bandLayer = rememberCandlestickCartesianLayer(
            candleProvider = CandlestickCartesianLayer.CandleProvider.absolute(candle, candle, candle),
            rangeProvider = range,
        )
        val layers: List<CartesianLayer<*>> = if (withBand.isEmpty()) listOf(lineLayer) else listOf(bandLayer, lineLayer)
        val model = remember(spec) {
            val lineModel = LineCartesianLayerModel.build { series(x = spec.points.map { it.x }, y = spec.points.map { it.y }) }
            val models = if (withBand.isEmpty()) {
                listOf(lineModel)
            } else {
                listOf(
                    CandlestickCartesianLayerModel.build(
                        x = withBand.map { it.x },
                        opening = withBand.map { it.min!! },
                        closing = withBand.map { it.max!! },
                        low = withBand.map { it.min!! },
                        high = withBand.map { it.max!! },
                    ),
                    lineModel,
                )
            }
            CartesianChartModel(models)
        }
        DetailChartHost(spec, range, layers, model, description, onTap, modifier)
    }
}

@Composable
private fun DetailChartHost(
    spec: ChartSpec,
    range: FixedRange,
    layers: List<CartesianLayer<*>>,
    model: CartesianChartModel,
    description: String,
    onTap: (Int) -> Unit,
    modifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val currentOnTap = rememberUpdatedState(onTap)
    val controller = remember { TapController { x -> currentOnTap.value(x.roundToInt()) } }
    val axisLabel = rememberTextComponent(style = MaterialTheme.typography.labelSmall.copy(color = colors.onSurfaceVariant))
    val axisLine = rememberLineComponent(fill = Fill(colors.outlineVariant), thickness = 1.dp)
    val gridLine = rememberLineComponent(fill = Fill(colors.outlineVariant), thickness = 1.dp)
    val referenceLine = rememberLineComponent(fill = Fill(colors.tertiary), thickness = 2.dp)
    // The label sits on the screen colour, so a heart rate trace passing behind it can't cross the text.
    val referenceText = rememberTextComponent(
        style = MaterialTheme.typography.labelSmall.copy(color = colors.tertiary),
        padding = Insets(horizontal = 4.dp, vertical = 1.dp),
        background = rememberShapeComponent(fill = Fill(colors.background), shape = RoundedCornerShape(4.dp)),
    )
    val decorations = remember(spec, referenceLine, referenceText) {
        val y = spec.referenceY ?: return@remember emptyList()
        listOf(
            HorizontalLine(
                y = { y },
                line = referenceLine,
                labelComponent = referenceText,
                label = { spec.referenceLabel.orEmpty() },
            ),
        )
    }
    val chart = rememberCartesianChart(
        *layers.toTypedArray(),
        startAxis = VerticalAxis.rememberStart(
            line = null,
            label = axisLabel,
            tick = null,
            guideline = gridLine,
            valueFormatter = remember(spec) { CartesianValueFormatter { _, value, _ -> spec.yLabel(value) } },
            itemPlacer = remember(range) { VerticalAxis.ItemPlacer.step(step = { range.step }) },
        ),
        bottomAxis = HorizontalAxis.rememberBottom(
            line = axisLine,
            label = axisLabel,
            tick = axisLine,
            valueFormatter = remember(spec) { CartesianValueFormatter { _, value, _ -> spec.xLabel(value.roundToInt()) } },
            itemPlacer = remember(spec.labelSpacing) { HorizontalAxis.ItemPlacer.aligned(spacing = { spec.labelSpacing }) },
            guideline = null,
        ),
        // The marker is never shown: taps go to [TapController], which drills down instead.
        marker = rememberDefaultCartesianMarker(label = rememberTextComponent()),
        markerController = controller,
        decorations = decorations,
    )
    CartesianChartHost(
        chart = chart,
        model = model,
        modifier = modifier
            .fillMaxWidth()
            .height(ChartHeight)
            .clearAndSetSemantics { contentDescription = description },
        scrollState = rememberVicoScrollState(scrollEnabled = false),
        zoomState = rememberVicoZoomState(zoomEnabled = false, initialZoom = Zoom.Content),
    )
}

@Composable
private fun ChartTheme(content: @Composable () -> Unit) {
    ProvideVicoTheme(rememberM3VicoTheme(), content)
}

/**
 * Reports the x value of a tapped bucket and keeps the marker hidden. Vico re-runs the last
 * accepted interaction when the viewport changes, so each tap is handled only once.
 */
private class TapController(private val onTap: (Double) -> Unit) : CartesianMarkerController {
    private var handled: Interaction? = null

    override fun shouldShowMarker(interaction: Interaction, targets: List<CartesianMarker.Target>): Boolean {
        if (interaction is Interaction.Tap && interaction !== handled && targets.isNotEmpty()) {
            handled = interaction
            onTap(targets.first().x)
        }
        return false
    }
}

/** Keeps the x axis on the whole window and the y axis on the given bounds. */
private class FixedRange(
    slots: Int,
    private val minY: Double,
    private val maxY: Double,
    val step: Double,
) : CartesianLayerRangeProvider {
    private val maxX = (slots - 1).coerceAtLeast(0).toDouble()

    override fun getMinX(minX: Double, maxX: Double, extraStore: ExtraStore) = 0.0

    override fun getMaxX(minX: Double, maxX: Double, extraStore: ExtraStore) = this.maxX

    override fun getMinY(minY: Double, maxY: Double, extraStore: ExtraStore) = this.minY

    override fun getMaxY(minY: Double, maxY: Double, extraStore: ExtraStore) = this.maxY
}

/** Y bounds around the data, the band and the reference line, so weight doesn't start at zero. */
private fun lineRange(spec: ChartSpec): FixedRange {
    val values = spec.points.flatMap { listOfNotNull(it.y, it.min, it.max) } + listOfNotNull(spec.referenceY)
    val low = values.min()
    val high = values.max()
    val pad = max((high - low) * 0.15, max(high.absoluteValue() * 0.02, 1.0))
    val step = niceStep((high - low + 2 * pad) / 4)
    return FixedRange(spec.slots, floor((low - pad) / step) * step, ceil((high + pad) / step) * step, step)
}

/** Bars start at zero and end on the first round step above the highest bar. */
private fun barRange(spec: ChartSpec): FixedRange {
    val high = spec.points.maxOf { it.y }
    val step = niceStep(high / 4)
    return FixedRange(spec.slots, 0.0, (ceil(high / step) * step).coerceAtLeast(step), step)
}

private fun Double.absoluteValue() = if (this < 0) -this else this

/** Rounds up to 1, 2, 2.5 or 5 times a power of ten, so the top axis label is a round number. */
internal fun niceCeil(value: Double): Double {
    if (value <= 0.0) return 1.0
    val magnitude = 10.0.pow(floor(log10(value)))
    val scaled = value / magnitude
    val nice = listOf(1.0, 2.0, 2.5, 5.0, 10.0).first { it >= scaled - 1e-9 }
    return nice * magnitude
}

private fun niceStep(raw: Double): Double = if (raw <= 0.0) 1.0 else niceCeil(raw)

/** Rounded bar tops: about a third of the bar width, at most 6 dp. */
private fun barRadius(thickness: Dp): Dp = minOf(thickness / 3, 6.dp)

private fun barThickness(slots: Int): Dp = when {
    slots <= 7 -> 20.dp
    slots <= 12 -> 14.dp
    slots <= 24 -> 8.dp
    else -> 6.dp
}
