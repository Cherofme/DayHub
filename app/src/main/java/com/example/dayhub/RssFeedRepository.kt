package com.example.dayhub

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

internal data class RssFeed(
    val category: String,
    val source: String,
    val url: String,
    val onlyImportantInBrief: Boolean = false
)

internal data class RssHeadline(
    val category: String,
    val source: String,
    val title: String
)

internal data class RssBrief(
    val headlines: List<RssHeadline>,
    val successfulFeeds: Int
)

internal object RssFeedRepository {
    private const val MAX_RESPONSE_BYTES = 1_048_576
    private val whitespace = Regex("\\s+")
    private val nonAlphanumeric = Regex("[^\\p{L}\\p{N}]")

    private val importantBulgariaMarkers = listOf(
        "election", "parliament", "government", "cabinet", "president",
        "prime minister", "resignation", "resign", "coalition", "corruption",
        "protest", "budget", "inflation", "energy", "euro", "court ruling",
        "earthquake", "flood", "wildfire", "explosion", "attack", "emergency",
        "disaster", "fatal", "war", "border", "strike", "security", "crisis"
    )
    private val euOrNato = Regex("""\b(?:eu|nato)\b""")

    val feeds = listOf(
        RssFeed("Мир", "BBC", "https://feeds.bbci.co.uk/news/world/rss.xml"),
        RssFeed("Украина", "Українська правда", "https://www.pravda.com.ua/rss/"),
        RssFeed("Болгария", "BTA", "https://www.bta.bg/en/rss/free", onlyImportantInBrief = true),
        RssFeed("ИИ", "TechCrunch", "https://techcrunch.com/category/artificial-intelligence/feed/")
    )

    fun recentStories(
        feed: RssFeed,
        cutoffMillis: Long,
        limit: Int = 12,
        timeoutMillis: Int = 12_000
    ): List<RssStory> {
        val xml = fetchText(feed.url, timeoutMillis)
        return SafeRssParser.recentStories(xml, cutoffMillis, limit)
    }

    fun fetchBrief(cutoffMillis: Long, timeoutMillis: Int): RssBrief {
        val headlines = mutableListOf<RssHeadline>()
        val seenTitles = mutableSetOf<String>()
        var successfulFeeds = 0

        for (feed in feeds) {
            try {
                val limit = if (feed.onlyImportantInBrief) 12 else 4
                val stories = recentStories(feed, cutoffMillis, limit, timeoutMillis)
                successfulFeeds++

                val story = stories.firstOrNull { candidate ->
                    val title = candidate.title.replace(whitespace, " ").trim()
                    val normalized = title.lowercase(Locale.ROOT).replace(nonAlphanumeric, "")
                    title.isNotBlank() &&
                        normalized.isNotBlank() &&
                        (!feed.onlyImportantInBrief || isImportantBulgaria(title)) &&
                        seenTitles.add(normalized)
                } ?: continue

                val title = story.title.replace(whitespace, " ").trim().take(180)
                headlines.add(RssHeadline(feed.category, feed.source, title))
            } catch (_: Exception) {
                // A failed feed does not prevent weather or calendar data from being shown.
            }
        }
        return RssBrief(headlines, successfulFeeds)
    }

    private fun isImportantBulgaria(title: String): Boolean {
        val normalized = title.lowercase(Locale.ROOT)
        return importantBulgariaMarkers.any { marker -> normalized.contains(marker) } ||
            euOrNato.containsMatchIn(normalized)
    }

    private fun fetchText(url: String, timeoutMillis: Int): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = timeoutMillis
        connection.readTimeout = timeoutMillis
        connection.setRequestProperty("User-Agent", "DayHub/0.3 Android")
        return try {
            connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var totalBytes = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    totalBytes += count
                    if (totalBytes > MAX_RESPONSE_BYTES) {
                        throw IOException("RSS-ответ превышает лимит 1 МБ")
                    }
                    output.write(buffer, 0, count)
                }
                String(output.toByteArray(), Charsets.UTF_8)
            }
        } finally {
            connection.disconnect()
        }
    }
}
