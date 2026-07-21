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
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.mehmet.ezanvakti.ui.VakitIkonlari
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*

private val VAKIT_ADLARI = listOf("İmsak", "Güneş", "Öğle", "İkindi", "Akşam", "Yatsı")
private val VAKIT_IKONLAR = listOf(
    R.drawable.ic_imsak,
    R.drawable.ic_gunes,
    R.drawable.ic_ogle,
    R.drawable.ic_ikindi,
    R.drawable.ic_aksam,
    R.drawable.ic_yatsi
)

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
    ) { granted -> }

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
                setShowBadge(true)
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

@OptIn(ExperimentalAnimationApi::class)
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
    var currentPrayerIndex by remember { mutableStateOf(-1) }
    var nextPrayerIndex by remember { mutableStateOf(-1) }
    var remainingTime by remember { mutableStateOf("") }

    val gradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF0D2B1F),
            Color(0xFF1A4D3E),
            Color(0xFF0D2B1F)
        )
    )

    fun calculateRemainingTime(times: List<String>, nextIndex: Int): String {
        if (nextIndex < 0 || nextIndex >= times.size) return ""
        
        val now = Calendar.getInstance()
        val currentTime = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        
        val (h, m) = times[nextIndex].split(":").map { it.toInt() }
        val prayerTime = h * 60 + m
        
        var diff = prayerTime - currentTime
        if (diff < 0) diff += 1440
        
        val hours = diff / 60
        val minutes = diff % 60
        
        return if (hours > 0) "${hours}s ${minutes}dk" else "${minutes}dk"
    }

    fun findCurrentPrayer(times: List<String>): Int {
        val now = Calendar.getInstance()
        val currentTime = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        
        val timesInMinutes = times.map { time ->
            val (h, m) = time.split(":").map { it.toInt() }
            h * 60 + m
        }
        
        var index = -1
        for (i in timesInMinutes.indices) {
            if (timesInMinutes[i] <= currentTime) {
                index = i
            }
        }
        return if (index >= 0 && index < timesInMinutes.size - 1) index else -1
    }

    fun updateOngoingNotification(times: List<String>, currentIndex: Int, nextIndex: Int) {
        if (currentIndex >= 0 && nextIndex >= 0) {
            val remaining = calculateRemainingTime(times, nextIndex)
            PrayerNotificationService.updateOngoingNotification(
                context,
                VAKIT_ADLARI[currentIndex],
                times[currentIndex],
                VAKIT_ADLARI[nextIndex],
                times[nextIndex],
                remaining
            )
        }
    }

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
                    currentPrayerIndex = findCurrentPrayer(it.times)
                    nextPrayerIndex = if (currentPrayerIndex >= 0 && currentPrayerIndex < it.times.size - 1) 
                        currentPrayerIndex + 1 
                    else -1
                    
                    if (nextPrayerIndex >= 0) {
                        remainingTime = calculateRemainingTime(it.times, nextPrayerIndex)
                    }
                    
                    if (notificationEnabled) {
                        scheduleNotifications(it.times)
                        if (currentPrayerIndex >= 0 && nextPrayerIndex >= 0) {
                            updateOngoingNotification(it.times, currentPrayerIndex, nextPrayerIndex)
                        }
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
        while (true) {
            delay(30000)
            if (times != null && notificationEnabled) {
                val newCurrent = findCurrentPrayer(times!!)
                if (newCurrent != currentPrayerIndex) {
                    currentPrayerIndex = newCurrent
                    nextPrayerIndex = if (currentPrayerIndex >= 0 && currentPrayerIndex < times!!.size - 1) 
                        currentPrayerIndex + 1 
                    else -1
                    if (nextPrayerIndex >= 0) {
                        remainingTime = calculateRemainingTime(times!!, nextPrayerIndex)
                    }
                    if (currentPrayerIndex >= 0 && nextPrayerIndex >= 0) {
                        updateOngoingNotification(times!!, currentPrayerIndex, nextPrayerIndex)
                    }
                } else if (nextPrayerIndex >= 0) {
                    remainingTime = calculateRemainingTime(times!!, nextPrayerIndex)
                    updateOngoingNotification(times!!, currentPrayerIndex, nextPrayerIndex)
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradient)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = ImageVector.vectorResource(id = R.drawable.ic_mosque),
                        contentDescription = null,
                        tint = Color(0xFFD4AF37),
                        modifier = Modifier.size(32.dp)
                    )
                    Text(
                        "Ezan Vakti",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFD4AF37)
                        )
                    )
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = ImageVector.vectorResource(id = R.drawable.ic_notification),
                        contentDescription = null,
                        tint = if (notificationEnabled) Color(0xFFD4AF37) else Color.Gray
                    )
                    Spacer(Modifier.width(4.dp))
                    Switch(
                        checked = notificationEnabled,
                        onCheckedChange = {
                            notificationEnabled = it
                            if (it && times != null) {
                                scheduleNotifications(times!!)
                                if (currentPrayerIndex >= 0 && nextPrayerIndex >= 0) {
                                    updateOngoingNotification(times!!, currentPrayerIndex, nextPrayerIndex)
                                }
                            } else {
                                PrayerNotificationService.removeOngoingNotification(context)
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFFD4AF37),
                            checkedTrackColor = Color(0xFF1A4D3E)
                        )
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            
            if (date.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF1E5A45).copy(alpha = 0.6f)
                    )
                ) {
                    Text(
                        text = date,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        color = Color(0xFFF5E6A3),
                        fontSize = 16.sp
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            if (nextPrayerIndex >= 0 && times != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFD4AF37).copy(alpha = 0.2f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = ImageVector.vectorResource(id = R.drawable.ic_hourglass),
                                    contentDescription = null,
                                    tint = Color(0xFFD4AF37),
                                    modifier = Modifier.size(24.dp)
                                )
                                Column {
                                    Text(
                                        "Sonraki Vakit",
                                        color = Color(0xFFD4AF37),
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        VAKIT_ADLARI[nextPrayerIndex],
                                        color = Color.White,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Text(
                                times!![nextPrayerIndex],
                                color = Color(0xFFD4AF37),
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (remainingTime.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Kalan süre: $remainingTime",
                                color = Color(0xFFF5E6A3),
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            times?.let { list ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 500.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(list.indices.toList()) { index ->
                        val isCurrent = index == currentPrayerIndex
                        val isNext = index == nextPrayerIndex
                        
                        AnimatedContent(
                            targetState = isCurrent,
                            transitionSpec = {
                                fadeIn() + slideInVertically() togetherWith 
                                fadeOut() + slideOutVertically()
                            }
                        ) { current ->
                            PrayerCard(
                                name = VAKIT_ADLARI[index],
                                time = list[index],
                                iconRes = VAKIT_IKONLAR[index],
                                isCurrent = current,
                                isNext = isNext
                            )
                        }
                    }
                }
            }

            errorText?.let {
                Spacer(Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFB71C1C).copy(alpha = 0.8f)
                    )
                ) {
                    Text(
                        it,
                        modifier = Modifier.padding(12.dp),
                        color = Color.White
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = { start() },
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(16.dp)),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFD4AF37),
                    disabledContainerColor = Color(0xFF1A4D3E)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color(0xFF0D2B1F)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Yükleniyor...",
                        color = Color(0xFF0D2B1F),
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = null,
                        tint = Color(0xFF0D2B1F)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Yenile",
                        color = Color(0xFF0D2B1F),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun PrayerCard(
    name: String,
    time: String,
    iconRes: Int,
    isCurrent: Boolean,
    isNext: Boolean
) {
    val cardColor = when {
        isCurrent -> Color(0xFFD4AF37).copy(alpha = 0.3f)
        isNext -> Color(0xFF1E5A45).copy(alpha = 0.6f)
        else -> Color(0xFF1E5A45).copy(alpha = 0.3f)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = cardColor
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (isCurrent) {
                    Icon(
                        imageVector = ImageVector.vectorResource(id = R.drawable.ic_current),
                        contentDescription = null,
                        tint = Color(0xFFD4AF37),
                        modifier = Modifier.size(12.dp)
                    )
                }
                
                Icon(
                    imageVector = ImageVector.vectorResource(id = iconRes),
                    contentDescription = null,
                    tint = if (isCurrent) Color(0xFFD4AF37) else Color(0xFFF5E6A3),
                    modifier = Modifier.size(24.dp)
                )
                
                Column {
                    Text(
                        name,
                        color = if (isCurrent) Color(0xFFD4AF37) else Color.White,
                        fontSize = 18.sp,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                    )
                    if (isCurrent) {
                        Text(
                            "Şu an",
                            color = Color(0xFFD4AF37),
                            fontSize = 10.sp
                        )
                    } else if (isNext) {
                        Text(
                            "Sıradaki",
                            color = Color(0xFFF5E6A3),
                            fontSize = 10.sp
                        )
                    }
                }
            }
            
            Text(
                time,
                color = if (isCurrent) Color(0xFFD4AF37) else Color.White,
                fontSize = 20.sp,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}
