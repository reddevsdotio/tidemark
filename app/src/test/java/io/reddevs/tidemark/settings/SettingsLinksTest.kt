// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.settings

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class SettingsLinksTest {
    private val app: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun sourceCodeOpensTheRepositoryInABrowser() {
        val intent = SettingsLinks.sourceCode()
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("https://github.com/reddevsdotio/tidemark", intent.dataString)
        assertTrue(intent.hasCategory(Intent.CATEGORY_BROWSABLE))
    }

    @Test
    fun startActivityOrFalseReportsAMissingHandler() {
        shadowOf(app).checkActivities(true)
        val intent = SettingsLinks.sourceCode().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        assertFalse(app.startActivityOrFalse(intent))
    }

    @Test
    fun startActivityOrFalseStartsTheActivity() {
        val intent = SettingsLinks.sourceCode().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        assertTrue(app.startActivityOrFalse(intent))
        assertEquals(intent, shadowOf(app).nextStartedActivity)
    }
}
