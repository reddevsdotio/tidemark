// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.store

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.GradientDrawable
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.core.content.ContextCompat
import androidx.health.connect.client.records.ExerciseLap
import androidx.health.connect.client.records.ExerciseSegment
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.units.Length
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.reddevs.tidemark.R
import io.reddevs.tidemark.dashboard.detail.DetailActions
import io.reddevs.tidemark.dashboard.detail.DetailContent
import io.reddevs.tidemark.dashboard.detail.DetailSamples
import io.reddevs.tidemark.dashboard.detail.MetricDetailScreen
import io.reddevs.tidemark.dashboard.detail.MetricDetailUiState
import io.reddevs.tidemark.dashboard.home.DashboardSamples
import io.reddevs.tidemark.dashboard.home.DashboardScreen
import io.reddevs.tidemark.data.model.ChartRange
import io.reddevs.tidemark.data.model.DataSource
import io.reddevs.tidemark.data.model.Metric
import io.reddevs.tidemark.data.model.RecordType
import io.reddevs.tidemark.explorer.ExplorerRange
import io.reddevs.tidemark.explorer.RecordDetailScreen
import io.reddevs.tidemark.explorer.RecordDetailState
import io.reddevs.tidemark.explorer.RecordFieldExtractor
import io.reddevs.tidemark.explorer.RecordListScreen
import io.reddevs.tidemark.explorer.RecordListState
import io.reddevs.tidemark.format.LocaleMeasurementFormatter
import io.reddevs.tidemark.settings.UnitPreference
import io.reddevs.tidemark.testing.TestRecords
import io.reddevs.tidemark.ui.components.AppLabels
import io.reddevs.tidemark.ui.theme.TidemarkTheme
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale

/**
 * Renders the raw screens for the store screenshots, plus the store icon and the wave mark, with
 * the static Shoreline theme and sample data. Skipped unless TIDEMARK_STORE_OUT names an output
 * folder; scripts/build-store-graphics.py runs it and frames the results.
 *
 * The window is 411 x 823 dp at 420 dpi, about 1080 x 2160 px. Each subclass sets the locale
 * through its qualifiers, since a compose rule takes one setContent per test.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class StoreScreenshotTest(private val name: String, private val locale: Locale) {
    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val out: File? = System.getenv("TIDEMARK_STORE_OUT")?.takeIf { it.isNotBlank() }?.let(::File)
    private val zone = ZoneOffset.UTC
    private val clock = Clock.fixed(Instant.parse("2026-11-19T18:00:00Z"), zone)

    // Generic source apps: a label and a plain coloured icon, never a real product.
    private class SourceApp(val en: String, val fr: String, val color: Int)

    private val sourceApps = mapOf(
        "com.example.watch" to SourceApp("Fitness watch", "Montre sport", 0xFF2E7A82.toInt()),
        "com.example.scale" to SourceApp("Smart scale", "Balance connectée", 0xFF8A6A35.toInt()),
        "com.example.sleep" to SourceApp("Sleep tracker", "Suivi du sommeil", 0xFF123F45.toInt()),
        "com.example.run" to SourceApp("Running app", "Appli de course", 0xFFC8553D.toInt()),
    )

    private val dir: File get() = File(out, "screens/$name").apply { mkdirs() }
    private val formatter by lazy { LocaleMeasurementFormatter(context, locale, UnitPreference.METRIC, clock) }
    private val appLabels by lazy { AppLabels(context.packageManager) }
    private val dashboardState = DashboardSamples.allData.copy(banner = null)

    @Before
    fun setUp() {
        assumeTrue("set TIDEMARK_STORE_OUT to render store graphics", out != null)
        Locale.setDefault(locale)
        val packageManager = shadowOf(context.packageManager)
        sourceApps.forEach { (packageName, app) ->
            packageManager.installPackage(
                PackageInfo().apply {
                    this.packageName = packageName
                    applicationInfo = ApplicationInfo().apply {
                        this.packageName = packageName
                        nonLocalizedLabel = if (locale.language == "fr") app.fr else app.en
                    }
                },
            )
            packageManager.setApplicationIcon(
                packageName,
                GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(app.color)
                },
            )
        }
    }

    @Test
    fun dashboard() = capture("1-dashboard.png") { Dashboard() }

    @Test
    fun sleepDay() = capture("2-sleep-day.png") {
        // A plain night: the shared sample also covers rare stages and gaps for the tests.
        Detail(DetailSamples.state(Metric.SLEEP, ChartRange.DAY).copy(content = DetailContent.SleepDay(DashboardSamples.lastNight)))
    }

    @Test
    fun stepsMonth() = capture("3-steps-month.png") { Detail(DetailSamples.state(Metric.STEPS, ChartRange.MONTH)) }

    @Test
    fun restingHeartRateMonth() = capture("4-resting-heart-rate-month.png") {
        Detail(realisticRestingHeartRate())
    }

    @Test
    fun exerciseList() = capture("5-exercise-list.png") {
        RecordListScreen(
            state = RecordListState(
                type = RecordType.EXERCISE_SESSION,
                range = ExplorerRange.Last30Days,
                selectedSource = null,
                sources = setOf(DataSource("com.example.run"), DataSource("com.example.watch")),
                records = exerciseSessions(),
                loadingFirstPage = false,
                endReached = true,
            ),
            formatter = formatter,
            appLabels = appLabels,
            clock = clock,
            onSelectRange = {},
            onSelectSource = {},
            onLastVisibleItem = {},
            onRetry = {},
            onOpenSettings = {},
            onOpenRecord = {},
            onBack = {},
        )
    }

    @Test
    fun exerciseDetail() {
        val run = exerciseSessions().first()
        val sections = RecordFieldExtractor(context, formatter, appLabels::label).extract(run)
        capture("6-exercise-detail.png") {
            RecordDetailScreen(
                type = RecordType.EXERCISE_SESSION,
                state = RecordDetailState.Loaded(run),
                sections = sections,
                onRetry = {},
                onOpenSettings = {},
                onBack = {},
            )
        }
    }

    @Test
    fun dashboardDark() = capture("7-dashboard-dark.png", dark = true) { Dashboard() }

    @Composable
    private fun Dashboard() = DashboardScreen(
        state = dashboardState,
        formatter = formatter,
        onRefresh = {},
        onOpenMetric = {},
        onRetry = {},
        onOpenHealthConnectSettings = {},
        onDismissBanner = {},
        onOpenExplorer = {},
        onOpenSettings = {},
    )

    /** A believable month of resting heart rate, in place of the sample's sine wave. */
    private fun realisticRestingHeartRate(): MetricDetailUiState {
        val state = DetailSamples.state(Metric.RESTING_HEART_RATE, ChartRange.MONTH)
        val buckets = state.content as DetailContent.Buckets
        val days = listOf(59, 60, 58, 58, 57, 59, 61, 60, 58, 57, 57, 56, 58, 59, 58, 57, 56, 57, 58)
        val points = buckets.series.points.mapIndexed { i, point ->
            val bpm = days.getOrNull(i)?.toDouble()
            point.copy(value = bpm, min = bpm?.minus(2 + i % 3), max = bpm?.plus(3 + (i * 7) % 5))
        }
        val values = points.mapNotNull { it.value }
        val series = buckets.series.copy(
            points = points,
            total = values.sum(),
            average = values.average(),
            min = points.mapNotNull { it.min }.minOrNull(),
            max = points.mapNotNull { it.max }.maxOrNull(),
        )
        return state.copy(content = buckets.copy(series = series))
    }

    @Composable
    private fun Detail(state: MetricDetailUiState) = MetricDetailScreen(
        state,
        formatter,
        zone,
        DetailActions(),
        appLabels::label,
    )

    /** The store icon and the bare waves, rendered once from the English run. */
    protected fun renderIcons() {
        val iconDir = File(out, "icons").apply { mkdirs() }
        val background = ContextCompat.getColor(context, R.color.ic_launcher_background)
        val foreground = ContextCompat.getDrawable(context, R.drawable.ic_launcher_foreground)!!
        // Play wants the full 108 dp adaptive icon, background included, and applies its own mask.
        save(
            Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888).also { bitmap ->
                val canvas = Canvas(bitmap)
                canvas.drawColor(background)
                foreground.setBounds(0, 0, 512, 512)
                foreground.draw(canvas)
            },
            File(iconDir, "icon-512.png"),
        )
        // The waves alone on transparent, for the feature graphic.
        save(
            Bitmap.createBitmap(1080, 1080, Bitmap.Config.ARGB_8888).also { bitmap ->
                foreground.setBounds(0, 0, 1080, 1080)
                foreground.draw(Canvas(bitmap))
            },
            File(iconDir, "waves-1080.png"),
        )
    }

    /** Two weeks of runs and rides, newest first, as the list shows them. */
    private fun exerciseSessions(): List<ExerciseSessionRecord> {
        val french = locale.language == "fr"
        val plan = listOf(
            Triple(0L, ExerciseSessionRecord.EXERCISE_TYPE_RUNNING, if (french) "Footing du matin" else "Morning run"),
            Triple(1L, ExerciseSessionRecord.EXERCISE_TYPE_WALKING, null),
            Triple(2L, ExerciseSessionRecord.EXERCISE_TYPE_BIKING, if (french) "Trajet à vélo" else "Bike commute"),
            Triple(3L, ExerciseSessionRecord.EXERCISE_TYPE_RUNNING, if (french) "Fractionné" else "Intervals"),
            Triple(5L, ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL, null),
            Triple(6L, ExerciseSessionRecord.EXERCISE_TYPE_RUNNING, if (french) "Sortie longue" else "Long run"),
            Triple(8L, ExerciseSessionRecord.EXERCISE_TYPE_YOGA, null),
            Triple(9L, ExerciseSessionRecord.EXERCISE_TYPE_BIKING, if (french) "Trajet à vélo" else "Bike commute"),
            Triple(10L, ExerciseSessionRecord.EXERCISE_TYPE_RUNNING, if (french) "Footing du matin" else "Morning run"),
        )
        return plan.mapIndexed { i, (daysAgo, type, title) ->
            val start = DashboardSamples.today.minusDays(daysAgo).atTime(7, 10 + i).toInstant(zone)
            val minutes = when (type) {
                ExerciseSessionRecord.EXERCISE_TYPE_RUNNING -> 32L + i * 3
                ExerciseSessionRecord.EXERCISE_TYPE_BIKING -> 24L
                else -> 45L
            }
            val end = start + Duration.ofMinutes(minutes)
            val packageName = if (type == ExerciseSessionRecord.EXERCISE_TYPE_RUNNING) "com.example.run" else "com.example.watch"
            val isRun = type == ExerciseSessionRecord.EXERCISE_TYPE_RUNNING
            ExerciseSessionRecord(
                startTime = start,
                startZoneOffset = zone,
                endTime = end,
                endZoneOffset = zone,
                metadata = TestRecords.metadata("run-$i", packageName, end),
                exerciseType = type,
                title = title,
                notes = if (i == 0) (if (french) "Bord de mer, vent de face au retour." else "Along the seafront, headwind on the way back.") else null,
                segments = if (i == 0) {
                    listOf(
                        ExerciseSegment(start, start + Duration.ofMinutes(5), ExerciseSegment.EXERCISE_SEGMENT_TYPE_WALKING),
                        ExerciseSegment(start + Duration.ofMinutes(5), end, ExerciseSegment.EXERCISE_SEGMENT_TYPE_RUNNING),
                    )
                } else {
                    emptyList()
                },
                laps = if (isRun && i == 0) {
                    (0 until 3).map { lap ->
                        val lapStart = start + Duration.ofMinutes(5 + lap * 9L)
                        ExerciseLap(lapStart, lapStart + Duration.ofMinutes(9), Length.meters(1600.0))
                    }
                } else {
                    emptyList()
                },
            )
        }
    }

    private fun capture(fileName: String, dark: Boolean = false, content: @Composable () -> Unit) {
        compose.setContent {
            TidemarkTheme(darkTheme = dark, dynamicColor = false) { content() }
        }
        compose.waitForIdle()
        save(compose.onRoot().captureToImage().asAndroidBitmap(), File(dir, fileName))
    }

    private fun save(bitmap: Bitmap, file: File) {
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}

@Config(qualifiers = "en-rUS-w411dp-h823dp-420dpi")
class StoreScreenshotEnglishTest : StoreScreenshotTest("en", Locale.US) {
    @Test
    fun icons() = renderIcons()
}

@Config(qualifiers = "fr-rFR-w411dp-h823dp-420dpi")
class StoreScreenshotFrenchTest : StoreScreenshotTest("fr", Locale.FRANCE)
