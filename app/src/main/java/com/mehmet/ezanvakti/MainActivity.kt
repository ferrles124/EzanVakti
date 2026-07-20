package com.mehmet.ezanvakti

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

private val VAKIT_ADLARI = listOf("İmsak", "Güneş", "Öğle", "İkindi", "Akşam", "Yatsı")

class MainActivity : ComponentActivity() {

    private var onPermissionResult: ((Boolean) -> Unit)? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        onPermissionResult?.invoke(granted)
        onPermissionResult = null
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        // Bildirim izni sonucu
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createNotificationChannel()
        requestNotificationPermission()
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    EzanVaktiScreen(
                        hasLocationPermission = { hasLocationPermission() },
                        requestLocationPermission = { cb -> requestLocationPermission(cb) }
                    )
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "prayer_channel",
                "Ezan Vakitleri",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Ezan vakitleri geldiğinde bildirim gönderir"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermission(callback: (Boolean) -> Unit) {
        onPermissionResult = callback
        permissionLauncher.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        )
    }
}

@Composable
fun EzanVaktiScreen(
    hasLocationPermission: () -> Boolean,
    requestLocationPermission: ((Boolean) -> Unit) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isLoading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var times by remember { mutableStateOf<List<String>?>(null) }
    var date by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }
    var notificationEnabled by remember { mutableStateOf(true) }

    fun scheduleNotifications(times: List<String>) {
        val prayerTimes = listOf(
            "İmsak" to times[0],
            "Güneş" to times[1],
            "Öğle" to times[2],
            "İkindi" to times[3],
            "Akşam" to times[4],
            "Yatsı" to times[5]
        )
        
        prayerTimes.forEach { (name, time) ->
            val (hour, minute) = time.split(":").map { it.toInt() }
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
            }
            
            if (calendar.timeInMillis <= System.currentTimeMillis()) {
                calendar.add(Calendar.DAY_OF_YEAR, 1)
            }
            
            PrayerNotificationService.scheduleNotification(
                context,
                name,
                "$name vakti girdi",
                calendar.timeInMillis
            )
        }
    }

    fun loadTimes() {
        if (isLoading) return
        scope.launch {
            isLoading = true
            status = "Konum alınıyor..."
            errorText = null
            try {
                val location = LocationHelper.getCurrentLocation(context)
                if (location == null) {
                    status = "Konum alınamadı"
                    isLoading = false
                    return@launch
                }
                status = "Vakitler getiriliyor..."
                val result = PrayerTimesRepository.fetchTimes(location.first, location.second)
                result.onSuccess {
                    times = it.times
                    date = it.date
                    status = "Tamam"
                    
                    if (notificationEnabled) {
                        scheduleNotifications(it.times)
                    }
                }.onFailure {
                    status = "Hata"
                    errorText = it.message
                }
            } catch (e: Exception) {
                status = "Hata"
                errorText = e.message
            }
            isLoading = false
        }
    }

    fun start() {
        if (hasLocationPermission()) {
            loadTimes()
        } else {
            requestLocationPermission { granted ->
                if (granted) {
                    loadTimes()
                } else {
                    status = "Konum izni verilmedi"
                    errorText = "Lütfen ayarlardan konum iznini etkinleştirin"
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        start()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))
        Text(
            "Ezan Vakti",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (status.isNotEmpty()) {
                Text(status)
            }
            Row {
                Text("Bildirim", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.width(4.dp))
                Switch(
                    checked = notificationEnabled,
                    onCheckedChange = { 
                        notificationEnabled = it
                        if (it && times != null) {
                            scheduleNotifications(times!!)
                        }
                    }
                )
            }
        }

        if (date.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text(date, style = MaterialTheme.typography.titleMedium)
        }

        times?.let { list ->
            Spacer(Modifier.height(16.dp))
            list.forEachIndexed { index, time ->
                val label = VAKIT_ADLARI.getOrElse(index) { "Vakit ${index + 1}" }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(label, style = MaterialTheme.typography.bodyLarge)
                    Text(time, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                }
            }
        }

        errorText?.let {
            Spacer(Modifier.height(16.dp))
            Text("Hata: $it", color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { start() },
            enabled = !isLoading
        ) {
            Text(if (isLoading) "Yükleniyor..." else "Yenile")
        }
    }
}
