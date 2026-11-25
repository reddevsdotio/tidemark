// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.settings

import android.content.Context
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.reddevs.tidemark.AppContainer
import io.reddevs.tidemark.BuildConfig
import io.reddevs.tidemark.R
import io.reddevs.tidemark.format.UnitSystem
import io.reddevs.tidemark.testing.FakeHealthRepository
import io.reddevs.tidemark.testing.FakeUserPreferences
import io.reddevs.tidemark.ui.theme.TidemarkTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {
    @get:Rule val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @After
    fun tearDown() = scope.cancel()

    private fun string(id: Int, vararg args: Any) = context.getString(id, *args)

    /** LazyColumn composes only visible rows, so scroll the list to a row before looking it up. */
    private fun row(text: String): SemanticsNodeInteraction {
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text))
        return compose.onNodeWithText(text)
    }

    private fun setScreen(
        selected: UnitPreference? = UnitPreference.SYSTEM,
        systemUnits: UnitSystem = UnitSystem.US,
        onSelectUnit: (UnitPreference) -> Unit = {},
        onManageHealthConnect: () -> Unit = {},
        onOpenPrivacy: () -> Unit = {},
        onOpenSourceCode: () -> Unit = {},
        onBack: () -> Unit = {},
    ) = compose.setContent {
        TidemarkTheme(dynamicColor = false) {
            SettingsScreen(
                unitPreference = selected,
                systemUnits = systemUnits,
                versionName = "9.8.7",
                onSelectUnit = onSelectUnit,
                onManageHealthConnect = onManageHealthConnect,
                onOpenPrivacy = onOpenPrivacy,
                onOpenSourceCode = onOpenSourceCode,
                onBack = onBack,
            )
        }
    }

    @Test
    fun unitChoicesShowTheSelectionAndWhatSystemMeans() {
        setScreen(selected = UnitPreference.METRIC, systemUnits = UnitSystem.UK)
        compose.onNodeWithText(string(R.string.settings_units_metric)).assertExists()
        compose.onNodeWithText(string(R.string.settings_units_system_current, string(R.string.settings_units_summary_uk)))
            .assertExists()
        compose.onNode(hasText(string(R.string.settings_units_metric)) and isSelectable()).assertIsSelected()
        compose.onNode(hasText(string(R.string.settings_units_imperial)) and isSelectable()).assertIsNotSelected()
    }

    @Test
    fun choosingAUnitReportsIt() {
        val chosen = mutableListOf<UnitPreference>()
        setScreen(onSelectUnit = { chosen += it })
        compose.onNodeWithText(string(R.string.settings_units_imperial)).performClick()
        compose.onNodeWithText(string(R.string.settings_units_system)).performClick()
        assertEquals(listOf(UnitPreference.IMPERIAL, UnitPreference.SYSTEM), chosen)
    }

    @Test
    fun rowsCallTheirCallbacks() {
        val calls = mutableListOf<String>()
        setScreen(
            onManageHealthConnect = { calls += "hc" },
            onOpenPrivacy = { calls += "privacy" },
            onOpenSourceCode = { calls += "source" },
            onBack = { calls += "back" },
        )
        row(string(R.string.settings_manage_access)).performClick()
        row(string(R.string.settings_privacy_policy)).performClick()
        row(string(R.string.settings_source_code)).performClick()
        compose.onNode(hasContentDescription(string(R.string.action_back))).performClick()
        assertEquals(listOf("hc", "privacy", "source", "back"), calls)
    }

    @Test
    fun aboutShowsVersionLicenseAndNoNetworkLine() {
        setScreen()
        row(string(R.string.settings_no_network)).assertExists()
        row("9.8.7").assertExists()
        row(string(R.string.settings_license_value)).assertExists()
        row("github.com/reddevsdotio/tidemark").assertExists()
    }

    @Test
    @Config(qualifiers = "fr-rFR")
    fun frenchUsesConnexionSante() {
        setScreen()
        compose.onNodeWithText("Paramètres").assertExists()
        row("Gérer l'accès à Connexion Santé").assertExists()
    }

    @Test
    fun routeWiresTheUnitChoiceIntoTheContainerFormatter() {
        val preferences = FakeUserPreferences()
        val container = AppContainer(context, FakeHealthRepository(), preferences, scope = scope)
        compose.setContent {
            TidemarkTheme(dynamicColor = false) {
                SettingsRoute(container = container, onOpenPrivacy = {}, onBack = {})
            }
        }
        compose.onNodeWithText(string(R.string.settings_units_imperial)).performClick()
        compose.waitUntil(5_000) { container.formatter.value.distanceUnitLabel() == "mi" }
        assertEquals(UnitPreference.IMPERIAL, preferences.unitPreference.value)
        row(BuildConfig.VERSION_NAME).assertExists()
    }
}
