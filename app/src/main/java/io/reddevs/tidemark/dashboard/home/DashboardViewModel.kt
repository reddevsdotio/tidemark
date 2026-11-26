// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.dashboard.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.reddevs.tidemark.dashboard.detail.HypnogramModel
import io.reddevs.tidemark.data.HealthAccessException
import io.reddevs.tidemark.data.HealthRepository
import io.reddevs.tidemark.data.model.HistoryAccess
import io.reddevs.tidemark.data.model.Metric
import io.reddevs.tidemark.settings.UserPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * Loads the six dashboard tiles and the history banner. It loads when the screen resumes and on
 * pull to refresh, never on a timer, to stay clear of Health Connect's foreground rate limits.
 */
class DashboardViewModel(
    private val repository: HealthRepository,
    userPreferences: UserPreferences,
    private val clock: Clock,
) : ViewModel() {

    private data class Content(val today: LocalDate, val tiles: Map<Metric, TileState>)

    private val content = MutableStateFlow(
        Content(LocalDate.now(clock), Metric.entries.associateWith { TileState.Loading }),
    )
    private val historyAccess = MutableStateFlow<HistoryAccess?>(null)
    private val bannerDismissed = MutableStateFlow(false)
    private val refreshing = MutableStateFlow(false)
    private var loadJob: Job? = null

    val state: StateFlow<DashboardUiState> =
        combine(content, historyAccess, userPreferences.firstGrantAt, bannerDismissed, refreshing) {
                content, access, firstGrant, dismissed, refreshing ->
            DashboardUiState(
                today = content.today,
                zone = clock.zone,
                tiles = content.tiles,
                banner = if (dismissed) null else banner(access, firstGrant),
                isRefreshing = refreshing,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, DashboardUiState(content.value.today, clock.zone))

    /**
     * Reloads every tile and the banner. A resume while a load is running is ignored; a pull to
     * refresh ([userInitiated]) restarts it and shows the refresh indicator.
     */
    fun refresh(userInitiated: Boolean = false) {
        val previous = loadJob
        if (previous?.isActive == true && !userInitiated) return
        loadJob = viewModelScope.launch {
            previous?.cancelAndJoin()
            refreshing.value = userInitiated
            try {
                loadAll()
            } finally {
                refreshing.value = false
            }
        }
    }

    /** Reloads one tile, after an error. */
    fun retry(metric: Metric) {
        setTile(metric, TileState.Loading)
        viewModelScope.launch {
            setTile(metric, loadTile(metric, content.value.today, ZonedDateTime.now(clock)))
        }
    }

    /** Hides the history banner until the app process ends. */
    fun dismissBanner() {
        bannerDismissed.value = true
    }

    private suspend fun loadAll() = coroutineScope {
        val now = ZonedDateTime.now(clock)
        val today = now.toLocalDate()
        content.update { it.copy(today = today) }
        launch { historyAccess.value = catching { repository.historyAccess() } }
        Metric.entries.forEach { metric ->
            launch { setTile(metric, loadTile(metric, today, now)) }
        }
    }

    private suspend fun loadTile(metric: Metric, today: LocalDate, now: ZonedDateTime): TileState = try {
        coroutineScope {
            val sparkline = async { sparkline(metric, today) }
            val value: TileState.Value? = when (metric) {
                Metric.STEPS, Metric.ACTIVE_CALORIES, Metric.DISTANCE ->
                    repository.todayTotal(metric, today)?.let { TileState.Value(it, sparkline = null) }
                Metric.SLEEP -> repository.lastNightSleep(now)?.let { night ->
                    // Time asleep, as in the sleep charts and the day view: awake stages don't count.
                    val seconds = HypnogramModel.of(night).asleep.seconds.toDouble()
                    TileState.Value(seconds, sparkline = null, sleep = night)
                }
                Metric.RESTING_HEART_RATE, Metric.WEIGHT ->
                    repository.latest(metric)?.let { TileState.Value(it.value, sparkline = null, latest = it) }
            }
            value?.copy(sparkline = sparkline.await()) ?: TileState.NoData(sparkline.await())
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: HealthAccessException) {
        e.toTileState()
    } catch (@Suppress("TooGenericExceptionCaught") e: RuntimeException) {
        // The repository contract allows only HealthAccessException; never crash the screen if it slips.
        TileState.Error(ErrorKind.OTHER)
    }

    /** The tile's trend line. A failure here hides the sparkline but keeps the tile's value. */
    private suspend fun sparkline(metric: Metric, today: LocalDate): SparklineData? = catching {
        val from = SparklineWindows.firstDay(metric, today)
        val points = coroutineScope {
            SparklineWindows.windows(from, today)
                .map { window -> async { repository.series(metric, window).points } }
                .awaitAll()
                .flatten()
        }
        SparklineWindows.densify(points, from, today)
    }

    private fun setTile(metric: Metric, state: TileState) {
        content.update { it.copy(tiles = it.tiles + (metric to state)) }
    }

    private fun banner(access: HistoryAccess?, firstGrant: Instant?): HistoryBanner? = when (access) {
        HistoryAccess.NOT_GRANTED, HistoryAccess.UNAVAILABLE -> HistoryBanner(
            access = access,
            visibleFrom = firstGrant?.atZone(clock.zone)?.toLocalDate()?.minusDays(HISTORY_WINDOW_DAYS),
        )
        HistoryAccess.GRANTED, null -> null
    }

    private suspend fun <T> catching(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: HealthAccessException) {
        null
    } catch (@Suppress("TooGenericExceptionCaught") e: RuntimeException) {
        null
    }

    companion object {
        /** Without the history permission Health Connect returns data from 30 days before the first grant. */
        const val HISTORY_WINDOW_DAYS = 30L
    }
}
