package com.sheqeriim.android

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
        val glucoseStore = GlucoseStore(this)
        val medicationStore = MedicationStore(this)
        setContent {
            var language by remember { mutableStateOf("sq") }
            var page by remember { mutableStateOf("home") }
            var devices by remember { mutableStateOf(listOf<String>()) }
            var message by remember { mutableStateOf("") }
            var readingInput by remember { mutableStateOf("") }
            var readingNote by remember { mutableStateOf("") }
            var mealContext by remember { mutableStateOf("Pas ushqimit") }
            var readings by remember { mutableStateOf(glucoseStore.all()) }
            var saveMessage by remember { mutableStateOf("") }
            var medicationName by remember { mutableStateOf("") }
            var medicationDose by remember { mutableStateOf("") }
            var medicationEntries by remember { mutableStateOf(medicationStore.all()) }
            var reportStatus by remember { mutableStateOf("") }
            val pdfLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument("application/pdf")
            ) { uri ->
                if (uri != null) {
                    try {
                        DoctorReport.write(this, uri, glucoseStore.all(), medicationStore.all())
                        reportStatus = "PDF gespeichert / Raporti u ruajt"
                    } catch (e: Exception) {
                        reportStatus = "PDF error: ${e.message ?: "unknown"}"
                    }
                }
            }
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
                                item { InfoCard("Glucose / Blutzucker", readings.firstOrNull()?.let { "${it.valueMgDl} mg/dL" } ?: "— mg/dL", if (readings.isEmpty()) "No measurements saved yet" else "${readings.size} locally saved measurements") }
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
                            "readings" -> {
                                item { InfoCard("Matjet / Messungen", "Manual entry", "Measurements are saved only on this device. BLE import is not yet available.") }
                                item {
                                    OutlinedTextField(value = readingInput, onValueChange = { readingInput = it.filter(Char::isDigit).take(3) },
                                        label = { Text("Glucose (mg/dL)") }, singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedLabelColor = Cyan, unfocusedLabelColor = Cyan))
                                }
                                item {
                                    Text("Koha ndaj ushqimit / Zeitpunkt zur Mahlzeit", color = Cyan)
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        listOf("Para ushqimit", "Pas ushqimit", "Esëll").forEach { option ->
                                            TextButton(onClick = { mealContext = option }) {
                                                Text(option, fontSize = 11.sp, color = if (mealContext == option) Cyan else Color.LightGray)
                                            }
                                        }
                                    }
                                }
                                item {
                                    OutlinedTextField(value = readingNote, onValueChange = { readingNote = it.take(120) },
                                        label = { Text("Note / Shënim") },
                                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedLabelColor = Cyan, unfocusedLabelColor = Cyan))
                                }
                                item {
                                    Button(onClick = {
                                        val value = readingInput.toIntOrNull()
                                        if (value != null && glucoseStore.add(value, "$mealContext · $readingNote")) {
                                            readings = glucoseStore.all()
                                            readingInput = ""
                                            readingNote = ""
                                            mealContext = "Pas ushqimit"
                                            saveMessage = "Measurement saved locally"
                                        } else saveMessage = "Enter a value from 20 to 600 mg/dL"
                                    }) { Text("Ruaj matjen / Speichern") }
                                }
                                item { Text(saveMessage, color = Cyan) }
                                items(readings) { reading ->
                                    val date = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY).format(Date(reading.timeMillis))
                                    InfoCard(date, "${reading.valueMgDl} mg/dL", reading.note.ifBlank { "Manual entry" })
                                }
                            }
                            "meds" -> {
                                item { InfoCard("Barnat / Medikamente", "Medication journal", "Log what you actually took. This app does not calculate insulin doses.") }
                                item {
                                    OutlinedTextField(value = medicationName, onValueChange = { medicationName = it.take(80) },
                                        label = { Text("Emri / Medikament") },
                                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White))
                                }
                                item {
                                    OutlinedTextField(value = medicationDose, onValueChange = { medicationDose = it.take(60) },
                                        label = { Text("Doza e marrë / Eingenommene Dosis") },
                                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White))
                                }
                                item {
                                    Button(onClick = {
                                        if (medicationStore.add(medicationName, medicationDose)) {
                                            medicationEntries = medicationStore.all()
                                            medicationName = ""
                                            medicationDose = ""
                                        }
                                    }) { Text("Ruaj / Speichern") }
                                }
                                items(medicationEntries) { entry ->
                                    InfoCard(entry.name, entry.dose, SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY).format(Date(entry.timeMillis)))
                                }
                            }
                            "report" -> {
                                item { InfoCard("Ärztlicher Bericht", "PDF auf Deutsch", "Exportiert lokal erfasste Messungen und Medikamenteneinträge.") }
                                item { Button(onClick = { pdfLauncher.launch("Sheqeri-Im-Arztbericht.pdf") }) { Text("PDF erstellen / Krijo raportin") } }
                                item { Text(reportStatus, color = Cyan) }
                            }
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
