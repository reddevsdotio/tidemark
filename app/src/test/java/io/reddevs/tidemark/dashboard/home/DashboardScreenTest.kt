// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.dashboard.home

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.reddevs.tidemark.data.model.Metric
import io.reddevs.tidemark.format.LocaleMeasurementFormatter
import io.reddevs.tidemark.settings.UnitPreference
import io.reddevs.tidemark.ui.theme.TidemarkTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale

@RunWith(AndroidJUnit4::class)
// Native graphics measure text for real; the legacy mode fakes glyph widths, which breaks the overflow checks.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-rUS-w360dp-h800dp-xxhdpi")
class DashboardScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val opened = mutableListOf<Metric>()
    private val retried = mutableListOf<Metric>()
    private var settingsOpened = 0
    private var dismissed = 0

    private fun show(state: DashboardUiState, fontScale: Float? = null, locale: Locale = Locale.US) {
        compose.setContent {
            ScaledFont(fontScale) {
                TidemarkTheme(dynamicColor = false) {
                    DashboardScreen(
                        state = state,
                        formatter = LocaleMeasurementFormatter(context, locale, UnitPreference.SYSTEM),
                        onRefresh = {},
                        onOpenMetric = { opened += it },
                        onRetry = { retried += it },
                        onOpenHealthConnectSettings = { settingsOpened++ },
                        onDismissBanner = { dismissed++ },
                        onOpenExplorer = {},
                        onOpenSettings = {},
                    )
                }
            }
        }
    }

    @Composable
    private fun ScaledFont(fontScale: Float?, content: @Composable () -> Unit) {
        if (fontScale == null) {
            content()
        } else {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale), content = content)
        }
    }

    private fun description(metric: Metric): String =
        compose.onNodeWithTag(tileTestTag(metric)).fetchSemanticsNode()
            .config[SemanticsProperties.ContentDescription].joinToString()
            // The formatter uses non-breaking and narrow spaces; compare them as plain spaces.
            .replace(' ', ' ').replace(' ', ' ').replace(' ', ' ')

    @Test
    fun tilesReadValueAndUnit() {
        show(DashboardSamples.allData)

        assertEquals("Steps, 8,432 steps, Today", description(Metric.STEPS))
        assertEquals("Active calories, 412 kcal, Today", description(Metric.ACTIVE_CALORIES))
        assertEquals("Distance, 3.9 mi, Today", description(Metric.DISTANCE))
        assertTrue(description(Metric.SLEEP), description(Metric.SLEEP).startsWith("Sleep, 7 h 5 min, "))
        assertTrue(description(Metric.SLEEP), description(Metric.SLEEP).endsWith(", From com.example.sleep"))
        assertEquals("Resting heart rate, 58 bpm, Today, From com.example.watch", description(Metric.RESTING_HEART_RATE))
        assertTrue(description(Metric.WEIGHT), description(Metric.WEIGHT).startsWith("Weight, 159.6 lb, "))
        assertTrue(description(Metric.WEIGHT), description(Metric.WEIGHT).endsWith(", From com.example.scale"))
    }

    @Test
    fun tappingATileOpensItsMetric() {
        show(DashboardSamples.allData)
        compose.onNodeWithTag(tileTestTag(Metric.SLEEP)).performScrollTo().performClick()
        assertEquals(listOf(Metric.SLEEP), opened)
    }

    @Test
    fun permissionAndErrorTilesOfferTheirAction() {
        show(DashboardSamples.mixed)

        compose.onNodeWithText("Open Health Connect settings").performScrollTo().performClick()
        assertEquals(1, settingsOpened)
        compose.onNodeWithText("Try again").performScrollTo().performClick()
        assertEquals(listOf(Metric.WEIGHT), retried)
        assertEquals("Active calories, No data today", description(Metric.ACTIVE_CALORIES))
        compose.onNodeWithText("Health Connect asked Tidemark to slow down. Try again in a moment.").assertExists()
        assertEquals("Distance, Loading", description(Metric.DISTANCE))
    }

    @Test
    fun deniedHistoryBannerOffersSettingsAndDismiss() {
        show(DashboardSamples.allData)

        compose.onNodeWithTag(BANNER_TEST_TAG).assertIsDisplayed()
        compose.onNode(hasText("Tidemark can only read data from about", substring = true)).assertExists()
        compose.onNode(hasText("Open Health Connect settings") and hasClickAction()).performClick()
        compose.onNodeWithText("Dismiss").performClick()
        assertEquals(1, settingsOpened)
        assertEquals(1, dismissed)
    }

    @Test
    fun unavailableHistoryBannerHasNoSettingsButton() {
        show(DashboardSamples.allData.copy(banner = DashboardSamples.mixed.banner))

        compose.onNodeWithText(
            "This version of Health Connect only shares data from 30 days before you first gave Tidemark access.",
        ).assertExists()
        compose.onNode(hasText("Open Health Connect settings")).assertDoesNotExist()
    }

    @Test
    fun noBannerWhenHistoryIsGranted() {
        show(DashboardSamples.allData.copy(banner = null))
        compose.onNodeWithTag(BANNER_TEST_TAG).assertDoesNotExist()
    }

    @Test
    fun topBarEntriesHaveLabels() {
        show(DashboardSamples.allData)
        compose.onNodeWithContentDescription("Browse records").assertExists()
        compose.onNodeWithContentDescription("Settings").assertExists()
    }

    @Test
    fun nothingTruncatesAtDoubleFontScaleOn360dp() {
        val checked = assertFitsAtDoubleFontScale(Locale.US, DashboardSamples.allData)
        // The tiles' own texts are among those checked, not only the banner and top bar.
        assertTrue(checked.toString(), "8,432 steps" in checked && "159.6 lb" in checked && "Resting heart rate" in checked)
    }

    @Test
    fun tileStatesFitAtDoubleFontScaleOn360dp() {
        assertFitsAtDoubleFontScale(Locale.US, DashboardSamples.mixed)
    }

    @Test
    @Config(qualifiers = "fr-rFR-w360dp-h800dp-xxhdpi")
    fun frenchFitsAtDoubleFontScaleOn360dp() {
        assertFitsAtDoubleFontScale(Locale.FRANCE, DashboardSamples.mixed)
        compose.onNodeWithText("Ouvrir les paramètres de Connexion Santé").assertExists()
    }

    /** Returns the texts it checked. */
    private fun assertFitsAtDoubleFontScale(locale: Locale, state: DashboardUiState): Set<String> {
        show(state, fontScale = 2f, locale = locale)
        val rootWidth = compose.onRoot().fetchSemanticsNode().size.width

        // One column, and every tile inside the screen's width.
        Metric.entries.forEach { metric ->
            // Size and position are unclipped, so tiles scrolled out of view count too.
            val node = compose.onNodeWithTag(tileTestTag(metric)).fetchSemanticsNode()
            val left = node.positionInRoot.x
            val width = node.size.width
            assertTrue("$metric starts on screen", left >= 0f)
            assertTrue("$metric ends on screen", left + width <= rootWidth)
            assertTrue("$metric takes the full row", width > rootWidth * 0.8f)
        }

        // No text is ellipsized, cut off at the bottom, or pushed past the screen edge. (didOverflowWidth
        // isn't used: Compose reports it for single-line texts in a Row even when they fit.)
        val texts = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
            .fetchSemanticsNodes()
        assertTrue("found text to check", texts.isNotEmpty())
        val checked = mutableSetOf<String>()
        texts.forEach { node ->
            val results = mutableListOf<TextLayoutResult>()
            node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
            results.forEach { layout ->
                val text = layout.layoutInput.text
                checked += text.text
                assertFalse("'$text' is cut off at the bottom", layout.didOverflowHeight)
                (0 until layout.lineCount).forEach { line ->
                    assertFalse("'$text' is ellipsized", layout.isLineEllipsized(line))
                }
            }
            assertTrue(
                "text node ${node.config} ends past the screen edge",
                node.positionInRoot.x + node.size.width <= rootWidth,
            )
        }
        return checked
    }
}
