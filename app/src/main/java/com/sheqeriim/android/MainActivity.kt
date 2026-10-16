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
import androidx.compose.foundation.horizontalScroll
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
            var devices by remember { mutableStateOf(listOf<Pair<String, String>>()) }
            var pairedMeters by remember { mutableStateOf(bluetooth.pairedDevices().filter { it.first.startsWith("meter+", ignoreCase = true) || it.first.contains("Accu-Chek", ignoreCase = true) }) }
            var message by remember { mutableStateOf("") }
            var meterPin by remember { mutableStateOf("") }
            var readingInput by remember { mutableStateOf("") }
            var readingNote by remember { mutableStateOf("") }
            var mealContext by remember { mutableStateOf("Pas ushqimit") }
            var readings by remember { mutableStateOf(glucoseStore.all()) }
            var saveMessage by remember { mutableStateOf("") }
            var medicationName by remember { mutableStateOf("") }
            var medicationDose by remember { mutableStateOf("") }
            var medicationEntries by remember { mutableStateOf(medicationStore.all()) }
            var reportStatus by remember { mutableStateOf("") }
            val wellnessStore = remember { WellnessStore(this@MainActivity) }
            var wellnessEntries by remember { mutableStateOf(wellnessStore.all()) }
            var foodDescription by remember { mutableStateOf("") }
            var foodCarbs by remember { mutableStateOf("") }
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
            val labels = if (language == "de") listOf("Übersicht","Messungen","Ernährung","Bluetooth","Medikamente","Bericht")
                         else if (language == "en") listOf("Home","Readings","Food","Bluetooth","Medicines","Report")
                         else listOf("Ballina","Matjet","Ushqimi","Bluetooth","Barnat","Raporti")
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
                    Row(Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        labels.forEachIndexed { index, label ->
                            TextButton(onClick = { page = listOf("home","readings","food","bluetooth","meds","report")[index] },
                                contentPadding = PaddingValues(horizontal = 3.dp)) {
                                Text(label, color = if (page == listOf("home","readings","food","bluetooth","meds","report")[index]) Cyan else Color.White, fontSize = 10.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        when (page) {
                            "home" -> {
                                item {
                                    Card(colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.fillMaxWidth(), onClick = { page = "readings" }) {
                                        Column(Modifier.padding(18.dp)) {
                                            Text("Sheqeri në gjak · mg/dL", color = Cyan)
                                            Spacer(Modifier.height(8.dp))
                                            Text(readings.firstOrNull()?.let { "${it.valueMgDl} mg/dL" } ?: "Shto matjen e parë →", color = Color.White, fontSize = 23.sp)
                                            Text("Prek për të regjistruar ose parë matjet", color = Color.LightGray)
                                        }
                                    }
                                }
                                item {
                                    Card(colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.fillMaxWidth(), onClick = { page = "bluetooth" }) {
                                        Column(Modifier.padding(18.dp)) {
                                            Text("Accu-Chek Guide · Roche 921", color = Cyan, fontSize = 16.sp)
                                            Spacer(Modifier.height(8.dp))
                                            Text("Lidhe glukometrin →", color = Color.White, fontSize = 20.sp)
                                            Text("Çiftimi Bluetooth dhe statusi i lidhjes", color = Color.LightGray)
                                        }
                                    }
                                }
                                item {
                                    val previousEvening = readings.filter {
                                        val c = java.util.Calendar.getInstance().apply { timeInMillis = it.timeMillis }
                                        val yesterday = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DATE, -1) }
                                        c.get(java.util.Calendar.YEAR) == yesterday.get(java.util.Calendar.YEAR) &&
                                            c.get(java.util.Calendar.DAY_OF_YEAR) == yesterday.get(java.util.Calendar.DAY_OF_YEAR)
                                    }.sortedBy { it.timeMillis }
                                    if (previousEvening.size >= 2) {
                                        Card(colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.fillMaxWidth()) {
                                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                                Text("🌙 Mbrëmë · Analiza e matjeve", color = Cyan, fontSize = 18.sp)
                                                previousEvening.takeLast(6).forEach { reading ->
                                                    val clock = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(reading.timeMillis))
                                                    Text("$clock  ·  ${reading.valueMgDl} mg/dL", color = Color.White)
                                                }
                                                val highest = previousEvening.maxOf { it.valueMgDl }
                                                val lowest = previousEvening.minOf { it.valueMgDl }
                                                Text("Intervali i matjeve: $lowest–$highest mg/dL", color = Color.White)
                                                Text("Ushqimi mund ta rrisë glukozën edhe kur insulina vepron. Mos ndrysho dozat vetëm sipas këtij grafiku.", color = Color.LightGray, fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }
                                item {
                                    val todayStart = java.util.Calendar.getInstance().apply {
                                        set(java.util.Calendar.HOUR_OF_DAY, 0)
                                        set(java.util.Calendar.MINUTE, 0)
                                        set(java.util.Calendar.SECOND, 0)
                                        set(java.util.Calendar.MILLISECOND, 0)
                                    }.timeInMillis
                                    val today = readings.filter { it.timeMillis >= todayStart }.sortedBy { it.timeMillis }
                                    val avg = if (today.isEmpty()) "—" else "%.0f".format(java.util.Locale.US, today.map { it.valueMgDl }.average())
                                    Card(colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.fillMaxWidth()) {
                                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Text("📈 Sot · Trend i glukozës", color = Cyan, fontSize = 19.sp)
                                            Text("${today.size} matje   •   Mesatarja $avg mg/dL", color = Color.White)
                                            if (today.isEmpty()) {
                                                Text("Nuk ka matje për sot", color = Color.LightGray)
                                            } else {
                                                today.forEach { reading ->
                                                    val clock = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(reading.timeMillis))
                                                    val indicator = when {
                                                        reading.valueMgDl < 70 -> "🔴"
                                                        reading.valueMgDl <= 180 -> "🟢"
                                                        else -> "🟠"
                                                    }
                                                    Text("$indicator $clock  ·  ${reading.valueMgDl} mg/dL", color = Color.White, fontSize = 16.sp)
                                                }
                                                if (today.size >= 2) {
                                                    val change = today.last().valueMgDl - today[today.size - 2].valueMgDl
                                                    Text("Ndryshimi nga matja e mëparshme: ${if (change > 0) "+" else ""}$change mg/dL", color = Cyan)
                                                }
                                            }
                                            Text("Ngjyrat janë orientuese; objektivat përcaktohen me mjekun. Matjet Bluetooth shfaqen pas importit.", color = Color.LightGray, fontSize = 12.sp)
                                        }
                                    }
                                }
                                item { InfoCard("Vlerësimi i fundit", readings.firstOrNull()?.let { GlucoseEducation.message(it.valueMgDl, language) } ?: "Prek kartën e matjeve për të shtuar matjen e parë", "Informacion orientues, jo diagnozë.") }
                                item {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(onClick = { page = "food" }) { Text("Ushqimi") }
                                        Button(onClick = { page = "meds" }) { Text("Barnat") }
                                        Button(onClick = { page = "report" }) { Text("Raporti") }
                                    }
                                }
                                item {
                                    val recent = readings.filter { it.timeMillis >= System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000 }
                                    val average = if (recent.isEmpty()) "—" else recent.map { it.valueMgDl }.average().toInt().toString()
                                    InfoCard("Përmbledhja · 7 ditë", "Mesatarja: $average mg/dL", "Matje: ${recent.size} · Barnat e regjistruara: ${medicationEntries.size}")
                                }
                                item {
                                    InfoCard("Ditari i ushqimit", wellnessEntries.firstOrNull { it.kind == "Ushqim" }?.detail ?: "Ende pa ushqime të regjistruara", "Hap Ushqimi për të shtuar vaktin.")
                                }
                                item { InfoCard("Gjendja e sistemit", "Matjet manuale aktive", "Importi Bluetooth ende nuk është verifikuar me pajisjen.") }
                            }
                            "bluetooth" -> {
                                item { InfoCard("Accu-Chek Guide", "Roche · Model 921 · mg/dL", "Native Bluetooth GATT. Importi i matjeve nuk është verifikuar ende.") }
                                item { Button(onClick = { pairedMeters = bluetooth.pairedDevices().filter { it.first.startsWith("meter+", ignoreCase = true) || it.first.contains("Accu-Chek", ignoreCase = true) } }) { Text("Glukometrat e çiftuar") } }
                                items(pairedMeters) { paired ->
                                    Column {
                                        Text(paired.first, color = Color.White)
                                        Button(onClick = { bluetooth.connectPaired(paired.second,
                                            onStatus = { status -> runOnUiThread { message = status } },
                                            onReading = { value, timestamp, meter, sequence ->
                                                val saved = glucoseStore.importMeterReading(value, timestamp, meter, sequence)
                                                runOnUiThread {
                                                    if (saved) {
                                                        readings = glucoseStore.all()
                                                        message = "U importua matja #$sequence: $value mg/dL"
                                                    }
                                                }
                                            }
                                        ) }) { Text("Lidhu / Verbinden") }
                                    }
                                }
                                item {
                                    Button(onClick = {
                                        if (Build.VERSION.SDK_INT >= 31 && !bluetooth.hasPermission()) {
                                            permissionLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT))
                                        } else {
                                            devices = emptyList()
                                            bluetooth.startPairingScan(
                                                onDevice = { name, address -> runOnUiThread { devices = (devices + (name to address)).filter { it.first.startsWith("meter+", ignoreCase = true) || it.first.contains("Accu-Chek", ignoreCase = true) }.distinctBy { it.second } } },
                                                onError = { error -> runOnUiThread { message = error } }
                                            )
                                            message = "Duke kërkuar glukometrin Accu-Chek Guide…"
                                        }
                                    }) { Text("Kërko Accu-Chek Guide") }
                                }
                                item { OutlinedButton(onClick = { bluetooth.stopScan(); message = "Scan stopped" }) { Text("Stop scan") } }
                                item { Text(message, color = Color.White) }
                                item { Text("Nëse shfaqet meter+..., shtyp Çifto. Nëse Android nuk kërkon PIN dhe glukometri nuk del te pajisjet e çiftuara, çiftimi nuk është konfirmuar.", color = Color.LightGray, fontSize = 12.sp) }
                                item {
                                    OutlinedTextField(
                                        value = meterPin,
                                        onValueChange = { meterPin = it.filter(Char::isDigit).take(6) },
                                        label = { Text("Kodi 6-shifror nga Accu-Chek Guide") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                                    )
                                }
                                item { Text("Kodi dergohet gjate kerkese se ciftimit. Nese Android shfaq dialog, konfirmoje edhe aty.", color = Color.LightGray, fontSize = 12.sp) }
                                items(devices) { found ->
                                    Column {
                                        Text("📶 " + found.first, color = Cyan)
                                        Button(onClick = { bluetooth.pairWithPin(found.second, meterPin) { status -> runOnUiThread { message = status; pairedMeters = bluetooth.pairedDevices().filter { it.first.startsWith("meter+", ignoreCase = true) || it.first.contains("Accu-Chek", ignoreCase = true) } } } }) { Text("Çifto / Koppeln") }
                                    }
                                }
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
                                    val entered = readingInput.toIntOrNull()
                                    if (entered != null && entered in 20..600) {
                                        val interpretation = when {
                                            entered < 70 -> "🔴 Sheqer i ulët: trajto hipoglikeminë sipas planit të mjekut, mat përsëri pas 15 minutash. Nëse je i hutuar ose s'mund të gëlltisësh, kërko ndihmë urgjente."
                                            entered < 80 -> "🟠 Afër kufirit të ulët: kontrollo simptomat dhe ndiq planin personal."
                                            mealContext == "Para ushqimit" && entered <= 130 -> "🟢 Brenda intervalit të zakonshëm para ushqimit (80–130 mg/dL)."
                                            mealContext == "Para ushqimit" -> "🟠 Mbi intervalin e zakonshëm para ushqimit; krahasoje me objektivin e mjekut."
                                            entered <= 180 -> "🟢 Vlerë përgjithësisht qetësuese pas ushqimit; interpretimi varet nga koha që ka kaluar."
                                            else -> "🟠 Vlerë e ngritur; kontrollo kohën pas vaktit dhe udhëzimet e mjekut."
                                        }
                                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF182B3C))) {
                                            Text(interpretation, modifier = Modifier.padding(14.dp), color = Color.White)
                                        }
                                    }
                                    if (entered != null && entered in 20..600 && mealContext == "Para ushqimit") {
                                        val chart = PhysicianPlan.lookup(entered)
                                        Card(colors = CardDefaults.cardColors(containerColor = if (chart.attention) Color(0xFF56242B) else Color(0xFF174936))) {
                                            Column(Modifier.padding(14.dp)) {
                                                Text("TABELA E MJEKUT · 13.10.2026", color = Color.White, fontSize = 13.sp)
                                                Text(chart.range, color = Color.White)
                                                Text("Në recetë: ${chart.documentedUnits} njësi Humalog", color = Color.White, fontSize = 19.sp)
                                                Text("Vetëm referencë e recetës, jo udhëzim për injektim. Verifiko planin aktual dhe kushtet e matjes.", color = Color.White, fontSize = 12.sp)
                                            }
                                        }
                                    } else if (entered != null) {
                                        Text("Tabela e Humalog vlen vetëm për matje PARA ushqimit. Nuk llogaritet dozë.", color = Cyan)
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
                                    InfoCard(date, "${reading.valueMgDl} mg/dL", reading.note.ifBlank { "Manual entry" } + "\n" + GlucoseEducation.message(reading.valueMgDl, language))
                                }
                            }
                            "food" -> {
                                item { InfoCard("🥗 Ushqimi", "Ditari i vakteve", "Shëno ushqimet dhe karbohidratet e përafërta; pa llogaritje insuline.") }
                                item {
                                    Text("Shembuj nga ushqimet e tua · vlerësime, jo peshime", color = Color.LightGray)
                                }
                                item {
                                    Button(onClick = {
                                        foodDescription = "Mëngjes: 2 vezë të ziera, djathë i bardhë (~90 g), ullinj, kastravec, ajvar, 2 speca të gjelbër, bukë (~70 g). Proteina ~36 g; kalori ~700 kcal. Sasitë janë vlerësuar nga fotografia."
                                        foodCarbs = "57"
                                    }) { Text("📷 Mëngjesi 16.10 · 57 g KH · 36 g proteina") }
                                }
                                item {
                                    Button(onClick = {
                                        foodDescription = "Mëngjes 15.10: 5 feta bukë integrale, 2 vezë të ziera, djathë, gjalpë, ullinj, kastravec, ajvar, speca dhe çaj pa sheqer. Vlerësim 65–77 g KH."
                                        foodCarbs = "75"
                                    }) { Text("Mëngjesi 15.10 · 75 g KH") }
                                }
                                item {
                                    Button(onClick = {
                                        foodDescription = "Darkë 15.10: 6 sarma, bukë e bardhë dhe 2 copa pite me spinaq e kaçkavall. Karbohidratet varen nga madhësia e porcioneve."
                                        foodCarbs = ""
                                    }) { Text("Darka 15.10 · plotëso karbohidratet") }
                                }
                                item { OutlinedTextField(value = foodDescription, onValueChange = { foodDescription = it.take(200) }, label = { Text("Përshkrimi i ushqimit") }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)) }
                                item { OutlinedTextField(value = foodCarbs, onValueChange = { foodCarbs = it.filter(Char::isDigit).take(4) }, label = { Text("Karbohidrate g (opsionale)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)) }
                                item { Button(onClick = {
                                    val detail = foodDescription.trim() + if (foodCarbs.isNotBlank()) " · rreth " + foodCarbs + " g karbohidrate" else ""
                                    if (wellnessStore.add("Ushqim", detail)) {
                                        wellnessEntries = wellnessStore.all()
                                        foodDescription = ""
                                        foodCarbs = ""
                                    }
                                }) { Text("Ruaj ushqimin") } }
                                items(wellnessEntries) { entry ->
                                    InfoCard(entry.kind, entry.detail, SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY).format(Date(entry.timestamp)))
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
