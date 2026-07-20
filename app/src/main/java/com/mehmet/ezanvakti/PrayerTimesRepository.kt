package com.mehmet.ezanvakti

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class PrayerTimesResult(
    val times: List<String>,
    val date: String
)

object PrayerTimesRepository {

    suspend fun fetchTimes(lat: Double, lng: Double): Result<PrayerTimesResult> =
        withContext(Dispatchers.IO) {
            try {
                val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                val tzOffsetMinutes = TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60000

                val url = "https://api.aladhan.com/v1/timings/$today" +
                        "?latitude=$lat&longitude=$lng&method=13&timezonestring=Europe/Istanbul"

                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                connection.setRequestProperty("Accept", "application/json")

                val responseCode = connection.responseCode
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    return@withContext Result.failure(Exception("Sunucu hatası: $responseCode"))
                }

                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                val data = json.getJSONObject("data")
                val timings = data.getJSONObject("timings")

                val times = listOf(
                    timings.getString("Fajr"),
                    timings.getString("Sunrise"),
                    timings.getString("Dhuhr"),
                    timings.getString("Asr"),
                    timings.getString("Maghrib"),
                    timings.getString("Isha")
                ).map { it.substring(0, 5) }

                val date = data.getJSONObject("date").getString("readable")

                Result.success(PrayerTimesResult(times, date))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}
