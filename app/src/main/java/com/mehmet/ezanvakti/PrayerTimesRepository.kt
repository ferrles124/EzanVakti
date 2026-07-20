package com.mehmet.ezanvakti

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class PrayerTimesResult(
    val rawJson: String,
    val times: List<String>,
    val date: String
)

private val TIME_REGEX = Regex("""\b([01][0-9]|2[0-3]):[0-5][0-9]\b""")
private val DATE_REGEX = Regex("""\b(20\d{2}-\d{2}-\d{2})\b""")

object PrayerTimesRepository {

    suspend fun fetchTimes(lat: Double, lng: Double): Result<PrayerTimesResult> =
        withContext(Dispatchers.IO) {
            try {
                val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                val tzOffsetMinutes = TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60000

                val url = "https://vakit.vercel.app/api/timesForGPS" +
                        "?lat=$lat&lng=$lng&date=$today&days=1" +
                        "&timezoneOffset=$tzOffsetMinutes&calculationMethod=Turkey"

                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 10000
                connection.readTimeout = 10000

                val responseCode = connection.responseCode
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    return@withContext Result.failure(Exception("Sunucu hatası: $responseCode"))
                }

                val body = connection.inputStream.bufferedReader().use { it.readText() }

                // API'nin tam alan isimlerini henüz doğrulayamadık; JSON içindeki
                // tüm SS:DD formatlı zamanları sırayla çekiyoruz. Diyanet'in
                // standart sırası: İmsak, Güneş, Öğle, İkindi, Akşam, Yatsı.
                val allTimes = TIME_REGEX.findAll(body).map { it.value }.toList()
                val foundDate = DATE_REGEX.find(body)?.value ?: today

                if (allTimes.size < 6) {
                    return@withContext Result.failure(
                        Exception("Vakit verisi ayrıştırılamadı. Ham veri: ${body.take(500)}")
                    )
                }

                Result.success(PrayerTimesResult(body, allTimes.take(6), foundDate))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}
