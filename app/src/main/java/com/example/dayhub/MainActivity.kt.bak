package com.example.dayhub

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.provider.CalendarContract
import android.text.InputType
import android.view.View
import android.widget.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.xml.parsers.DocumentBuilderFactory

class MainActivity : Activity() {
    private lateinit var root: LinearLayout
    private val ink = Color.rgb(24, 39, 62)
    private val blue = Color.rgb(29, 95, 175)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showHome()
    }
    private fun text(value: String, size: Float = 16f): TextView = TextView(this).apply {
        text = value; textSize = size; setTextColor(ink); setPadding(4, 9, 4, 9)
    }
    private fun page(title: String) {
        val scroll = ScrollView(this)
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 30, 24, 32); setBackgroundColor(Color.rgb(246, 249, 253)) }
        scroll.addView(root); setContentView(scroll)
        root.addView(text(title, 27f))
    }
    private fun button(label: String, action: () -> Unit) {
        root.addView(Button(this).apply { text = label; isAllCaps = false; setOnClickListener { action() } })
    }
    private fun showHome() {
        page("DayHub · Мой день")
        root.addView(text("Персональный центр · прототип 0.1", 13f))
        button("✉ Почта · подключение впереди") { placeholder("Gmail", "Для Gmail нужен OAuth и отдельная интеграция. Письма не читаются и не отправляются.") }
        button("♫ YouTube Music") { music() }
        button("☁ Погода по геолокации") { weather() }
        button("▦ Календарь") { calendar() }
        button("➤ Telegram · подключение впереди") { placeholder("Telegram", "Полная история чатов потребует TDLib и авторизации аккаунта. Пока сообщения не читаются.") }
        button("▤ Новости · 4 категории") { news() }
        root.addView(text("Без рекламных SDK и без отправки личных данных на собственный сервер.", 12f))
    }
    private fun placeholder(title: String, message: String) { page(title); root.addView(text(message)); button("← Назад") { showHome() } }
    private fun music() {
        page("YouTube Music")
        root.addView(text("Вставь ссылку на свой плейлист. Она сохранится только на этом телефоне."))
        val edit = EditText(this).apply { setSingleLine(); inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI; setText(getPreferences(MODE_PRIVATE).getString("playlist", "https://music.youtube.com/")) }
        root.addView(edit)
        button("Сохранить и открыть") {
            val url = edit.text.toString().trim()
            if (!url.startsWith("https://music.youtube.com/") && !url.startsWith("https://www.youtube.com/")) {
                Toast.makeText(this, "Нужна ссылка YouTube Music / YouTube", Toast.LENGTH_LONG).show()
            } else {
                getPreferences(MODE_PRIVATE).edit().putString("playlist", url).apply()
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
        }
        root.addView(text("Автовоспроизведение зависит от приложения YouTube Music.", 13f))
        button("← Назад") { showHome() }
    }
    private fun weather() {
        page("Погода")
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            root.addView(text("Разреши доступ к примерному местоположению. Точные координаты не нужны."))
            button("Разрешить геолокацию") { requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), 101) }
            button("← Назад") { showHome() }
            return
        }
        val output = text("Получаю координаты и прогноз…")
        root.addView(output)
        button("← Назад") { showHome() }
        val manager = getSystemService(LOCATION_SERVICE) as LocationManager
        val location = try { manager.getProviders(true).mapNotNull { manager.getLastKnownLocation(it) }.maxByOrNull { it.time } } catch (_: SecurityException) { null }
        if (location == null) { output.text = "Местоположение ещё не определено. Включи геолокацию, открой карты и повтори попытку."; return }
        val lat = location.latitude; val lon = location.longitude
        Thread {
            try {
                val json = JSONObject(fetch("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,wind_speed_10m&hourly=temperature_2m,precipitation_probability,precipitation&forecast_days=2&timezone=auto"))
                val current = json.getJSONObject("current"); val hourly = json.getJSONObject("hourly")
                val times = hourly.getJSONArray("time"); val temps = hourly.getJSONArray("temperature_2m"); val rain = hourly.getJSONArray("precipitation_probability")
                val now = SimpleDateFormat("yyyy-MM-dd'T'HH", Locale.US).format(Date())
                val lines = mutableListOf("Сейчас: ${current.optDouble("temperature_2m")} °C · ветер ${current.optDouble("wind_speed_10m")} км/ч", "", "Ближайшие часы:")
                var count = 0
                for (i in 0 until times.length()) {
                    if (times.getString(i).take(13) >= now && count < 12) {
                        lines.add("${times.getString(i).takeLast(5)} · ${temps.optDouble(i)} °C · дождь ${rain.optInt(i)}%")
                        count++
                    }
                }
                runOnUiThread { output.text = lines.joinToString("\n") }
            } catch (e: Exception) { runOnUiThread { output.text = "Ошибка прогноза: ${e.message}" } }
        }.start()
    }
    private fun calendar() {
        page("Календарь · ближайшие 48 часов")
        if (checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            root.addView(text("Для чтения событий нужно разрешение календаря."))
            button("Разрешить календарь") { requestPermissions(arrayOf(Manifest.permission.READ_CALENDAR), 102) }
            button("← Назад") { showHome() }; return
        }
        val start = System.currentTimeMillis(); val end = start + 48L * 3600_000
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also { android.content.ContentUris.appendId(it, start); android.content.ContentUris.appendId(it, end) }.build()
        val result = mutableListOf<String>()
        try {
            contentResolver.query(uri, arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.END), null, null, CalendarContract.Instances.BEGIN + " ASC")?.use { cursor ->
                val fmt = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault())
                while (cursor.moveToNext() && result.size < 30) {
                    result.add("${fmt.format(Date(cursor.getLong(1)))} — ${cursor.getString(0) ?: "Без названия"}")
                }
            }
        } catch (e: Exception) { result.add("Ошибка календаря: ${e.message}") }
        root.addView(text(if (result.isEmpty()) "Событий нет или календарь не синхронизирован." else result.joinToString("\n")))
        button("← Назад") { showHome() }
    }
    private fun news() {
        page("Новости · RSS")
        root.addView(text("Ленты показывают публикации источников, не ИИ-проверку важности. Для фильтра за 24 часа используется дата RSS." , 13f))
        val feeds = listOf(
            "Мир" to "https://feeds.bbci.co.uk/news/world/rss.xml",
            "Украина" to "https://www.pravda.com.ua/rss/",
            "Болгария" to "https://www.bta.bg/en/rss/free",
            "ИИ" to "https://techcrunch.com/category/artificial-intelligence/feed/"
        )
        for ((category, url) in feeds) button(category) { loadFeed(category, url) }
        button("← Назад") { showHome() }
    }
    private fun loadFeed(category: String, url: String) {
        page("Новости · $category")
        val output = text("Загружаю…"); root.addView(output); button("← Категории") { news() }
        Thread {
            try {
                val xml = fetch(url)
                val factory = DocumentBuilderFactory.newInstance().apply {
                    setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                    setFeature("http://xml.org/sax/features/external-general-entities", false)
                    setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                }
                val doc = factory.newDocumentBuilder().parse(xml.byteInputStream())
                val items = doc.getElementsByTagName("item")
                val cutoff = System.currentTimeMillis() - 24L * 3600_000
                val stories = mutableListOf<Pair<String, String>>()
                val format = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US)
                for (i in 0 until items.length) {
                    val item = items.item(i)
                    fun field(name: String): String {
                        val children = item.childNodes
                        for (j in 0 until children.length) if (children.item(j).nodeName == name) return children.item(j).textContent.orEmpty()
                        return ""
                    }
                    val published = try { format.parse(field("pubDate"))?.time } catch (_: Exception) { null }
                    if (published != null && published >= cutoff) stories.add(field("title") to field("link"))
                    if (stories.size >= 12) break
                }
                runOnUiThread {
                    output.text = if (stories.isEmpty()) "За последние 24 часа публикаций с распознанной датой нет." else "Найдено: ${stories.size}"
                    for ((title, link) in stories) if (link.startsWith("https://")) button("• $title") { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link))) }
                }
            } catch (e: Exception) { runOnUiThread { output.text = "Лента недоступна: ${e.message}" } }
        }.start()
    }
    private fun fetch(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 12000; conn.readTimeout = 12000
        conn.setRequestProperty("User-Agent", "DayHub/0.1 Android")
        return try { conn.inputStream.bufferedReader().use { it.readText() } } finally { conn.disconnect() }
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) { 101 -> weather(); 102 -> calendar() }
    }
}
