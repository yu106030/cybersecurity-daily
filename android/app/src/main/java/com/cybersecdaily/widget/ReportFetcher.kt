package com.cybersecdaily.widget

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit

object ReportFetcher {

    private const val BASE_URL = "https://unclecheng-li.github.io/cybersecurity-daily"
    private const val INDEX_URL = "$BASE_URL/index.html"
    private const val DAILY_URL = "$BASE_URL/daily"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /**
     * Fetch the latest daily report data.
     *
     * Strategy:
     * 1. Try fetching index.html → parse the ARCHIVE_DATA JS blob to find latest date.
     * 2. If that fails, guess today's date and try to fetch directly.
     * 3. Parse the daily HTML to extract headlines, keywords, etc.
     */
    suspend fun fetchLatest(): DailyReport = withContext(Dispatchers.IO) {
        try {
            val latestDate = fetchLatestDate()
            val dailyHtml = fetchDailyPage(latestDate)
            parseDailyHtml(dailyHtml, latestDate)
        } catch (e: Exception) {
            DailyReport.error("获取失败：${e.localizedMessage ?: "未知错误"}")
        }
    }

    // ---------- Step 1: find latest date ----------

    private fun fetchLatestDate(): String {
        val request = Request.Builder().url(INDEX_URL).build()
        val body = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("index request failed (${response.code})")
            response.body?.string() ?: throw Exception("empty index page")
        }

        // Try regex: ARCHIVE_DATA[0].date = "YYYY-MM-DD"
        val regex = Regex(""""date"\s*:\s*"(\d{4}-\d{2}-\d{2})"""")
        val match = regex.find(body)
        if (match != null) {
            return match.groupValues[1]
        }

        // Fallback: find any date pattern in ARCHIVE_DATA
        val fallback = Regex("""(\d{4}-\d{2}-\d{2})""").find(body)
        if (fallback != null) {
            return fallback.groupValues[1]
        }

        throw Exception("无法解析最新日期")
    }

    // ---------- Step 2: fetch daily page ----------

    private fun fetchDailyPage(date: String): String {
        val url = "$DAILY_URL/$date.html"
        val request = Request.Builder().url(url).build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("日报不存在 ($date)")
            response.body?.string() ?: throw Exception("empty daily page")
        }
    }

    // ---------- Step 3: parse HTML ----------

    private fun parseDailyHtml(html: String, date: String): DailyReport {
        val doc = Jsoup.parse(html)

        // Keywords banner
        val keywords = doc.selectFirst(".keywords-banner")
            ?.ownText()
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?: ""

        // Date CN from .edition-date
        val dateCN = doc.selectFirst(".edition-date")?.text()?.trim() ?: date

        // Edition number
        val editionNumber = doc.selectFirst(".edition-number")?.text()?.trim() ?: ""

        // Main headline from .headline-section .main-headline
        val mainHeadline = doc.selectFirst(".headline-section .main-headline")
            ?.text()
            ?.trim()
            ?: ""

        // Article headlines from .articles-grid .article-card
        val headlines = doc.select(".articles-grid .article-card .article-title")
            .map { it.text().trim() }
            .filter { it.isNotEmpty() }
            .take(5)

        // Quick news items
        val quickNews = doc.select(".quick-news .quick-item")
            .map { it.text().trim() }
            .filter { it.isNotEmpty() }

        return DailyReport(
            date = date,
            dateCN = dateCN,
            editionNumber = editionNumber,
            keywords = keywords,
            mainHeadline = mainHeadline,
            headlines = headlines + quickNews,
            quickNews = quickNews
        )
    }
}
