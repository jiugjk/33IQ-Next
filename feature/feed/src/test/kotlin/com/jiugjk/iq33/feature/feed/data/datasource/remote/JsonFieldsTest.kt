package com.jiugjk.iq33.feature.feed.data.datasource.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeNull
import org.junit.jupiter.api.Test

class JsonFieldsTest {
    @Test
    fun `a non-primitive field is treated as absent instead of crashing the parse`() {
        val json = Json.parseToJsonElement("""{"qc_title":{"nested":"x"},"tag_micro":[{"gtt_name":"逻辑"}]}""").jsonObject

        json.stringOrNull("qc_title").shouldBeNull()
        json.stringList("tag_micro") shouldBeEqualTo emptyList()
    }

    @Test
    fun `a string field is still read`() {
        val json = Json.parseToJsonElement("""{"qc_title":"题"}""").jsonObject

        json.stringOrNull("qc_title") shouldBeEqualTo "题"
    }
}
