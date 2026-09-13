package com.jiugjk.iq33.feature.feed.data.datasource.remote

import com.jiugjk.iq33.library.network.IqConstants
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.nodes.Document

/** Follows advertised links only. A missing link is an end, not permission to guess ?page=N. */
internal object QuestionListLinks {
    private val NEXT_LABELS = setOf("下一页", "下一页 »", "下页", "next", ">", ">>", "›", "»")
    private val DETAIL_PATH = Regex("/question/\\d+\\.html")
    private const val HTTPS_PORT = 443

    fun nextPage(document: Document): String? {
        val current = document.location().toHttpUrlOrNull() ?: return null
        val candidates = document.select("a[rel=next], link[rel=next], .pagination a[href], .pager a[href], .page a[href]")
        val explicit =
            candidates.filter { link ->
                "next" in link.attr("rel").lowercase().split(Regex("\\s+")) ||
                    listOf(link.text(), link.attr("title"), link.attr("aria-label")).any { it.trim().lowercase() in NEXT_LABELS }
            }
        val following =
            document.select(
                ".pagination .active + li a[href], .pagination .current + a[href], .pagination .current + li a[href]",
            )
        return (explicit + following)
            .asSequence()
            .filterNot { link ->
                link.attr("href").isBlank() || link.hasClass("disabled") ||
                    link.parent()?.hasClass("disabled") == true || link.attr("aria-disabled") == "true"
            }.mapNotNull { link ->
                current
                    .resolve(link.attr("href"))
                    ?.newBuilder()
                    ?.fragment(null)
                    ?.build()
                    ?.toString()
            }.firstOrNull { url ->
                isSafeListUrl(url) && url !=
                    current
                        .newBuilder()
                        .fragment(null)
                        .build()
                        .toString()
            }
    }

    fun isSafeListUrl(value: String): Boolean {
        val url = value.toHttpUrlOrNull() ?: return false
        return url.scheme == "https" && url.host == IqConstants.BASE_URL.toHttpUrlOrNull()?.host &&
            url.port == HTTPS_PORT && url.username.isEmpty() && url.password.isEmpty() &&
            (url.encodedPath.startsWith("/question/") || url.encodedPath.startsWith("/tag/")) &&
            !DETAIL_PATH.matches(url.encodedPath)
    }
}
