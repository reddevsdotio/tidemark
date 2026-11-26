// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.dashboard.detail

import io.reddevs.tidemark.data.model.DataSource
import io.reddevs.tidemark.data.model.SleepNight
import io.reddevs.tidemark.data.model.SleepStage
import io.reddevs.tidemark.data.model.SleepStageSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class HypnogramModelTest {
    private val night = DetailSamples.night()
    private val model = HypnogramModel.of(night)

    @Test
    fun everyStageKindGetsARowInOrder() {
        assertEquals(HypnogramModel.ROW_ORDER, model.rows)
        assertEquals(SleepStage.entries.toSet(), model.rows.toSet())
        assertFalse(model.stageless)
    }

    @Test
    fun segmentsKeepTheirStagesAndRows() {
        assertEquals(night.stages.size, model.segments.size)
        model.segments.forEach { assertEquals(model.rows[it.row], it.stage) }
        assertEquals(0f, model.segments.first().span.startFraction)
        assertEquals(1f, model.segments.last().span.endFraction)
    }

    @Test
    fun gapsBetweenStagesAreReported() {
        assertEquals(2, model.gaps.size)
        val length = Duration.between(night.start, night.end).toMinutes().toFloat()
        // The first gap starts after 162 minutes of stages and lasts 10 minutes.
        assertEquals(162 / length, model.gaps[0].startFraction, 1e-4f)
        assertEquals(172 / length, model.gaps[0].endFraction, 1e-4f)
    }

    @Test
    fun totalsAndSleepTime() {
        val byStage = model.totals.toMap()
        assertEquals(Duration.ofMinutes(155), byStage[SleepStage.LIGHT])
        assertEquals(Duration.ofMinutes(85), byStage[SleepStage.DEEP])
        assertEquals(Duration.ofMinutes(16), byStage[SleepStage.AWAKE])
        // The session minus awake (16), awake in bed (12) and out of bed (4). As in Health Connect's
        // sleep duration, the 18 minutes of gaps count as sleep.
        assertEquals(Duration.between(night.start, night.end) - Duration.ofMinutes(16 + 12 + 4), model.asleep)
        assertEquals(Duration.between(night.start, night.end), model.inBed)
    }

    @Test
    fun sessionWithoutStagesIsOneSleepingBlock() {
        val start = Instant.parse("2026-11-18T23:00:00Z")
        val plain = HypnogramModel.of(SleepNight("s", start, start.plusSeconds(8 * 3600), emptyList(), DataSource("a")))
        assertTrue(plain.stageless)
        assertEquals(listOf(SleepStage.SLEEPING), plain.rows)
        assertEquals(Duration.ofHours(8), plain.asleep)
        assertTrue(plain.gaps.isEmpty())
    }

    @Test
    fun stagesOutsideTheSessionAreClipped() {
        val start = Instant.parse("2026-11-18T23:00:00Z")
        val end = start.plusSeconds(3600)
        val clipped = HypnogramModel.of(
            SleepNight(
                "s", start, end,
                listOf(
                    SleepStageSpan(start.minusSeconds(600), start.plusSeconds(600), SleepStage.LIGHT),
                    SleepStageSpan(end.minusSeconds(600), end.plusSeconds(600), SleepStage.DEEP),
                    SleepStageSpan(end.plusSeconds(60), end.plusSeconds(120), SleepStage.REM),
                ),
                DataSource("a"),
            ),
        )
        assertEquals(listOf(SleepStage.LIGHT, SleepStage.DEEP), clipped.rows)
        // The whole hour: no awake stage, and the uncovered 40 minutes count as sleep.
        assertEquals(Duration.ofHours(1), clipped.asleep)
        assertEquals(1, clipped.gaps.size)
    }

    @Test
    fun hourTicksIncludeStartAndEnd() {
        val start = Instant.parse("2026-11-18T23:05:00Z")
        val ticks = hourTicks(start, start.plusSeconds(8 * 3600), ZoneOffset.UTC)
        assertEquals(start, ticks.first())
        assertEquals(start.plusSeconds(8 * 3600), ticks.last())
        assertEquals(Instant.parse("2026-11-19T00:00:00Z"), ticks[1])
    }
}
