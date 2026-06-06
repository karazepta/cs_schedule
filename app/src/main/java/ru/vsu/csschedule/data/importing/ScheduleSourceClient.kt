package ru.vsu.csschedule.data.importing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.io.File
import java.security.MessageDigest

data class DownloadedSheet(
    val bytes: ByteArray,
    val resolvedUrl: String,
    val extension: String,
)

class ScheduleSourceClient(
    private val okHttpClient: OkHttpClient,
) {
    suspend fun resolveSheetUrl(sourcePageUrl: String): String = withContext(Dispatchers.IO) {
        val html = fetchText(sourcePageUrl)
        val document = Jsoup.parse(html, sourcePageUrl)
        document
            .select("a[href*=docs.google.com/spreadsheets]")
            .firstOrNull()
            ?.absUrl("href")
            ?.takeIf { it.isNotBlank() }
            ?: error("Google Sheets link was not found on $sourcePageUrl")
    }

    suspend fun downloadSheet(sheetUrl: String): DownloadedSheet = withContext(Dispatchers.IO) {
        val candidateUrls = buildDownloadCandidates(sheetUrl)
        val failures = mutableListOf<String>()

        for (candidate in candidateUrls) {
            runCatching { fetchBytes(candidate) }
                .onSuccess { bytes ->
                    return@withContext DownloadedSheet(
                        bytes = bytes,
                        resolvedUrl = candidate,
                        extension = guessExtension(candidate),
                    )
                }
                .onFailure { failures += "${candidate}: ${it.message}" }
        }

        error(failures.joinToString(separator = "\n"))
    }

    fun calculateSha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString(separator = "") { "%02x".format(it) }
    }

    fun persistDownloadedSheet(targetFile: File, downloadedSheet: DownloadedSheet) {
        targetFile.parentFile?.mkdirs()
        targetFile.writeBytes(downloadedSheet.bytes)
    }

    private fun buildDownloadCandidates(sheetUrl: String): List<String> {
        val match = Regex("spreadsheets/d/([a-zA-Z0-9-_]+)").find(sheetUrl) ?: return listOf(sheetUrl)
        val spreadsheetId = match.groupValues[1]
        val gid = Regex("[?&]gid=([0-9]+)").find(sheetUrl)?.groupValues?.get(1)

        val xlsx = buildString {
            append("https://docs.google.com/spreadsheets/d/")
            append(spreadsheetId)
            append("/export?format=xlsx")
            if (gid != null) {
                append("&gid=")
                append(gid)
            }
        }

        val csv = buildString {
            append("https://docs.google.com/spreadsheets/d/")
            append(spreadsheetId)
            append("/export?format=csv")
            if (gid != null) {
                append("&gid=")
                append(gid)
            }
        }

        return listOf(xlsx, csv, sheetUrl).distinct()
    }

    private fun guessExtension(url: String): String = when {
        "format=xlsx" in url -> "xlsx"
        "format=csv" in url -> "csv"
        else -> "bin"
    }

    private fun fetchText(url: String): String = executeRequest(url).body?.string()
        ?: error("Empty response body from $url")

    private fun fetchBytes(url: String): ByteArray = executeRequest(url).body?.bytes()
        ?: error("Empty response body from $url")

    private fun executeRequest(url: String) = okHttpClient.newCall(
        Request.Builder()
            .url(url)
            .header("User-Agent", "CSSchedule/1.0")
            .build()
    ).execute().also { response ->
        if (!response.isSuccessful) {
            response.close()
            error("HTTP ${response.code} while loading $url")
        }
    }
}
