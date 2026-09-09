package com.jiugjk.iq33.feature.feed.data.datasource.remote

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/*
 * Field accessors for 33IQ's loosely-typed legacy JSON, where numbers and booleans alike arrive as
 * strings. Kept as top-level extensions so the parsers and data sources share one set of rules for
 * "absent", "present but unparseable" and "present".
 *
 * Accessors never throw on an unexpected JSON shape: `jsonPrimitive` would crash the whole parse
 * when a field is an object or array, and a single deformed field must not take the question down.
 */

internal fun JsonObject.stringOrNull(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

/** Null when the field is absent, blank or not a number - deliberately distinct from a value of 0. */
internal fun JsonObject.intOrNull(key: String): Int? = stringOrNull(key)?.trim()?.toIntOrNull()

/** For display-only counters, where "not reported" and "zero" are shown the same way anyway. */
internal fun JsonObject.intOrZero(key: String): Int = intOrNull(key) ?: 0

internal fun JsonObject.jsonArrayOrEmpty(key: String): List<JsonElement> = (this[key] as? JsonArray).orEmpty()

internal fun JsonObject.stringList(key: String): List<String> =
    jsonArrayOrEmpty(key).mapNotNull { element -> (element as? JsonPrimitive)?.contentOrNull }
