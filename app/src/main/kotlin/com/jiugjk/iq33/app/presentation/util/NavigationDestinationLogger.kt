package com.jiugjk.iq33.app.presentation.util

import android.os.Bundle
import androidx.navigation.NavDestination
import com.jiugjk.iq33.app.presentation.NavigationRoute
import com.jiugjk.iq33.feature.base.util.TimberLogTags
import timber.log.Timber

object NavigationDestinationLogger {
    fun logDestinationChange(
        destination: NavDestination,
        arguments: Bundle?,
    ) {
        val className = NavigationRoute::class.simpleName
        val destinationRoute = destination.route?.substringAfter("$className.") ?: "Unknown"
        val destinationId = destination.id
        val destinationLabel = destination.label ?: "No Label"

        val logMessage =
            buildString {
                appendLine("Navigation destination changed:")
                appendLine("\tRoute: $destinationRoute")
                appendLine("\tID: $destinationId")
                appendLine("\tLabel: $destinationLabel")

                arguments?.let { bundle ->
                    if (!bundle.isEmpty) {
                        appendLine("   Arguments:")
                        bundle.keySet().forEach { key ->
                            appendLine("\t\t$key: ${bundle.describe(key)}")
                        }
                    }
                }
            }

        Timber.tag(TimberLogTags.NAVIGATION).d(logMessage)
    }

    /**
     * Formats one argument by reading the value that is actually stored.
     *
     * The previous version chained `getInt`, `getLong`, `getBoolean`, ... expecting a mismatched type
     * to throw. `Bundle` getters do not throw: they catch the class-cast internally and return the
     * default, so the chain always stopped at `getInt` and logged every `Long` argument (such as a
     * question id) as `0`.
     */
    private fun Bundle.describe(key: String): String =
        when (val value = valueOf(key)) {
            null -> "null"
            is String -> "\"$value\""
            is Array<*> -> value.contentToString()
            is IntArray -> value.contentToString()
            is LongArray -> value.contentToString()
            is FloatArray -> value.contentToString()
            is DoubleArray -> value.contentToString()
            is BooleanArray -> value.contentToString()
            else -> value.toString()
        }

    /**
     * Reads the stored value once, whatever its type.
     *
     * `Bundle.get` is deprecated from API 33 on in favour of typed getters, but a generic logger has
     * no type to ask for - and the typed getters are exactly what cannot be chained safely here. The
     * deprecated call is kept deliberately, in one place, for a debug-only logger.
     */
    @Suppress("DEPRECATION")
    private fun Bundle.valueOf(key: String): Any? = get(key)
}
