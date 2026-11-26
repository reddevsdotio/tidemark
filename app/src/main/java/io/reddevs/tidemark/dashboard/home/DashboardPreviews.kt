// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.dashboard.home

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import io.reddevs.tidemark.dashboard.detail.HypnogramModel
import io.reddevs.tidemark.data.model.DataSource
import io.reddevs.tidemark.data.model.HistoryAccess
import io.reddevs.tidemark.data.model.LatestValue
import io.reddevs.tidemark.data.model.Metric
import io.reddevs.tidemark.data.model.RecordType
import io.reddevs.tidemark.data.model.SleepNight
import io.reddevs.tidemark.data.model.SleepStage
import io.reddevs.tidemark.data.model.SleepStageSpan
import io.reddevs.tidemark.format.LocaleMeasurementFormatter
import io.reddevs.tidemark.format.MeasurementFormatter
import io.reddevs.tidemark.settings.UnitPreference
import io.reddevs.tidemark.ui.theme.TidemarkTheme
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneOffset

/** Sample dashboards shared by the previews and the Compose tests. */
internal object DashboardSamples {
    val today: LocalDate = LocalDate.of(2026, 11, 19)
    val zone: ZoneOffset = ZoneOffset.UTC

    private fun spark(days: Int, vararg values: Double?) =
        SparklineData(today.minusDays(days - 1L), values.toList().takeLast(days).let { List(days - it.size) { null } + it })

    val lastNight: SleepNight = run {
        val start = today.minusDays(1).atTime(23, 5).toInstant(zone)
        val stages = listOf(
            SleepStage.LIGHT to 40L, SleepStage.DEEP to 70L, SleepStage.LIGHT to 60L, SleepStage.REM to 35L,
            SleepStage.AWAKE to 10L, SleepStage.LIGHT to 80L, SleepStage.DEEP to 40L, SleepStage.REM to 50L,
            SleepStage.LIGHT to 50L,
        )
        var cursor = start
        val spans = stages.map { (stage, minutes) ->
            val end = cursor + Duration.ofMinutes(minutes)
            SleepStageSpan(cursor, end, stage).also { cursor = end }
        }
        SleepNight("night-1", start, cursor, spans, DataSource("com.example.sleep"))
    }

    val allData = DashboardUiState(
        today = today,
        zone = zone,
        tiles = mapOf(
            Metric.STEPS to TileState.Value(8432.0, spark(7, 6100.0, 9800.0, 4300.0, null, 12040.0, 7700.0, 8432.0)),
            Metric.ACTIVE_CALORIES to TileState.Value(412.0, spark(7, 380.0, 520.0, 210.0, 0.0, 640.0, 450.0, 412.0)),
            Metric.DISTANCE to TileState.Value(6240.0, spark(7, 4500.0, 7100.0, 3000.0, null, 9000.0, 5600.0, 6240.0)),
            Metric.SLEEP to TileState.Value(
                HypnogramModel.of(lastNight).asleep.seconds.toDouble(),
                spark(7, 25200.0, 27000.0, 23400.0, 28800.0, 26100.0, 24300.0, 26100.0),
                sleep = lastNight,
            ),
            Metric.RESTING_HEART_RATE to TileState.Value(
                58.0,
                spark(7, 60.0, 59.0, 61.0, 58.0, null, 57.0, 58.0),
                latest = LatestValue(
                    Metric.RESTING_HEART_RATE,
                    today.atTime(7, 12).toInstant(zone),
                    58.0,
                    DataSource("com.example.watch"),
                ),
            ),
            Metric.WEIGHT to TileState.Value(
                72.4,
                spark(30, 73.1, null, 72.9, 72.8, null, 72.6, 72.7, 72.5, 72.4),
                latest = LatestValue(
                    Metric.WEIGHT,
                    today.minusDays(3).atTime(7, 30).toInstant(zone),
                    72.4,
                    DataSource("com.example.scale"),
                ),
            ),
        ),
        banner = HistoryBanner(HistoryAccess.NOT_GRANTED, visibleFrom = LocalDate.of(2026, 10, 20)),
    )

    val mixed = allData.copy(
        tiles = allData.tiles + mapOf(
            Metric.ACTIVE_CALORIES to TileState.NoData(sparkline = null),
            Metric.DISTANCE to TileState.Loading,
            Metric.RESTING_HEART_RATE to TileState.PermissionMissing(RecordType.RESTING_HEART_RATE),
            Metric.WEIGHT to TileState.Error(ErrorKind.RATE_LIMITED),
        ),
        banner = HistoryBanner(HistoryAccess.UNAVAILABLE, visibleFrom = null),
    )
}

@Composable
private fun previewFormatter(): MeasurementFormatter {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    return remember(locale) { LocaleMeasurementFormatter(context, locale, UnitPreference.SYSTEM) }
}

@Composable
private fun DashboardPreview(state: DashboardUiState, dark: Boolean = false) {
    TidemarkTheme(darkTheme = dark, dynamicColor = false) {
        DashboardScreen(
            state = state,
            formatter = previewFormatter(),
            onRefresh = {},
            onOpenMetric = {},
            onRetry = {},
            onOpenHealthConnectSettings = {},
            onDismissBanner = {},
            onOpenExplorer = {},
            onOpenSettings = {},
        )
    }
}

@Preview(name = "Light", widthDp = 360, heightDp = 900)
@Composable
private fun DashboardLightPreview() = DashboardPreview(DashboardSamples.allData)

@Preview(name = "Dark", widthDp = 360, heightDp = 900, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DashboardDarkPreview() = DashboardPreview(DashboardSamples.allData, dark = true)

@Preview(name = "French", locale = "fr-rFR", widthDp = 360, heightDp = 900)
@Composable
private fun DashboardFrenchPreview() = DashboardPreview(DashboardSamples.allData)

@Preview(name = "Font 200%", fontScale = 2f, widthDp = 360, heightDp = 2000)
@Composable
private fun DashboardLargeFontPreview() = DashboardPreview(DashboardSamples.allData)

@Preview(name = "Tile states", widthDp = 360, heightDp = 1000)
@Composable
private fun DashboardMixedPreview() = DashboardPreview(DashboardSamples.mixed)

@Preview(name = "Tile states, French, dark", locale = "fr-rFR", widthDp = 360, heightDp = 1000, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DashboardMixedFrenchDarkPreview() = DashboardPreview(DashboardSamples.mixed, dark = true)
