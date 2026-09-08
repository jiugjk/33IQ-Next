package com.jiugjk.iq33.library.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.IOException
import java.net.URLEncoder

/** Thrown when 33IQ redirects a request to its login page instead of serving the requested content. */
class IqLoginRequiredException : IOException("33IQ 要求登录后才能查看该内容，或触发了反爬虫验证")

/**
 * Thin HTTP client around [OkHttpClient] used to fetch and parse 33IQ's server-rendered pages.
 *
 * 33IQ has no public JSON API, so this app works by requesting the same HTML pages a mobile browser
 * would get and parsing them with Jsoup. The site's pages are served as GBK (not UTF-8), which is
 * handled explicitly here since OkHttp/Jsoup both default to UTF-8.
 */
class IqHtmlClient(
    private val okHttpClient: OkHttpClient,
) {
    suspend fun get(url: String): Document =
        withContext(Dispatchers.IO) {
            val request =
                Request
                    .Builder()
                    .url(url)
                    .get()
                    .build()

            execute(request)
        }

    suspend fun postForm(
        url: String,
        params: Map<String, String>,
    ): Document =
        withContext(Dispatchers.IO) {
            val encodedBody = params.entries.joinToString("&") { (key, value) -> "$key=${encodeGbk(value)}" }
            val body = encodedBody.toRequestBody(FORM_MEDIA_TYPE)
            val request =
                Request
                    .Builder()
                    .url(url)
                    .post(body)
                    .build()

            execute(request)
        }

    /** Raw text response (e.g. for the JSON login endpoint) rather than a parsed HTML [Document]. */
    suspend fun postFormForText(
        url: String,
        params: Map<String, String>,
    ): String =
        withContext(Dispatchers.IO) {
            val encodedBody = params.entries.joinToString("&") { (key, value) -> "$key=${encodeGbk(value)}" }
            val body = encodedBody.toRequestBody(FORM_MEDIA_TYPE)
            val request =
                Request
                    .Builder()
                    .url(url)
                    .post(body)
                    .build()

            okHttpClient.newCall(request).execute().use { response ->
                response.checkSuccessful()

                decodeGbk(response.body.bytes())
            }
        }

    /**
     * Raw text response for 33IQ's app-facing JSON endpoints (e.g. `/question/<id>.html?p=3`,
     * `/app/taskall`). These are the *same* URLs the public website serves as HTML, but adding the
     * right `p` query parameter (confirmed from a real captured app session, value differs per
     * endpoint) flips the response to plain JSON.
     */
    suspend fun getText(url: String): String =
        withContext(Dispatchers.IO) {
            val request =
                Request
                    .Builder()
                    .url(url)
                    .get()
                    .build()

            okHttpClient.newCall(request).execute().use { response ->
                response.checkSuccessful()

                val text = decodeGbk(response.body.bytes())

                if (isLoginWallText(text)) throw IqLoginRequiredException()

                text
            }
        }

    private fun execute(request: Request): Document {
        okHttpClient.newCall(request).execute().use { response ->
            response.checkSuccessful()

            val html = decodeGbk(response.body.bytes())
            val document = Jsoup.parse(html, response.request.url.toString())

            if (isLoginWall(document)) throw IqLoginRequiredException()

            return document
        }
    }

    private fun isLoginWall(document: Document): Boolean = document.title().contains("用户登录") || document.selectFirst(".login-card") != null

    private fun isLoginWallText(text: String): Boolean = text.contains("用户登录") || text.contains("login-card")

    private fun decodeGbk(bytes: ByteArray): String = String(bytes, charset(IqConstants.PAGE_CHARSET))

    private fun encodeGbk(value: String): String = URLEncoder.encode(value, IqConstants.PAGE_CHARSET)

    private fun Response.checkSuccessful() {
        if (!isSuccessful) throw IOException("HTTP $code")
    }

    private companion object {
        val FORM_MEDIA_TYPE = "application/x-www-form-urlencoded".toMediaType()
    }
}
