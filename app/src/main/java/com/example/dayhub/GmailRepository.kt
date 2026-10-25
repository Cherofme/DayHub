package com.example.dayhub

import android.text.Html
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

internal data class GmailEmail(
    val id: String,
    val threadId: String,
    val from: String,
    val subject: String,
    val receivedAtMillis: Long,
    val snippet: String,
    val labels: Set<String>,
    var body: String = "",
    var aiTitle: String = "",
    var aiSummary: String = "",
    var aiImportant: Boolean = false,
    var aiReason: String = ""
)

internal data class GmailTodayResult(val totalEstimate: Int, val emails: List<GmailEmail>)

internal object GmailRepository {
    private const val API_ROOT = "https://gmail.googleapis.com/gmail/v1/users/me"
    private const val MAX_RESPONSE_BYTES = 2 * 1024 * 1024
    private const val TIMEOUT_MILLIS = 15_000

    fun fetchToday(accessToken: String, startOfLocalDayMillis: Long, limit: Int = 40): GmailTodayResult {
        require(accessToken.isNotBlank()) { "Требуется токен Google." }
        val query = URLEncoder.encode("after:${startOfLocalDayMillis / 1000} -from:me", "UTF-8")
        val listed = requestJson("$API_ROOT/messages?maxResults=${limit.coerceIn(1, 100)}&q=$query", accessToken)
        val ids = listed.optJSONArray("messages") ?: JSONArray()
        val emails = ArrayList<GmailEmail>(ids.length())
        for (index in 0 until ids.length()) {
            val id = ids.optJSONObject(index)?.optString("id").orEmpty()
            if (id.isBlank()) continue
            val encodedId = URLEncoder.encode(id, "UTF-8")
            val detail = requestJson(
                "$API_ROOT/messages/$encodedId?format=metadata" +
                    "&metadataHeaders=From&metadataHeaders=Subject&metadataHeaders=Date",
                accessToken
            )
            val payload = detail.optJSONObject("payload")
            val headers = payload?.optJSONArray("headers") ?: JSONArray()
            val labels = detail.optJSONArray("labelIds") ?: JSONArray()
            emails += GmailEmail(
                id = id,
                threadId = detail.optString("threadId", ""),
                from = headerValue(headers, "From"),
                subject = headerValue(headers, "Subject"),
                receivedAtMillis = detail.optString("internalDate").toLongOrNull() ?: 0L,
                snippet = detail.optString("snippet", ""),
                labels = (0 until labels.length()).mapNotNull { labels.optString(it).takeIf { label -> label.isNotBlank() } }.toSet()
            )
        }
        return GmailTodayResult(listed.optInt("resultSizeEstimate", emails.size), emails)
    }

    fun fetchBody(accessToken: String, id: String): String {
        require(accessToken.isNotBlank()) { "Требуется токен Google." }
        val encodedId = URLEncoder.encode(id, "UTF-8")
        val detail = requestJson("$API_ROOT/messages/$encodedId?format=full", accessToken)
        val payload = detail.optJSONObject("payload") ?: return detail.optString("snippet", "")
        val plain = mutableListOf<String>()
        collectBodies(payload, "text/plain", plain)
        val content = plain.firstOrNull { it.isNotBlank() } ?: run {
            val html = mutableListOf<String>()
            collectBodies(payload, "text/html", html)
            html.firstOrNull().orEmpty().let { Html.fromHtml(it, Html.FROM_HTML_MODE_COMPACT).toString() }
        }
        return content.replace('\u0000', ' ').trim().take(60_000)
            .ifBlank { detail.optString("snippet", "Текст письма пуст или находится во вложении.") }
    }

    private fun collectBodies(part: JSONObject, wantedMime: String, output: MutableList<String>) {
        if (part.optString("mimeType").equals(wantedMime, ignoreCase = true) &&
            part.optString("filename").isBlank()) {
            val encoded = part.optJSONObject("body")?.optString("data").orEmpty()
            if (encoded.isNotBlank()) {
                try {
                    output += String(Base64.decode(encoded, Base64.URL_SAFE or Base64.NO_WRAP), Charsets.UTF_8)
                } catch (_: IllegalArgumentException) { }
            }
        }
        val children = part.optJSONArray("parts") ?: return
        for (index in 0 until children.length()) {
            children.optJSONObject(index)?.let { collectBodies(it, wantedMime, output) }
        }
    }

    private fun headerValue(headers: JSONArray, requestedName: String): String {
        for (index in 0 until headers.length()) {
            val header = headers.optJSONObject(index) ?: continue
            if (header.optString("name").equals(requestedName, ignoreCase = true)) {
                return header.optString("value", "")
            }
        }
        return ""
    }

    private fun requestJson(url: String, accessToken: String): JSONObject {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = TIMEOUT_MILLIS
        connection.readTimeout = TIMEOUT_MILLIS
        connection.useCaches = false
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("Authorization", "Bearer $accessToken")
        return try {
            val status = connection.responseCode
            if (status !in 200..299) {
                val error = connection.errorStream?.bufferedReader()?.use { it.readText().take(500) }.orEmpty()
                throw IOException("Gmail API вернул HTTP $status${if (error.isBlank()) "" else ": $error"}")
            }
            val body = connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > MAX_RESPONSE_BYTES) throw IOException("Письмо слишком большое для отображения")
                    output.write(buffer, 0, count)
                }
                String(output.toByteArray(), Charsets.UTF_8)
            }
            JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }
}
