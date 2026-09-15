package com.jiugjk.iq33.library.network

import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.nio.charset.Charset

/**
 * What a request does when 33IQ's session has lapsed underneath it.
 *
 * The login wall is the only thing this client ever sees of an expired session, so it is also the
 * only place a recovery can start. These cover the three ways that has to end: restored and
 * repeated, unrecoverable and reported, and - for anything that may already have been acted on -
 * reported without being sent twice.
 */
class SessionRecoveryRetryTest {
    private var requests = 0
    private var recoveries = 0

    @Test
    fun `a GET that hits the login wall is repeated once the session is restored`() =
        runTest {
            val sut = client(recovers = true)

            sut.get(IqConstants.BASE_URL).title() shouldBeEqualTo "题目"

            requests shouldBeEqualTo 2
            recoveries shouldBeEqualTo 1
        }

    @Test
    fun `a JSON endpoint behind the login wall is repeated the same way`() =
        runTest {
            val sut = client(recovers = true)

            sut.getText(IqConstants.GUEST_PROBE_URL) shouldBeEqualTo SERVED

            requests shouldBeEqualTo 2
            recoveries shouldBeEqualTo 1
        }

    @Test
    fun `a GET is reported, not repeated, when the session cannot be restored`() =
        runTest {
            val sut = client(recovers = false)

            val thrown = runCatching { sut.get(IqConstants.BASE_URL) }.exceptionOrNull()

            thrown shouldBeInstanceOf IqLoginRequiredException::class
            requests shouldBeEqualTo 1
            recoveries shouldBeEqualTo 1
        }

    @Test
    fun `a request that opts out of recovery is left alone`() =
        runTest {
            val sut = client(recovers = true)

            // How the probe and the renewal themselves avoid starting a recovery from inside one.
            val thrown = runCatching { sut.get(IqConstants.BASE_URL, allowSessionRecovery = false) }.exceptionOrNull()

            thrown shouldBeInstanceOf IqLoginRequiredException::class
            requests shouldBeEqualTo 1
            recoveries shouldBeEqualTo 0
        }

    @Test
    fun `a POST that hits the login wall is never replayed against the restored session`() =
        runTest {
            val sut = client(recovers = true)

            // Answers, hints and payments are not idempotent: the first POST may already have been
            // acted on, so a restored session is no reason to send it a second time.
            val thrown = runCatching { sut.postFormForText(IqConstants.SUBMIT_ANSWER_URL, mapOf("id" to "1")) }.exceptionOrNull()

            thrown shouldBeInstanceOf IqLoginRequiredException::class
            requests shouldBeEqualTo 1
            recoveries shouldBeEqualTo 0
        }

    /** Serves the login wall once, then the real page - as a server does once the session is back. */
    private fun client(recovers: Boolean): IqHtmlClient {
        val okHttpClient =
            OkHttpClient
                .Builder()
                .addInterceptor { chain ->
                    requests++
                    reply(chain.request(), if (requests == 1) LOGIN_WALL else SERVED)
                }.build()

        return IqHtmlClient(okHttpClient) {
            SessionRecovery {
                recoveries++
                recovers
            }
        }
    }

    private fun reply(
        request: Request,
        html: String,
    ) = Response
        .Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(200)
        .message("OK")
        // 33IQ serves GBK, and the login wall is only recognisable once it is decoded as such.
        .body(html.toByteArray(Charset.forName(IqConstants.PAGE_CHARSET)).toResponseBody(HTML_MEDIA_TYPE))
        .build()

    private companion object {
        val HTML_MEDIA_TYPE = "text/html; charset=gbk".toMediaType()
        const val LOGIN_WALL = "<html><head><title>用户登录</title></head><body>请先登录</body></html>"
        const val SERVED = "<html><head><title>题目</title></head><body>正文</body></html>"
    }
}
