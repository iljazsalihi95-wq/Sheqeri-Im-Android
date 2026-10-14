package com.sheqeriim.android

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Navy = Color(0xFF071426)
private val Panel = Color(0xFF142943)
private val Cyan = Color(0xFF3BD7D0)

class MainActivity : ComponentActivity() {
    private lateinit var bluetooth: GlucoseBluetooth
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bluetooth = GlucoseBluetooth(this)
        setContent {
            var language by remember { mutableStateOf("sq") }
            var page by remember { mutableStateOf("home") }
            var devices by remember { mutableStateOf(listOf<String>()) }
            var message by remember { mutableStateOf("") }
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { result -> message = if (result.values.all { it }) "Bluetooth permission granted" else "Bluetooth permission denied" }
            val labels = if (language == "de") listOf("Übersicht","Messungen","Bluetooth","Medikamente","Bericht")
                         else if (language == "en") listOf("Home","Readings","Bluetooth","Medicines","Report")
                         else listOf("Ballina","Matjet","Bluetooth","Barnat","Raporti")
            MaterialTheme {
                Column(Modifier.fillMaxSize().background(Navy).padding(16.dp)) {
                    Text("💠 SHEQERI IM", color = Cyan, fontSize = 26.sp)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("sq","de","en").forEach { code ->
                            OutlinedButton(onClick = { language = code }) { Text(code.uppercase(), color = Cyan) }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        labels.forEachIndexed { index, label ->
                            TextButton(onClick = { page = listOf("home","readings","bluetooth","meds","report")[index] },
                                contentPadding = PaddingValues(horizontal = 3.dp)) {
                                Text(label, color = if (page == listOf("home","readings","bluetooth","meds","report")[index]) Cyan else Color.White, fontSize = 10.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        when (page) {
                            "home" -> {
                                item { InfoCard("Glucose / Blutzucker", "— mg/dL", "No measurement imported yet") }
                                item { InfoCard("Bluetooth", "Accu-Chek Guide", "Open Bluetooth tab to discover nearby devices") }
                                item { InfoCard("Status", "Native Android prototype", "Account, storage, chart and automatic sync are still in development") }
                            }
                            "bluetooth" -> {
                                item { InfoCard("Bluetooth Low Energy", "Discovery test", "Scan only. Automatic readings and pairing are not implemented yet.") }
                                item {
                                    Button(onClick = {
                                        if (Build.VERSION.SDK_INT >= 31 && !bluetooth.hasPermission()) {
                                            permissionLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT))
                                        } else {
                                            devices = emptyList()
                                            bluetooth.startScan(
                                                onDevice = { name -> runOnUiThread { devices = (devices + name).distinct() } },
                                                onError = { error -> runOnUiThread { message = error } }
                                            )
                                            message = "Scanning nearby BLE devices…"
                                        }
                                    }) { Text("Scan BLE devices") }
                                }
                                item { OutlinedButton(onClick = { bluetooth.stopScan(); message = "Scan stopped" }) { Text("Stop scan") } }
                                item { Text(message, color = Color.White) }
                                items(devices) { Text("📶 $it", color = Cyan) }
                            }
                            "readings" -> item { InfoCard("Matjet / Messungen", "In development", "No automatic measurements are shown until BLE sync is verified") }
                            "meds" -> item { InfoCard("Barnat / Medikamente", "In development", "Medication logging and reminders are not yet enabled") }
                            "report" -> item { InfoCard("Ärztlicher Bericht", "Deutsch PDF", "German doctor report is planned; no clinical data available in this prototype") }
                        }
                    }
                }
            }
        }
    }
    override fun onDestroy() { bluetooth.close(); super.onDestroy() }
}

@Composable
private fun InfoCard(title: String, value: String, description: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Text(title, color = Cyan, fontSize = 15.sp)
            Spacer(Modifier.height(8.dp))
            Text(value, color = Color.White, fontSize = 23.sp)
            Spacer(Modifier.height(8.dp))
            Text(description, color = Color.LightGray, fontSize = 13.sp)
        }
    }
}
