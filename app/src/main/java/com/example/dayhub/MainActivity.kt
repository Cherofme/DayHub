package com.example.dayhub

import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.workDataOf
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.content.res.ColorStateList
import android.view.Gravity
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.provider.CalendarContract
import android.text.InputType
import android.view.View
import android.view.WindowInsets
import android.os.Build
import android.widget.*
import org.json.JSONObject
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class MainActivity : Activity() {
    companion object {
        private const val GMAIL_AUTH_REQUEST_CODE = 7411
        private const val GMAIL_READONLY_SCOPE = "https://www.googleapis.com/auth/gmail.readonly"
    }

    private data class LivePanel(
        val container: LinearLayout,
        val headline: TextView,
        val description: TextView
    )

    private lateinit var root: LinearLayout
    private var gmailAccessCallback: ((String?, String?) -> Unit)? = null
    private var gmailListStatus: TextView? = null
    private val ink = Color.rgb(236, 242, 252)
    private val muted = Color.rgb(158, 178, 205)
    private val surface = Color.rgb(24, 35, 54)
    private val accent = Color.rgb(117, 181, 255)
    private val clockChangeMarkers = listOf(
        "daylight saving",
        "standard time begins",
        "standard time ends",
        "summer time begins",
        "summer time ends",
        "clock change",
        "clocks change",
        "clocks go forward",
        "clocks go back",
        "перевод часов",
        "переход на летнее время",
        "переход на зимнее время",
        "летнее время начинается",
        "летнее время заканчивается",
        "зимнее время начинается",
        "зимнее время заканчивается"
    )
    private fun dp(n: Int): Int = (n * resources.displayMetrics.density).toInt()
    private fun bg(color: Int, radius: Int = 18): GradientDrawable =
        GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showHome()
        val briefPrefs = getSharedPreferences(BriefPreferences.FILE, MODE_PRIVATE)
        setMorningBriefEnabled(briefPrefs.getBoolean(BriefPreferences.ENABLED, true))
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 103)
        }
    }
    private fun text(value: String, size: Float = 16f): TextView = TextView(this).apply {
        text = value; textSize = size; setTextColor(if (size <= 14f) muted else ink)
        setPadding(dp(4), dp(8), dp(4), dp(8))
        setLineSpacing(dp(3).toFloat(), 1f)
    }
    private fun page(title: String) {
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(Color.rgb(12, 19, 32))
        }
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(26), dp(20), dp(36))
        }
        scroll.addView(root)
        if (Build.VERSION.SDK_INT >= 35) {
            scroll.setOnApplyWindowInsetsListener { view, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                insets
            }
        }
        window.statusBarColor = Color.rgb(12, 19, 32)
        window.navigationBarColor = Color.rgb(12, 19, 32)
        window.decorView.systemUiVisibility = 0
        setContentView(scroll)
        root.addView(text(title, 27f).apply {
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(2), dp(12), dp(2), dp(18))
        })
    }
    private fun button(label: String, action: () -> Unit) {
        val view = TextView(this).apply {
            text = label
            textSize = 16f
            setTextColor(ink)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(18), dp(18), dp(18))
            background = bg(surface)
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
        }
        root.addView(view, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(11)
        })
    }
    private fun panel(kicker: String, headline: String, description: String): LivePanel {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(15), dp(18), dp(15))
            background = bg(Color.rgb(30, 49, 75), 22)
        }
        card.addView(text(kicker.uppercase(Locale.getDefault()), 12f).apply { setTextColor(accent) })
        val headlineView = text(headline, 23f).apply { setTypeface(null, Typeface.BOLD) }
        val descriptionView = text(description, 14f)
        card.addView(headlineView)
        card.addView(descriptionView)
        root.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(18) })
        return LivePanel(card, headlineView, descriptionView)
    }

    private fun briefSettings() {
        page("Настройки автономности")

        val prefs = getSharedPreferences(BriefPreferences.FILE, MODE_PRIVATE)

        val toggle = android.widget.Switch(this).apply {
            text = "Утренняя сводка (08:00–12:00)"
            textSize = 16f
            setTextColor(ink)
            isChecked = prefs.getBoolean(BriefPreferences.ENABLED, true)

            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean(BriefPreferences.ENABLED, checked).apply()
                setMorningBriefEnabled(checked)
            }
        }

        root.addView(toggle)

        root.addView(
            text(
                "Сводка включает погоду, события и свежие заголовки новостей. " +
                "Android может задерживать фоновые задачи.",
                14f
            )
        )

        button("← Назад") { showHome() }
    }

    private fun setMorningBriefEnabled(enabled: Boolean) {
        val workManager = WorkManager.getInstance(this)
        if (!enabled) {
            workManager.cancelUniqueWork("dayhub_morning")
            return
        }
        workManager.enqueueUniquePeriodicWork(
            "dayhub_morning",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<DailyBriefWorker>(1, TimeUnit.HOURS).build()
        )
    }

    private fun showHome() {
        page("DayHub")
        panel("Персональный ассистент · 0.3.3", "Твой день, без лишнего шума",
            "Погода, календарь, новости и музыка. Автономная сводка готовится в фоне.")
        val gmailPanel = panel("GMAIL · СЕГОДНЯ", "Загружаю почту…", "Нажми для писем и ответов")
        gmailPanel.container.isClickable = true
        gmailPanel.container.isFocusable = true
        gmailPanel.container.setOnClickListener { gmail() }
        loadHomeGmailStats(gmailPanel)
        val weatherPanel = panel("ПОГОДА", "Загружаю…", "По последней геопозиции")
        weatherPanel.container.isClickable = true
        weatherPanel.container.isFocusable = true
        weatherPanel.container.setOnClickListener { weather() }
        loadHomeWeather(weatherPanel)
        val calendarPanel = panel("СЛЕДУЮЩЕЕ СОБЫТИЕ", "Загружаю…", "Календарь · ближайшие 48 часов")
        calendarPanel.container.isClickable = true
        calendarPanel.container.isFocusable = true
        calendarPanel.container.setOnClickListener { calendar() }
        loadHomeCalendar(calendarPanel)
        val newsPanel = panel("СВЕЖИЕ НОВОСТИ", "Загружаю…", "RSS · публикации за последние 24 часа")
        newsPanel.container.isClickable = true
        newsPanel.container.isFocusable = true
        newsPanel.container.setOnClickListener { news() }
        loadHomeNews(newsPanel)
        button("☀  Сформировать сводку сейчас") { runBriefNow() }
        button("⚙  Настройки автономности") { briefSettings() }
        root.addView(text("ТВОИ РАЗДЕЛЫ", 12f).apply {
            setTextColor(accent)
            setPadding(dp(4), dp(14), dp(4), dp(12))
        })
        button("☁  Погода · прогноз по часам") { weather() }
        button("▦  Календарь · ближайшие события") { calendar() }
        button("▤  Новости · Мир / Украина / Болгария / ИИ") { news() }
        button("♫  YouTube Music") { music() }
        button("✉  Gmail · письма за сегодня") { gmail() }
        button("➤  Telegram · скоро") { placeholder("Telegram", "Полная интеграция потребует TDLib и авторизации. Пока чаты не читаются.") }
        root.addView(text("Данные хранятся на устройстве. Отправка писем не реализована.", 12f))
    }

    private fun loadHomeWeather(panel: LivePanel) {
        val hasLocationPermission =
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasLocationPermission) {
            panel.headline.text = "Покажи погоду"
            panel.description.text = "Нажми и разреши примерное местоположение в разделе погоды."
            return
        }

        val manager = getSystemService(LOCATION_SERVICE) as LocationManager
        val location = try {
            manager.getProviders(true)
                .mapNotNull { manager.getLastKnownLocation(it) }
                .maxByOrNull { it.time }
        } catch (_: SecurityException) {
            null
        }
        if (location == null) {
            panel.headline.text = "Местоположение ещё не определено"
            panel.description.text = "Нажми, открой карты и затем обнови прогноз."
            return
        }

        Thread {
            try {
                val json = JSONObject(
                    fetch(
                        "https://api.open-meteo.com/v1/forecast?latitude=${location.latitude}" +
                            "&longitude=${location.longitude}&current=temperature_2m,wind_speed_10m&timezone=auto"
                    )
                )
                val current = json.getJSONObject("current")
                val temperature = String.format(
                    Locale.getDefault(),
                    "%.1f",
                    current.optDouble("temperature_2m")
                )
                val wind = String.format(
                    Locale.getDefault(),
                    "%.1f",
                    current.optDouble("wind_speed_10m")
                )
                val updatedAt = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                runOnUiThread {
                    panel.headline.text = "$temperature °C"
                    panel.description.text =
                        "Ветер $wind км/ч · по последней геопозиции · обновлено $updatedAt · нажми для прогноза"
                }
            } catch (_: Exception) {
                runOnUiThread {
                    panel.headline.text = "Погода сейчас недоступна"
                    panel.description.text = "Нажми, чтобы открыть прогноз и повторить загрузку."
                }
            }
        }.start()
    }

    private fun loadHomeCalendar(panel: LivePanel) {
        if (checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            panel.headline.text = "Разреши доступ к календарю"
            panel.description.text = "Нажми и включи доступ, чтобы увидеть следующее событие."
            return
        }

        val start = System.currentTimeMillis()
        val end = start + 48L * 3600_000
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            android.content.ContentUris.appendId(it, start)
            android.content.ContentUris.appendId(it, end)
        }.build()

        Thread {
            try {
                var nextEvent: Pair<Long, String>? = null
                contentResolver.query(
                    uri,
                    arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN),
                    null,
                    null,
                    CalendarContract.Instances.BEGIN + " ASC"
                )?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val title = cursor.getString(0) ?: "Без названия"
                        if (isClockChangeNotice(title)) continue
                        nextEvent = cursor.getLong(1) to title
                        break
                    }
                }
                runOnUiThread {
                    val event = nextEvent
                    if (event == null) {
                        panel.headline.text = "Планов пока нет"
                        panel.description.text = "Нет событий на ближайшие 48 часов."
                    } else {
                        panel.headline.text =
                            SimpleDateFormat("EEE, dd MMM · HH:mm", Locale.getDefault())
                                .format(Date(event.first))
                        panel.description.text = event.second
                    }
                }
            } catch (_: Exception) {
                runOnUiThread {
                    panel.headline.text = "Календарь сейчас недоступен"
                    panel.description.text = "Нажми, чтобы открыть календарь и повторить."
                }
            }
        }.start()
    }

    private fun loadHomeNews(panel: LivePanel) {
        Thread {
            try {
                val cutoff = System.currentTimeMillis() - 24L * 3600_000
                val brief = RssFeedRepository.fetchBrief(cutoff, timeoutMillis = 4000)
                val updatedAt = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                val sourceCount = "${brief.successfulFeeds}/${RssFeedRepository.feeds.size} лент"
                val headlines = brief.headlines.take(2)

                runOnUiThread {
                    if (headlines.isNotEmpty()) {
                        val first = headlines.first()
                        panel.headline.text = first.title.take(150)
                        val second = headlines.getOrNull(1)
                        val sourceLine =
                            "${first.category} · ${first.source} · обновлено в $updatedAt ($sourceCount)"
                        panel.description.text = if (second == null) {
                            sourceLine
                        } else {
                            "$sourceLine\nЕщё: ${second.category} · ${second.title.take(90)}"
                        }
                    } else if (brief.successfulFeeds > 0) {
                        panel.headline.text = "Свежих заголовков нет"
                        panel.description.text = "RSS обновлён в $updatedAt ($sourceCount)."
                    } else {
                        panel.headline.text = "Новости сейчас недоступны"
                        panel.description.text = "Нажми, чтобы открыть ленты и повторить загрузку."
                    }
                }
            } catch (_: Exception) {
                runOnUiThread {
                    panel.headline.text = "Новости сейчас недоступны"
                    panel.description.text = "Нажми, чтобы открыть ленты и повторить загрузку."
                }
            }
        }.start()
    }

    private fun isClockChangeNotice(title: String): Boolean {
        val normalized = title.lowercase(Locale.ROOT)
        return clockChangeMarkers.any { marker -> normalized.contains(marker) }
    }
    private fun runBriefNow() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 103)
            Toast.makeText(this, "Разреши уведомления и повтори", Toast.LENGTH_LONG).show()
            return
        }
        val request = OneTimeWorkRequestBuilder<DailyBriefWorker>()
            .setInputData(workDataOf("manual" to true))
            .build()
        WorkManager.getInstance(this).enqueue(request)
        Toast.makeText(this, "Сводка поставлена в очередь. Проверь уведомления.", Toast.LENGTH_LONG).show()
    }
    private fun placeholder(title: String, message: String) { page(title); root.addView(text(message)); button("← Назад") { showHome() } }
    private fun gmail() {
        page("Gmail · сегодня")
        root.addView(text("Письма за сегодня · важность определяет локальный AI. Текст и черновики не отправляются в облачную AI-службу.", 14f))
        val status = text("Запрашиваю доступ к Gmail…", 14f)
        gmailListStatus = status
        root.addView(status)
        button("↻  Обновить письма") { gmail() }
        button("←  Главная") { showHome() }
        requestGmailAccess { token, error ->
            if (gmailListStatus !== status) return@requestGmailAccess
            if (token == null) {
                status.text = error ?: "Не удалось подключиться к Gmail."
                return@requestGmailAccess
            }
            getSharedPreferences("dayhub_gmail", MODE_PRIVATE).edit().putBoolean("authorized", true).apply()
            status.text = "Загружаю письма за сегодня…"
            Thread {
                try {
                    val start = localDayStartMillis()
                    val inbox = GmailRepository.fetchToday(token, start)
                    val ai = OnDeviceEmailAi.summarize(inbox.emails) { message ->
                        runOnUiThread { if (gmailListStatus === status) status.text = message }
                    }
                    val important = inbox.emails.count { it.aiImportant }
                    getSharedPreferences("dayhub_gmail", MODE_PRIVATE).edit()
                        .putString("stats_date", todayKey())
                        .putLong("stats_updated", System.currentTimeMillis())
                        .putInt("stats_total", inbox.totalEstimate)
                        .putInt("stats_important", important)
                        .putInt("stats_scanned", inbox.emails.size)
                        .apply()
                    runOnUiThread {
                        if (gmailListStatus !== status) return@runOnUiThread
                        val coverage = if (inbox.totalEstimate > inbox.emails.size) " среди последних ${inbox.emails.size}" else ""
                        val aiNotice = if (!ai.available) "\n${ai.message}" else ""
                        status.text = "Сегодня: ${inbox.totalEstimate} новых · $important важных$coverage$aiNotice"
                        renderGmailCards(inbox.emails, token)
                    }
                } catch (error: Exception) {
                    runOnUiThread {
                        if (gmailListStatus === status) status.text = gmailErrorMessage(error)
                    }
                }
            }.start()
        }
    }

    private fun loadHomeGmailStats(card: LivePanel) {
        val prefs = getSharedPreferences("dayhub_gmail", MODE_PRIVATE)
        if (!prefs.getBoolean("authorized", false)) {
            card.headline.text = "Подключи Gmail"
            card.description.text = "Нажми, чтобы дать доступ и увидеть письма за сегодня."
            return
        }
        val today = todayKey()
        val statsDate = prefs.getString("stats_date", "")
        if (statsDate == today) {
            val total = prefs.getInt("stats_total", 0)
            val important = prefs.getInt("stats_important", 0)
            val scanned = prefs.getInt("stats_scanned", total)
            card.headline.text = "$total новых · $important важных"
            card.description.text = if (total > scanned) "Важность среди последних $scanned · нажми, чтобы открыть письма" else "Почта за сегодня · нажми, чтобы открыть письма"
            if (System.currentTimeMillis() - prefs.getLong("stats_updated", 0L) < 10 * 60_000L) return
        } else {
            card.headline.text = "Обновляю почту…"
            card.description.text = "Письма за сегодня · нажми для списка"
        }
        requestGmailAccess { token, error ->
            if (token == null) {
                if (card.container.isAttachedToWindow) {
                    card.headline.text = "Gmail недоступен"
                    card.description.text = error ?: "Нажми, чтобы повторить подключение."
                }
                return@requestGmailAccess
            }
            Thread {
                try {
                    val inbox = GmailRepository.fetchToday(token, localDayStartMillis())
                    val ai = OnDeviceEmailAi.summarize(inbox.emails) { }
                    val important = inbox.emails.count { it.aiImportant }
                    getSharedPreferences("dayhub_gmail", MODE_PRIVATE).edit()
                        .putString("stats_date", todayKey())
                        .putLong("stats_updated", System.currentTimeMillis())
                        .putInt("stats_total", inbox.totalEstimate)
                        .putInt("stats_important", important)
                        .putInt("stats_scanned", inbox.emails.size)
                        .apply()
                    runOnUiThread {
                        if (!card.container.isAttachedToWindow) return@runOnUiThread
                        card.headline.text = "${inbox.totalEstimate} новых · $important важных"
                        card.description.text = if (ai.available) "Почта за сегодня · нажми, чтобы открыть письма" else "AI временно недоступен · нажми, чтобы открыть письма"
                    }
                } catch (error: Exception) {
                    runOnUiThread {
                        if (card.container.isAttachedToWindow) {
                            card.headline.text = "Gmail недоступен"
                            card.description.text = gmailErrorMessage(error)
                        }
                    }
                }
            }.start()
        }
    }

    private fun requestGmailAccess(callback: (String?, String?) -> Unit) {
        gmailAccessCallback = callback
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(GMAIL_READONLY_SCOPE)))
            .build()

        Identity.getAuthorizationClient(this)
            .authorize(request)
            .addOnSuccessListener { result ->
                if (result.hasResolution()) {
                    val consentIntent = result.pendingIntent
                    if (consentIntent == null) {
                        gmailAccessCallback?.invoke(null, "Google не вернул запрос подтверждения доступа.")
                        gmailAccessCallback = null
                        return@addOnSuccessListener
                    }
                    try {
                        startIntentSenderForResult(
                            consentIntent.intentSender,
                            GMAIL_AUTH_REQUEST_CODE,
                            null,
                            0,
                            0,
                            0
                        )
                    } catch (_: IntentSender.SendIntentException) {
                        gmailAccessCallback?.invoke(null, "Не удалось открыть подтверждение доступа к Gmail.")
                        gmailAccessCallback = null
                    }
                } else {
                    gmailAccessCallback?.invoke(result.accessToken, null)
                    gmailAccessCallback = null
                }
            }
            .addOnFailureListener { error ->
                gmailAccessCallback?.invoke(null, gmailErrorMessage(error))
                gmailAccessCallback = null
            }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != GMAIL_AUTH_REQUEST_CODE) return

        if (resultCode != RESULT_OK || data == null) {
            gmailAccessCallback?.invoke(null, "Доступ к Gmail не предоставлен.")
            gmailAccessCallback = null
            return
        }

        try {
            val result = Identity.getAuthorizationClient(this).getAuthorizationResultFromIntent(data)
            gmailAccessCallback?.invoke(result.accessToken, null)
            gmailAccessCallback = null
        } catch (error: Exception) {
            gmailAccessCallback?.invoke(null, gmailErrorMessage(error))
            gmailAccessCallback = null
        }
    }

    private fun renderGmailCards(emails: List<GmailEmail>, accessToken: String) {
        val sorted = emails.sortedWith(compareByDescending<GmailEmail> { it.aiImportant }.thenByDescending { it.receivedAtMillis })
        if (sorted.isEmpty()) {
            root.addView(text("Сегодня новых входящих писем нет.", 17f))
            return
        }
        sorted.forEach { email ->
            val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(email.receivedAtMillis))
            val detail = buildString {
                append(email.from.ifBlank { "Неизвестный отправитель" })
                append("\nСегодня, $time · ")
                append(email.aiSummary.ifBlank { email.snippet.ifBlank { "Открой письмо, чтобы прочитать текст." } })
                if (email.aiImportant && email.aiReason.isNotBlank()) append("\nПочему важно: ${email.aiReason}")
            }
            val card = panel(if (email.aiImportant) "ВАЖНОЕ ПИСЬМО" else "ПИСЬМО", email.aiTitle.ifBlank { email.subject.ifBlank { "Без темы" } }, detail)
            card.container.isClickable = true
            card.container.isFocusable = true
            card.container.setOnClickListener { showGmailDetail(email, accessToken) }
        }
    }

    private fun showGmailDetail(email: GmailEmail, accessToken: String) {
        page(email.aiTitle.ifBlank { email.subject.ifBlank { "Письмо" } })
        val time = SimpleDateFormat("dd.MM.yyyy · HH:mm", Locale.getDefault()).format(Date(email.receivedAtMillis))
        root.addView(text("От: ${email.from}\nПолучено: $time", 14f))
        if (email.aiImportant) root.addView(text("ВАЖНО · ${email.aiReason}", 14f))
        val bodyView = text("Загружаю текст письма…", 15f)
        root.addView(bodyView)
        root.addView(text("ЧЕРНОВИК ОТВЕТА · ПРОВЕРЬ ПЕРЕД ОТПРАВКОЙ", 12f).apply { setTextColor(accent) })
        val draft = EditText(this).apply {
            minLines = 5
            gravity = Gravity.TOP
            textSize = 16f
            setTextColor(ink)
            setHintTextColor(muted)
            hint = "Черновик появится здесь. Его можно изменить."
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = bg(surface, 16)
        }
        root.addView(draft, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        val draftStatus = text("Черновик создаётся локально на телефоне…", 13f)
        root.addView(draftStatus)
        button("✦  Создать черновик ещё раз") {
            val body = email.body.ifBlank { bodyView.text.toString() }
            Thread {
                val result = OnDeviceEmailAi.draftReply(email, body)
                runOnUiThread {
                    if (draft.tag == email.id) {
                        if (result.available) draft.setText(result.message)
                        draftStatus.text = if (result.available) "Черновик готов. Проверь его перед отправкой." else result.message
                    }
                }
            }.start()
        }
        button("✉  Открыть черновик в Gmail") {
            val address = Regex("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", RegexOption.IGNORE_CASE)
                .find(email.from)?.value.orEmpty()
            if (address.isBlank()) {
                Toast.makeText(this, "Не удалось определить адрес отправителя.", Toast.LENGTH_LONG).show()
                return@button
            }
            val originalSubject = email.subject.ifBlank { email.aiTitle }
            val replySubject = if (originalSubject.startsWith("Re:", true)) originalSubject else "Re: $originalSubject"
            val compose = Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", address, null)).apply {
                putExtra(Intent.EXTRA_SUBJECT, replySubject)
                putExtra(Intent.EXTRA_TEXT, draft.text.toString())
            }
            try { startActivity(Intent.createChooser(compose, "Проверь и отправь черновик в Gmail")) }
            catch (_: Exception) { Toast.makeText(this, "Не удалось открыть приложение почты.", Toast.LENGTH_LONG).show() }
        }
        button("←  К списку писем") { gmail() }
        Thread {
            try {
                val body = GmailRepository.fetchBody(accessToken, email.id)
                email.body = body
                runOnUiThread {
                    if (draft.tag == email.id) bodyView.text = body
                }
                val result = OnDeviceEmailAi.draftReply(email, body)
                runOnUiThread {
                    if (draft.tag == email.id) {
                        if (result.available) draft.setText(result.message)
                        draftStatus.text = if (result.available) "Черновик готов. Проверь его перед отправкой." else result.message
                    }
                }
            } catch (error: Exception) {
                runOnUiThread {
                    if (draft.tag == email.id) {
                        bodyView.text = "Не удалось загрузить текст письма: ${gmailErrorMessage(error)}"
                        draftStatus.text = "Автоматический черновик пока не создан."
                    }
                }
            }
        }.start()
        draft.tag = email.id
    }

    private fun localDayStartMillis(): Long = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun todayKey(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private fun gmailErrorMessage(error: Throwable): String {
        val message = error.message.orEmpty()
        return when {
            message.contains("HTTP 403") -> "Google запретил доступ. Проверь Gmail API, тестового пользователя OAuth и разрешение gmail.readonly."
            message.contains("HTTP 401") -> "Доступ истёк. Нажми «Обновить письма» и подтверди Google ещё раз."
            else -> "Gmail сейчас недоступен: ${message.take(240).ifBlank { "проверь интернет и настройки OAuth" }}"
        }
    }

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
        for (feed in RssFeedRepository.feeds) {
            button(feed.category) { loadFeed(feed) }
        }
        button("← Назад") { showHome() }
    }
    private fun loadFeed(feed: RssFeed) {
        page("Новости · ${feed.category}")
        val output = text("Загружаю…"); root.addView(output); button("← Категории") { news() }
        Thread {
            try {
                val cutoff = System.currentTimeMillis() - 24L * 3600_000
                val stories = RssFeedRepository.recentStories(feed, cutoff)
                runOnUiThread {
                    output.text = if (stories.isEmpty()) "За последние 24 часа публикаций с распознанной датой нет." else "Найдено: ${stories.size}"
                    for (story in stories) if (story.link.startsWith("https://")) {
                        button("• ${story.title}") {
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(story.link)))
                        }
                    }
                }
            } catch (e: Exception) { runOnUiThread { output.text = "Лента недоступна: ${e.message}" } }
        }.start()
    }
    private fun fetch(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 12000; conn.readTimeout = 12000
        conn.setRequestProperty("User-Agent", "DayHub/0.1 Android")
        return try {
            conn.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var totalBytes = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    totalBytes += count
                    if (totalBytes > 1_048_576) {
                        throw java.io.IOException("Ответ сервера превышает лимит 1 МБ")
                    }
                    output.write(buffer, 0, count)
                }
                String(output.toByteArray(), Charsets.UTF_8)
            }
        } finally { conn.disconnect() }
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) { 101 -> weather(); 102 -> calendar() }
    }
}
