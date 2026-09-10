package com.jiugjk.iq33.library.network

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.IOException
import java.net.URLEncoder
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Thrown when 33IQ redirects a request to its login page instead of serving the requested content.
 *
 * The message is a diagnostic, not UI copy - callers map this type to their own localised text.
 */
class IqLoginRequiredException : IOException("33IQ served its login wall instead of the requested content")

/**
 * Thin HTTP client around [OkHttpClient] used to fetch and parse 33IQ's server-rendered pages.
 *
 * 33IQ has no public JSON API, so this app works by requesting the same HTML pages a mobile browser
 * would get and parsing them with Jsoup. The site's pages are served as GBK (not UTF-8), which is
 * handled explicitly here since OkHttp/Jsoup both default to UTF-8.
 *
 * Every call is bound to its coroutine: cancelling the caller aborts the HTTP call instead of
 * leaving it to run to completion on an IO thread with nobody waiting for - or wanting - its result.
 */
class IqHtmlClient(
    private val okHttpClient: OkHttpClient,
) {
    suspend fun get(url: String): Document {
        val request =
            Request
                .Builder()
                .url(url)
                .get()
                .build()

        return execute(request) { response ->
            val html = decodeGbk(response.body.bytes())
            val document = Jsoup.parse(html, response.request.url.toString())

            if (isLoginWall(document)) throw IqLoginRequiredException()

            document
        }
    }

    /** Raw text response (e.g. for the JSON login endpoint) rather than a parsed HTML [Document]. */
    suspend fun postFormForText(
        url: String,
        params: Map<String, String>,
    ): String {
        val encodedBody = params.entries.joinToString("&") { (key, value) -> "$key=${encodeGbk(value)}" }
        val request =
            Request
                .Builder()
                .url(url)
                .post(encodedBody.toRequestBody(FORM_MEDIA_TYPE))
                .build()

        return execute(request) { response ->
            val text = decodeGbk(response.body.bytes())

            if (isLoginWallText(text)) throw IqLoginRequiredException()

            text
        }
    }

    /**
     * Raw text response for 33IQ's app-facing JSON endpoints (e.g. `/question/<id>.html?p=3`,
     * `/app/taskall`). These are the *same* URLs the public website serves as HTML, but adding the
     * right `p` query parameter (confirmed from a captured app session, value differs per
     * endpoint) flips the response to plain JSON.
     */
    suspend fun getText(url: String): String {
        val request =
            Request
                .Builder()
                .url(url)
                .get()
                .build()

        return execute(request) { response ->
            val text = decodeGbk(response.body.bytes())

            if (isLoginWallText(text)) throw IqLoginRequiredException()

            text
        }
    }

    /**
     * Enqueues the call and cancels it from [CancellableContinuation.invokeOnCancellation], which
     * runs as soon as the coroutine enters Cancelling - unlike [Job.invokeOnCompletion], which waits
     * until a blocking [Call.execute] has already finished reading the body.
     */
    private suspend fun <T> execute(
        request: Request,
        readResponse: (Response) -> T,
    ): T {
        coroutineContext.ensureActive()

        return withContext(Dispatchers.IO) {
            suspendCancellableCoroutine { continuation ->
                val call = okHttpClient.newCall(request)

                continuation.invokeOnCancellation { call.cancel() }

                call.enqueue(ResponseCallback(continuation, readResponse))
            }
        }
    }

    private inner class ResponseCallback<T>(
        private val continuation: CancellableContinuation<T>,
        private val readResponse: (Response) -> T,
    ) : Callback {
        override fun onFailure(
            call: Call,
            e: IOException,
        ) {
            continuation.resumeIfActive(
                if (call.isCanceled()) CancellationException("HTTP call cancelled", e) else e,
            )
        }

        override fun onResponse(
            call: Call,
            response: Response,
        ) {
            response.use { body ->
                val outcome =
                    runCatching {
                        check(continuation.isActive)
                        body.checkSuccessful()
                        readResponse(body)
                    }
                outcome.fold(
                    onSuccess = { value -> if (continuation.isActive) continuation.resume(value) },
                    onFailure = { error -> continuation.resumeIfActive(error) },
                )
            }
        }
    }

    private fun <T> CancellableContinuation<T>.resumeIfActive(error: Throwable) {
        if (isActive) resumeWithException(error)
    }

    private fun isLoginWall(document: Document): Boolean = document.title().contains("用户登录") || document.selectFirst(".login-card") != null

    /**
     * JSON payloads (question detail, probes, action endpoints) are never a login wall, even when a
     * riddle's body happens to contain the words "用户登录". Only HTML-shaped responses are checked.
     */
    private fun isLoginWallText(text: String): Boolean {
        val trimmed = text.trimStart()

        if (trimmed.startsWith("{") || trimmed.startsWith("[")) return false

        return text.contains("用户登录") || text.contains("login-card")
    }

    private fun decodeGbk(bytes: ByteArray): String = String(bytes, charset(IqConstants.PAGE_CHARSET))

    private fun encodeGbk(value: String): String = URLEncoder.encode(value, IqConstants.PAGE_CHARSET)

    private fun Response.checkSuccessful() {
        if (!isSuccessful) throw IOException("HTTP $code")
    }

    private companion object {
        val FORM_MEDIA_TYPE = "application/x-www-form-urlencoded".toMediaType()
    }
}
