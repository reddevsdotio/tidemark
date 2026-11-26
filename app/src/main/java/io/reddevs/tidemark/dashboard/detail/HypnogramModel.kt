// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.dashboard.detail

import io.reddevs.tidemark.data.model.SleepNight
import io.reddevs.tidemark.data.model.SleepStage
import io.reddevs.tidemark.data.model.SleepStageSpan
import java.time.Duration
import java.time.Instant

/**
 * Layout-independent description of a hypnogram. Fractions run from 0 at the session start to 1 at
 * its end, so the Canvas only scales them.
 */
internal data class HypnogramModel(
    val start: Instant,
    val end: Instant,
    /** Stages that occur this night, top row first. */
    val rows: List<SleepStage>,
    val segments: List<Segment>,
    /** Parts of the session that no stage covers. */
    val gaps: List<Span>,
    /** Time per stage, in [rows] order. */
    val totals: List<Pair<SleepStage, Duration>>,
    /** Sleep time: the session minus its awake and out-of-bed stages, as Health Connect counts it. */
    val asleep: Duration,
    /** Session length from start to end. */
    val inBed: Duration,
    /** True when the session carried no stage data and the whole session is drawn as "Sleeping". */
    val stageless: Boolean,
) {
    data class Span(val startFraction: Float, val endFraction: Float)

    data class Segment(val row: Int, val stage: SleepStage, val span: Span, val start: Instant, val end: Instant)

    companion object {
        /** Top to bottom: wake states first, then sleep from lightest to deepest. */
        val ROW_ORDER = listOf(
            SleepStage.OUT_OF_BED,
            SleepStage.AWAKE,
            SleepStage.AWAKE_IN_BED,
            SleepStage.REM,
            SleepStage.LIGHT,
            SleepStage.SLEEPING,
            SleepStage.DEEP,
            SleepStage.UNKNOWN,
        )

        private val NOT_ASLEEP = setOf(SleepStage.OUT_OF_BED, SleepStage.AWAKE, SleepStage.AWAKE_IN_BED)

        fun of(night: SleepNight): HypnogramModel {
            val start = night.start
            val end = maxOf(night.end, night.start)
            val length = Duration.between(start, end).toMillis().coerceAtLeast(1).toFloat()
            fun fraction(t: Instant) = (Duration.between(start, t).toMillis() / length).coerceIn(0f, 1f)

            val clipped = night.stages
                .map { it.copy(start = maxOf(it.start, start), end = minOf(it.end, end)) }
                .filter { it.end.isAfter(it.start) }
                .sortedWith(compareBy({ it.start }, { it.end }))
            val stageless = clipped.isEmpty()
            val spans = if (stageless) {
                listOf(SleepStageSpan(start, end, SleepStage.SLEEPING))
            } else {
                clipped
            }

            val present = spans.mapTo(hashSetOf()) { it.stage }
            val rows = ROW_ORDER.filter { it in present }
            val segments = spans.map {
                Segment(rows.indexOf(it.stage), it.stage, Span(fraction(it.start), fraction(it.end)), it.start, it.end)
            }

            val gaps = buildList {
                var covered = start
                for (span in spans) {
                    if (span.start.isAfter(covered)) add(Span(fraction(covered), fraction(span.start)))
                    covered = maxOf(covered, span.end)
                }
                if (end.isAfter(covered)) add(Span(fraction(covered), 1f))
            }

            val totals = rows.map { stage ->
                stage to spans.filter { it.stage == stage }
                    .fold(Duration.ZERO) { acc, s -> acc + Duration.between(s.start, s.end) }
            }
            // Health Connect's sleep duration is the session minus its awake stages, so gaps count as
            // sleep. Matching it keeps this figure equal to the sleep charts and the dashboard tile.
            val inBed = Duration.between(start, end)
            val awake = totals.filter { it.first in NOT_ASLEEP }.fold(Duration.ZERO) { acc, p -> acc + p.second }
            val asleep = (inBed - awake).coerceAtLeast(Duration.ZERO)

            return HypnogramModel(
                start = start,
                end = end,
                rows = rows,
                segments = segments,
                gaps = gaps,
                totals = totals,
                asleep = asleep,
                inBed = inBed,
                stageless = stageless,
            )
        }
    }
}
