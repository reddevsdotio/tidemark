// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.dashboard.home

import androidx.health.connect.client.permission.HealthPermission
import io.reddevs.tidemark.data.HealthAccessException
import io.reddevs.tidemark.data.model.ChartRange
import io.reddevs.tidemark.data.model.ChartWindow
import io.reddevs.tidemark.data.model.DataSource
import io.reddevs.tidemark.data.model.HistoryAccess
import io.reddevs.tidemark.data.model.LatestValue
import io.reddevs.tidemark.data.model.Metric
import io.reddevs.tidemark.data.model.RecordType
import io.reddevs.tidemark.data.model.Series
import io.reddevs.tidemark.data.model.SeriesPoint
import io.reddevs.tidemark.data.model.SleepNight
import io.reddevs.tidemark.data.model.SleepStage
import io.reddevs.tidemark.data.model.SleepStageSpan
import io.reddevs.tidemark.testing.FakeHealthRepository
import io.reddevs.tidemark.testing.FakeHealthRepository.Call
import io.reddevs.tidemark.testing.FakeUserPreferences
import io.reddevs.tidemark.testing.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val now = Instant.parse("2026-11-19T10:00:00Z")
    private val today = LocalDate.of(2026, 11, 19)
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val repo = FakeHealthRepository()
    private val preferences = FakeUserPreferences()

    private val night = SleepNight(
        sessionId = "n1",
        start = Instant.parse("2026-11-18T23:00:00Z"),
        end = Instant.parse("2026-11-19T06:30:00Z"),
        stages = emptyList(),
        source = DataSource("com.example.sleep"),
    )

    private fun viewModel() = DashboardViewModel(repo, preferences, clock)

    private fun loaded() = viewModel().also { it.refresh() }

    /** A value for every day of the month window: the day of month, times 100 for cumulative metrics. */
    private fun dailySeries(metric: Metric, window: ChartWindow): Series {
        val month = YearMonth.from(window.anchor)
        val points = (1..month.lengthOfMonth()).map { day ->
            val date = month.atDay(day)
            val value = if (metric.sparklineAsColumns()) day * 100.0 else 70.0 + day / 10.0
            SeriesPoint(date.atStartOfDay(), date.plusDays(1).atStartOfDay(), value)
        }
        return Series(metric, window, points, total = null, average = null, min = null, max = null)
    }

    private fun withAllData() {
        repo.todayTotals[Metric.STEPS] = 8432.0
        repo.todayTotals[Metric.ACTIVE_CALORIES] = 410.0
        repo.todayTotals[Metric.DISTANCE] = 6200.0
        repo.lastNight = night
        repo.latestValues[Metric.RESTING_HEART_RATE] =
            LatestValue(Metric.RESTING_HEART_RATE, now - Duration.ofHours(3), 58.0, DataSource("com.example.watch"))
        repo.latestValues[Metric.WEIGHT] =
            LatestValue(Metric.WEIGHT, now - Duration.ofDays(2), 72.4, DataSource("com.example.scale"))
        repo.seriesProvider = ::dailySeries
    }

    @Test
    fun allDataPresentFillsEveryTile() {
        withAllData()
        val state = loaded().state.value

        assertEquals(today, state.today)
        val steps = state.tile(Metric.STEPS) as TileState.Value
        assertEquals(8432.0, steps.value, 0.0)
        assertEquals(SparklineData(today.minusDays(6), (13..19).map { it * 100.0 }), steps.sparkline)

        assertEquals(410.0, (state.tile(Metric.ACTIVE_CALORIES) as TileState.Value).value, 0.0)
        assertEquals(6200.0, (state.tile(Metric.DISTANCE) as TileState.Value).value, 0.0)

        val sleep = state.tile(Metric.SLEEP) as TileState.Value
        assertEquals(Duration.ofMinutes(450).seconds.toDouble(), sleep.value, 0.0)
        assertEquals(night, sleep.sleep)

        val restingHr = state.tile(Metric.RESTING_HEART_RATE) as TileState.Value
        assertEquals(58.0, restingHr.value, 0.0)
        assertEquals(DataSource("com.example.watch"), restingHr.latest?.source)

        val weight = state.tile(Metric.WEIGHT) as TileState.Value
        assertEquals(72.4, weight.value, 0.0)
        val weightSpark = weight.sparkline!!
        assertEquals(today.minusDays(29), weightSpark.from)
        assertEquals(30, weightSpark.values.size)
        // 21 October to 19 November: the October window and the November window are both read.
        assertEquals(70.0 + 2.1, weightSpark.values.first()!!, 1e-9)
        assertEquals(70.0 + 1.9, weightSpark.values.last()!!, 1e-9)

        assertNull(state.banner)
        assertFalse(state.isRefreshing)
    }

    @Test
    fun sleepTileCountsTimeAsleepNotTimeInBed() {
        withAllData()
        fun span(from: String, to: String, stage: SleepStage) =
            SleepStageSpan(Instant.parse("2026-11-$from:00Z"), Instant.parse("2026-11-$to:00Z"), stage)
        repo.lastNight = night.copy(
            stages = listOf(
                span("18T23:00", "18T23:20", SleepStage.AWAKE_IN_BED),
                span("18T23:20", "19T03:00", SleepStage.LIGHT),
                span("19T03:00", "19T03:10", SleepStage.AWAKE),
                span("19T03:10", "19T06:20", SleepStage.DEEP),
                span("19T06:20", "19T06:30", SleepStage.OUT_OF_BED),
            ),
        )

        val sleep = loaded().state.value.tile(Metric.SLEEP) as TileState.Value

        // 7 h 30 min in bed, minus 20 + 10 + 10 minutes awake or out of bed.
        assertEquals(Duration.ofMinutes(410).seconds.toDouble(), sleep.value, 0.0)
    }

    @Test
    fun noDataShowsNoDataTiles() {
        val state = loaded().state.value

        Metric.entries.forEach { metric ->
            val tile = state.tile(metric)
            assertTrue("$metric is $tile", tile is TileState.NoData)
            assertFalse((tile as TileState.NoData).sparkline?.hasData ?: false)
        }
    }

    @Test
    fun missingPermissionAffectsOnlyItsTile() {
        withAllData()
        repo.granted = repo.granted - RecordType.WEIGHT.readPermission
        val state = loaded().state.value

        assertEquals(TileState.PermissionMissing(RecordType.WEIGHT), state.tile(Metric.WEIGHT))
        (Metric.entries - Metric.WEIGHT).forEach { metric ->
            assertTrue("$metric still loads", state.tile(metric) is TileState.Value)
        }
    }

    @Test
    fun rateLimitedShowsAnErrorOnTheAffectedTilesOnly() {
        withAllData()
        repo.failures[Call.TODAY_TOTAL] = HealthAccessException.RateLimited()
        val state = loaded().state.value

        listOf(Metric.STEPS, Metric.ACTIVE_CALORIES, Metric.DISTANCE).forEach { metric ->
            assertEquals(TileState.Error(ErrorKind.RATE_LIMITED), state.tile(metric))
        }
        listOf(Metric.SLEEP, Metric.RESTING_HEART_RATE, Metric.WEIGHT).forEach { metric ->
            assertTrue(state.tile(metric) is TileState.Value)
        }
    }

    @Test
    fun everyCallFailingNeverBlanksTheScreen() {
        Call.entries.forEach { repo.failures[it] = HealthAccessException.Unavailable() }
        val state = loaded().state.value

        Metric.entries.forEach { metric -> assertEquals(TileState.Error(ErrorKind.UNAVAILABLE), state.tile(metric)) }
        assertNull(state.banner)
    }

    @Test
    fun unexpectedErrorMapsToTheGenericError() {
        repo.failures[Call.LAST_NIGHT] = HealthAccessException.Unexpected(IllegalStateException("boom"))
        assertEquals(TileState.Error(ErrorKind.OTHER), loaded().state.value.tile(Metric.SLEEP))
    }

    @Test
    fun sparklineFailureKeepsTheValue() {
        withAllData()
        repo.failures[Call.SERIES] = HealthAccessException.RateLimited()
        val steps = loaded().state.value.tile(Metric.STEPS) as TileState.Value

        assertEquals(8432.0, steps.value, 0.0)
        assertNull(steps.sparkline)
    }

    @Test
    fun retryReloadsOneTile() {
        withAllData()
        repo.failures[Call.LATEST] = HealthAccessException.RateLimited()
        val viewModel = loaded()
        assertEquals(TileState.Error(ErrorKind.RATE_LIMITED), viewModel.state.value.tile(Metric.WEIGHT))

        repo.failures.clear()
        val todayCalls = repo.calls[Call.TODAY_TOTAL]
        viewModel.retry(Metric.WEIGHT)

        assertTrue(viewModel.state.value.tile(Metric.WEIGHT) is TileState.Value)
        assertTrue(viewModel.state.value.tile(Metric.RESTING_HEART_RATE) is TileState.Error)
        assertEquals(todayCalls, repo.calls[Call.TODAY_TOTAL])
    }

    @Test
    fun loadsOnlyWhenAsked() {
        val viewModel = viewModel()
        assertNull(repo.calls[Call.TODAY_TOTAL])
        assertTrue(viewModel.state.value.tiles.values.all { it == TileState.Loading })

        viewModel.refresh()
        assertEquals(3, repo.calls[Call.TODAY_TOTAL])

        // No polling: time passing triggers nothing.
        mainRule.dispatcher.scheduler.advanceTimeBy(Duration.ofHours(2).toMillis())
        assertEquals(3, repo.calls[Call.TODAY_TOTAL])

        viewModel.refresh()
        assertEquals(6, repo.calls[Call.TODAY_TOTAL])
    }

    @Test
    fun resumeDuringALoadIsIgnoredButPullToRefreshRestarts() {
        val gate = CompletableDeferred<Unit>()
        val gated = object : FakeHealthRepository() {
            override suspend fun todayTotal(metric: Metric, today: LocalDate): Double? {
                gate.await()
                return super.todayTotal(metric, today)
            }
        }
        val viewModel = DashboardViewModel(gated, preferences, clock)

        viewModel.refresh()
        viewModel.refresh()
        assertEquals(1, gated.calls[Call.LAST_NIGHT])
        assertFalse(viewModel.state.value.isRefreshing)

        viewModel.refresh(userInitiated = true)
        assertEquals(2, gated.calls[Call.LAST_NIGHT])
        assertTrue(viewModel.state.value.isRefreshing)

        gate.complete(Unit)
        assertFalse(viewModel.state.value.isRefreshing)
        assertTrue(viewModel.state.value.tile(Metric.STEPS) is TileState.NoData)
    }

    @Test
    fun bannerHiddenWhenHistoryGranted() {
        preferences.firstGrantAt.value = now
        assertNull(loaded().state.value.banner)
    }

    @Test
    fun bannerForDeniedHistoryDatesTheVisibleWindow() {
        repo.granted = repo.granted - HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY
        preferences.firstGrantAt.value = Instant.parse("2026-11-01T08:00:00Z")
        val banner = loaded().state.value.banner!!

        assertEquals(HistoryAccess.NOT_GRANTED, banner.access)
        assertEquals(LocalDate.of(2026, 10, 2), banner.visibleFrom)
        assertTrue(banner.canOpenSettings)
    }

    @Test
    fun bannerForUnavailableHistoryWithoutAFirstGrant() = runTest {
        repo.historyFeatureAvailable = false
        val viewModel = loaded()
        val banner = viewModel.state.value.banner!!

        assertEquals(HistoryAccess.UNAVAILABLE, banner.access)
        assertNull(banner.visibleFrom)
        assertFalse(banner.canOpenSettings)

        // The first-grant time arriving later dates the banner without a reload.
        preferences.recordFirstGrantIfAbsent(Instant.parse("2026-11-10T12:00:00Z"))
        advanceUntilIdle()
        assertEquals(LocalDate.of(2026, 10, 11), viewModel.state.value.banner?.visibleFrom)
    }

    @Test
    fun dismissedBannerStaysHiddenAfterARefresh() {
        repo.historyFeatureAvailable = false
        val viewModel = loaded()
        viewModel.dismissBanner()
        assertNull(viewModel.state.value.banner)

        viewModel.refresh(userInitiated = true)
        assertNull(viewModel.state.value.banner)
        assertEquals(2, repo.calls[Call.HISTORY_ACCESS])
    }

    @Test
    fun bannerHiddenWhenHistoryAccessFails() {
        repo.historyFeatureAvailable = false
        repo.failures[Call.HISTORY_ACCESS] = HealthAccessException.RateLimited()
        assertNull(loaded().state.value.banner)
    }

    @Test
    fun monthWindowsAreRequestedForSparklines() {
        val windows = mutableListOf<Pair<Metric, ChartWindow>>()
        repo.seriesProvider = { metric, window ->
            windows += metric to window
            FakeHealthRepository.emptySeries(metric, window)
        }
        loaded()

        val november = ChartWindow(ChartRange.MONTH, LocalDate.of(2026, 11, 1))
        val october = ChartWindow(ChartRange.MONTH, LocalDate.of(2026, 10, 1))
        assertEquals(listOf(november), windows.filter { it.first == Metric.STEPS }.map { it.second })
        assertEquals(setOf(october, november), windows.filter { it.first == Metric.WEIGHT }.map { it.second }.toSet())
    }
}
