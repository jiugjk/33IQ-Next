package com.jiugjk.iq33.library.network

import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test
import java.nio.charset.Charset

class WebFormRequestTest {
    @Test
    fun `web actions use AJAX headers UTF8 fields a non replayable body and GBK response decoding`() =
        runTest {
            var requests = 0
            val client =
                OkHttpClient
                    .Builder()
                    .addInterceptor { chain ->
                        requests++
                        val request = chain.request()
                        request.header("X-Requested-With") shouldBeEqualTo "XMLHttpRequest"
                        request.header("Origin") shouldBeEqualTo IqConstants.BASE_URL
                        request.header("Referer") shouldBeEqualTo "${IqConstants.BASE_URL}/"
                        val body = requireNotNull(request.body)
                        body.isOneShot() shouldBeEqualTo true
                        val buffer = Buffer()
                        body.writeTo(buffer)
                        buffer.readUtf8() shouldBeEqualTo "q_id=1&text=%E8%A7%A3%E6%9E%90"
                        Response
                            .Builder()
                            .request(request)
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body("解析".toByteArray(Charset.forName("GBK")).toResponseBody("text/html; charset=gbk".toMediaType()))
                            .build()
                    }.build()
            IqHtmlClient(client).postWebFormForText(IqConstants.ANSWER_QUOTE_URL, mapOf("q_id" to "1", "text" to "解析")) shouldBeEqualTo "解析"
            requests shouldBeEqualTo 1
        }
}
