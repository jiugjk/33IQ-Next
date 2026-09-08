package com.jiugjk.iq33.feature.feed.data.datasource.remote

import java.io.IOException

/**
 * Raised when 33IQ answers with a technically valid response that is not a usable success.
 *
 * These endpoints are a loosely-typed legacy PHP API: a business failure comes back as an ordinary
 * HTTP 200 JSON object. Treating "it parsed as JSON" as success is what used to turn an error
 * envelope into an empty answer, a 0-学识 price quote or an upvote count of 0 - so every endpoint
 * validates the fields it actually needs and raises this instead of substituting a default.
 */
internal class IqResponseException(
    /** Which 33IQ endpoint replied, for diagnostics. */
    val endpoint: String,
    val reason: Reason,
    /** The `status` the server reported, when it reported one. */
    val status: String? = null,
    /**
     * True when an earlier call in the same multi-step flow already succeeded, so the account may
     * already have been charged even though this step failed. Callers must not present such a
     * failure as "nothing happened".
     */
    val afterSideEffect: Boolean = false,
) : IOException(
        buildString {
            append("33IQ $endpoint responded with ")
            append(
                when (reason) {
                    Reason.BUSINESS_ERROR -> "business error status=$status"
                    Reason.MISSING_FIELD -> "a response missing a required field (status=$status)"
                    Reason.MALFORMED -> "a response that is not a JSON object"
                },
            )
            if (afterSideEffect) append(" after an earlier step of the flow had already succeeded")
        },
    ) {
    enum class Reason {
        /** The server explicitly reported a failure status. */
        BUSINESS_ERROR,

        /** A field this client needs to build its result is absent or unparseable. */
        MISSING_FIELD,

        /** The body is not the JSON object shape this endpoint is documented to return. */
        MALFORMED,
    }
}
