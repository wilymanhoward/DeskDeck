package com.deskdeck.data.brief

import com.deskdeck.domain.brief.Headline
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * A deliberately small RSS 2.0 reader: titles, links and dates of `<item>`s, nothing else. Plain
 * Kotlin (no Android XML classes), so it's unit-testable; feeds come from a fixed list of
 * publishers, so a full XML parser isn't needed.
 */
object RssParser {
    private val ITEM = Regex("""<item\b[^>]*>(.*?)</item>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))

    fun parse(xml: String, source: String, limit: Int = 10): List<Headline> =
        ITEM.findAll(xml).mapNotNull { match ->
            val item = match.groupValues[1]
            val title = tag(item, "title")?.let(::clean)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val link = tag(item, "link")?.let(::clean)?.takeIf { it.startsWith("https://") || it.startsWith("http://") } ?: return@mapNotNull null
            Headline(title = title, source = source, url = link, publishedAtMs = tag(item, "pubDate")?.let(::clean)?.let(::parseDate))
        }.take(limit).toList()

    private fun tag(item: String, name: String): String? =
        Regex("""<$name\b[^>]*>(.*?)</$name>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)).find(item)?.groupValues?.get(1)

    private fun clean(raw: String): String {
        val text = raw.trim().removePrefix("<![CDATA[").removeSuffix("]]>")
        return text
            .replace(Regex("<[^>]+>"), "")
            .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
            .replace("&apos;", "'").replace("&#39;", "'").replace("&#8217;", "’").replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun parseDate(text: String): Long? =
        runCatching { ZonedDateTime.parse(text, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }.getOrNull()
}
