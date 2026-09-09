package com.jiugjk.iq33.feature.base.util

/**
 * Centralized log tags for consistent logging throughout the application.
 *
 * Use with Timber: `Timber.tag(TimberLogTags.NETWORK).d("message")`.
 */
object TimberLogTags {
    const val NETWORK = "Network"

    const val DATABASE = "Database"

    const val ACTION = "Action"

    const val NAVIGATION = "Navigation"
}
