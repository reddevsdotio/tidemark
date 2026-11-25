// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri

/** Intents the settings screen fires. Tidemark itself opens no connection: links go to other apps. */
internal object SettingsLinks {
    const val SOURCE_CODE_URL = "https://github.com/reddevsdotio/tidemark"

    fun sourceCode(): Intent =
        Intent(Intent.ACTION_VIEW, SOURCE_CODE_URL.toUri()).addCategory(Intent.CATEGORY_BROWSABLE)
}

/** Starts [intent] and returns false instead of crashing when no app handles it. */
internal fun Context.startActivityOrFalse(intent: Intent): Boolean = try {
    startActivity(intent)
    true
} catch (_: ActivityNotFoundException) {
    false
} catch (_: SecurityException) {
    false
}
