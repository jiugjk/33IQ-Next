package com.jiugjk.iq33.library.network

import androidx.core.net.toUri

/**
 * The one place a shareable 33IQ link is built.
 *
 * Every link this app hands out carries the same two tracking parameters, so they live here as
 * constants rather than being spelled out at each share entry point: a value that appears in more
 * than one string literal is a value that eventually disagrees with itself.
 *
 * It sits next to [IqConstants.questionPageUrl] - the plain page URL it builds on - so "which URL
 * does a question have" and "what do we append when sharing it" stay one decision in one file.
 */
object ShareConfig {
    /** Referrer id credited for questions opened through a link this app shared. */
    const val RRU_ID = "6470927"

    /** Marks the traffic as coming from the Android client. */
    const val SOURCE = "ard"

    private const val RRU_ID_PARAM = "rruID"
    private const val SOURCE_PARAM = "source"

    /**
     * The public question link to hand out, with this client's tracking parameters attached.
     *
     * Built through `Uri.Builder` rather than string concatenation so the parameters are encoded and
     * separated correctly no matter what [IqConstants.questionPageUrl] returns - including if it
     * ever grows a query string of its own.
     */
    fun questionShareUrl(questionId: Long): String =
        IqConstants
            .questionPageUrl(questionId)
            .toUri()
            .buildUpon()
            .appendQueryParameter(RRU_ID_PARAM, RRU_ID)
            .appendQueryParameter(SOURCE_PARAM, SOURCE)
            .build()
            .toString()
}
