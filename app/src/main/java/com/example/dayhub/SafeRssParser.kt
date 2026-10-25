package com.example.dayhub

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Locale

internal data class RssStory(val title: String, val link: String)

internal object SafeRssParser {
    private const val MAX_XML_CHARS = 1_048_576
    private val forbiddenDeclarations =
        Regex("""<!\s*(DOCTYPE|ENTITY)\b""", RegexOption.IGNORE_CASE)

    fun recentStories(
        xml: String,
        cutoffMillis: Long,
        limit: Int = 12
    ): List<RssStory> {
        if (limit <= 0) return emptyList()
        if (xml.length > MAX_XML_CHARS) {
            throw SecurityException("RSS превышает лимит 1 МБ")
        }
        if (forbiddenDeclarations.containsMatchIn(xml)) {
            throw SecurityException("RSS содержит запрещённые XML DTD/ENTITY")
        }

        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
        if (parser.getFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL)) {
            throw SecurityException("Не удалось отключить обработку XML DTD")
        }
        parser.setInput(StringReader(xml))

        val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US)
        val stories = mutableListOf<RssStory>()
        var inItem = false
        var itemDepth = -1
        var currentField: String? = null
        val fieldText = StringBuilder()
        var title = ""
        var link = ""
        var pubDate = ""
        var event = parser.eventType

        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    if (!inItem && parser.name == "item") {
                        inItem = true
                        itemDepth = parser.depth
                        title = ""
                        link = ""
                        pubDate = ""
                    } else if (inItem &&
                        parser.depth == itemDepth + 1 &&
                        parser.name in setOf("title", "link", "pubDate")
                    ) {
                        currentField = parser.name
                        fieldText.setLength(0)
                    }
                }

                XmlPullParser.TEXT, XmlPullParser.CDSECT -> {
                    if (inItem && currentField != null) {
                        fieldText.append(parser.text.orEmpty())
                    }
                }

                XmlPullParser.ENTITY_REF -> {
                    if (inItem && currentField != null) {
                        fieldText.append(decodeEntityReference(parser.name.orEmpty()))
                    }
                }

                XmlPullParser.DOCDECL -> {
                    throw SecurityException("RSS содержит запрещённый XML DTD")
                }

                XmlPullParser.END_TAG -> {
                    if (inItem &&
                        currentField != null &&
                        parser.depth == itemDepth + 1 &&
                        parser.name == currentField
                    ) {
                        when (currentField) {
                            "title" -> title = fieldText.toString().trim()
                            "link" -> link = fieldText.toString().trim()
                            "pubDate" -> pubDate = fieldText.toString().trim()
                        }
                        currentField = null
                    }

                    if (inItem && parser.depth == itemDepth && parser.name == "item") {
                        val published = try {
                            dateFormat.parse(pubDate)?.time
                        } catch (_: Exception) {
                            null
                        }
                        if (published != null && published >= cutoffMillis) {
                            stories.add(RssStory(title, link))
                        }
                        inItem = false
                        if (stories.size >= limit) break
                    }
                }
            }
            event = parser.nextToken()
        }

        return stories
    }

    private fun decodeEntityReference(name: String): String {
        return when (name) {
            "amp" -> "&"
            "lt" -> "<"
            "gt" -> ">"
            "apos" -> "'"
            "quot" -> "\""
            else -> {
                if (!name.startsWith("#")) {
                    // DTDs are rejected, so unknown named entities cannot expand.
                    // Keep them literal to tolerate HTML entities in feed titles.
                    return "&$name;"
                }

                val codePoint = when {
                    name.startsWith("#x", ignoreCase = true) ->
                        name.substring(2).toIntOrNull(16)
                    else -> name.substring(1).toIntOrNull(10)
                } ?: throw SecurityException("RSS содержит некорректную числовую XML-сущность")

                val validXmlCharacter =
                    codePoint == 0x9 || codePoint == 0xA || codePoint == 0xD ||
                        codePoint in 0x20..0xD7FF ||
                        codePoint in 0xE000..0xFFFD ||
                        codePoint in 0x10000..0x10FFFF
                if (!validXmlCharacter) {
                    throw SecurityException("RSS содержит недопустимую числовую XML-сущность")
                }
                String(Character.toChars(codePoint))
            }
        }
    }
}
