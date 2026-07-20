package com.mehmet.ezanvakti

import android.Manifest
import android.content.pm.PackageManager
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

private val VAKIT_ADLARI = listOf("İmsak", "Güneş", "Öğle", "İkindi", "Akşam", "Yatsı")

class MainActivity : ComponentActivity() {

    private var onPermissionResult: ((Boolean) -> Unit)? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        onPermissionResult?.invoke(results.values.any { it })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
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

    var status by remember { mutableStateOf("Konum bekleniyor...") }
    var times by remember { mutableStateOf<List<String>?>(null) }
    var date by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    fun loadTimes() {
        scope.launch {
            status = "Konum alınıyor..."
            errorText = null
            try {
                val location = LocationHelper.getCurrentLocation(context)
                if (location == null) {
                    status = "Konum alınamadı."
                    return@launch
                }
                status = "Vakitler getiriliyor..."
                val result = PrayerTimesRepository.fetchTimes(location.first, location.second)
                result.onSuccess {
                    times = it.times
                    date = it.date
                    status = "Tamam"
                }.onFailure {
                    status = "Hata"
                    errorText = it.message
                }
            } catch (e: Exception) {
                status = "Hata"
                errorText = e.message
            }
        }
    }

    fun start() {
        if (hasLocationPermission()) {
            loadTimes()
        } else {
            requestLocationPermission { granted ->
                if (granted) loadTimes() else status = "Konum izni verilmedi."
            }
        }
    }

    LaunchedEffect(Unit) { start() }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))
        Text("Ezan Vakti", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(status)

        if (date.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text(date, style = MaterialTheme.typography.titleMedium)
        }

        times?.let { list ->
            Spacer(Modifier.height(16.dp))
            list.forEachIndexed { index, time ->
                val label = VAKIT_ADLARI.getOrElse(index) { "Vakit ${index + 1}" }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(label, style = MaterialTheme.typography.bodyLarge)
                    Text(time, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                }
            }
        }

        errorText?.let {
            Spacer(Modifier.height(16.dp))
            Text("Sorun: $it", color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(24.dp))
        Button(onClick = { start() }) { Text("Yenile") }
    }
}
