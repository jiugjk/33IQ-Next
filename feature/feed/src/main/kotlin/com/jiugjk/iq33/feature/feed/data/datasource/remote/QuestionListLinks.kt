package com.jiugjk.iq33.feature.feed.data.datasource.remote

import com.jiugjk.iq33.library.network.IqConstants
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.nodes.Document

/** Prefer advertised links, with the legacy page-number protocol for lists without navigation. */
internal object QuestionListLinks {
    private val NEXT_LABELS = setOf("下一页", "下一页 »", "下页", "next", ">", ">>", "›", "»")
    private val DETAIL_PATH = Regex("/question/\\d+\\.html")

    // Archived /question/ (全部 / 精选题目) paginates as /24h.html, /24h/2.html, … not /question/N.html.
    private val FEATURED_FEED_PATH = Regex("^/24h(/\\d+)?\\.html$")
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
        // Captured tag HTML uses ">" for the LAST page (e.g. 932), not the next page.
        // A declared rel=next wins; otherwise prefer the current page's immediate successor.
        val declaredNext = document.select("a[rel=next], link[rel=next]")
        return (declaredNext + following + explicit)
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

    /**
     * The list can support ?page=N without displaying a next-page control. Preserve the old client's
     * protocol in that case, but never override explicit navigation or invent a cursor-based URL.
     * Call only for a non-empty question page; the caller also stops on duplicate-only responses.
     */
    @Suppress("ReturnCount") // Fail closed at each URL/navigation validation boundary.
    fun legacyNextPage(document: Document): String? {
        val navigation = "a[rel=next], link[rel=next], a[rel=prev], link[rel=prev], .pagination, .pager, .page"
        if (document.select(navigation).isNotEmpty()) return null
        val current = document.location().toHttpUrlOrNull() ?: return null
        if (!isSafeListUrl(current.toString()) || current.queryParameterNames.any { it != "page" }) return null
        val values = current.queryParameterValues("page")
        val page = if (values.isEmpty()) 1 else values.singleOrNull()?.toIntOrNull() ?: return null
        if (page < 1 || page == Int.MAX_VALUE) return null

        return current
            .newBuilder()
            .setQueryParameter("page", (page + 1).toString())
            .fragment(null)
            .build()
            .toString()
    }

    fun isSafeListUrl(value: String): Boolean {
        val url = value.toHttpUrlOrNull() ?: return false
        return url.scheme == "https" && url.host == IqConstants.BASE_URL.toHttpUrlOrNull()?.host &&
            url.port == HTTPS_PORT && url.username.isEmpty() && url.password.isEmpty() &&
            isListPath(url.encodedPath)
    }

    private fun isListPath(path: String): Boolean {
        if (DETAIL_PATH.matches(path)) return false
        return path == "/question" ||
            path.startsWith("/question/") ||
            path.startsWith("/tag/") ||
            FEATURED_FEED_PATH.matches(path)
    }
}
