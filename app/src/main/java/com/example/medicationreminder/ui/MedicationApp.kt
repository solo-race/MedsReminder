@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.example.medicationreminder.ui

import android.Manifest
import android.app.AlarmManager
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.medicationreminder.MainActivity
import com.example.medicationreminder.domain.model.DoseEvent
import com.example.medicationreminder.domain.model.MedicationDraft
import com.example.medicationreminder.domain.model.MedicationPlan
import com.example.medicationreminder.domain.model.TimeZoneMode
import com.example.medicationreminder.domain.scheduling.NextDoseCalculator
import java.io.File
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import kotlinx.coroutines.delay

private object Routes {
    const val HOME = "home"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val EDIT_NEW = "edit/new"
    const val EDIT = "edit/{medicationId}"
    fun edit(id: Long) = "edit/$id"
}

@Composable
fun MedicationApp(viewModel: MedicationViewModel, notificationMedicationId: Long) {
    val navController = rememberNavController()
    val plans by viewModel.plans.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    var openedNotificationTarget by remember { mutableStateOf(false) }

    LaunchedEffect(error) {
        error?.let {
            snackbarHost.showSnackbar(it)
            viewModel.clearError()
        }
    }
    LaunchedEffect(notificationMedicationId, plans) {
        if (!openedNotificationTarget && notificationMedicationId >= 0 && plans.any { it.medication.id == notificationMedicationId }) {
            openedNotificationTarget = true
            navController.navigate(Routes.edit(notificationMedicationId))
        }
    }

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            MainScaffold(
                currentRoute = Routes.HOME,
                snackbarHost = { SnackbarHost(snackbarHost) },
                onNavigate = { navController.navigate(it) },
                floatingAction = {
                    ExtendedFloatingActionButton(
                        onClick = { navController.navigate(Routes.EDIT_NEW) },
                    ) { Text("Add medication") }
                },
            ) { padding ->
                HomeScreen(plans, Modifier.padding(padding), onEdit = { navController.navigate(Routes.edit(it)) })
            }
        }
        composable(Routes.HISTORY) {
            MainScaffold(
                currentRoute = Routes.HISTORY,
                snackbarHost = { SnackbarHost(snackbarHost) },
                onNavigate = { navController.navigate(it) },
            ) { padding -> HistoryScreen(history, Modifier.padding(padding)) }
        }
        composable(Routes.SETTINGS) {
            MainScaffold(
                currentRoute = Routes.SETTINGS,
                snackbarHost = { SnackbarHost(snackbarHost) },
                onNavigate = { navController.navigate(it) },
            ) { padding -> SettingsScreen(Modifier.padding(padding)) }
        }
        composable(Routes.EDIT_NEW) {
            EditMedicationScreen(
                plan = null,
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.EDIT,
            arguments = listOf(navArgument("medicationId") { type = NavType.LongType }),
        ) { entry ->
            val id = entry.arguments?.getLong("medicationId") ?: return@composable
            EditMedicationScreen(
                plan = plans.firstOrNull { it.medication.id == id },
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }
    }
}

@Composable
private fun MainScaffold(
    currentRoute: String,
    snackbarHost: @Composable () -> Unit,
    onNavigate: (String) -> Unit,
    floatingAction: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Medication Reminder") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        bottomBar = {
            NavigationBar {
                listOf(Routes.HOME to "Today", Routes.HISTORY to "History", Routes.SETTINGS to "Settings").forEach { (route, label) ->
                    NavigationBarItem(
                        selected = route == currentRoute,
                        onClick = { onNavigate(route) },
                        icon = { Text(if (route == Routes.HOME) "●" else if (route == Routes.HISTORY) "◷" else "⚙") },
                        label = { Text(label) },
                    )
                }
            }
        },
        floatingActionButton = floatingAction,
        snackbarHost = snackbarHost,
    ) { content(it) }
}

@Composable
private fun HomeScreen(plans: List<MedicationPlan>, modifier: Modifier = Modifier, onEdit: (Long) -> Unit) {
    val now by minuteTicker()
    val ordered = remember(plans, now) {
        plans.sortedBy { plan ->
            plan.times.mapNotNull { time ->
                NextDoseCalculator.nextOccurrence(time.time, plan.schedule.weekdays, plan.schedule.zoneId(), now)
            }.minOrNull()
        }
    }
    if (plans.isEmpty()) {
        Box(modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No medications yet", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                Text("Add your first medication and its daily times.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("Today", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                Text("Your active medication schedule", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(ordered, key = { it.medication.id }) { plan ->
                MedicationCard(plan, now, onEdit)
            }
        }
    }
}

@Composable
private fun MedicationCard(plan: MedicationPlan, now: Instant, onEdit: (Long) -> Unit) {
    val next = plan.times.mapNotNull { time ->
        NextDoseCalculator.nextOccurrence(time.time, plan.schedule.weekdays, plan.schedule.zoneId(), now)
    }.minOrNull()
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onEdit(plan.medication.id) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            plan.medication.photoPath?.let { MedicationImage(Uri.fromFile(File(it)), Modifier.size(58.dp)) }
            if (plan.medication.photoPath != null) Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(plan.medication.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
                Text(plan.medication.dosageText, color = MaterialTheme.colorScheme.secondary)
                if (plan.medication.note.isNotBlank()) {
                    Text(
                        plan.medication.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    next?.let { "Next: ${formatInstant(it, plan.schedule.zoneId())}" } ?: "No upcoming dose",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun HistoryScreen(history: List<DoseEvent>, modifier: Modifier = Modifier) {
    if (history.isEmpty()) {
        Box(modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
            Text("Taken and skipped doses will appear here for 30 days.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { Text("Dose history", style = MaterialTheme.typography.headlineMedium) }
            items(history, key = { it.id }) { event ->
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(event.medicationName, fontWeight = FontWeight.Medium)
                            Text(event.dosageText, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(formatInstant(event.scheduledFor, ZoneId.systemDefault()), style = MaterialTheme.typography.bodySmall)
                        }
                        AssistChip(
                            onClick = {},
                            label = { Text(if (event.status.name == "TAKEN") "Taken" else "Skipped") },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val alarms = context.getSystemService(AlarmManager::class.java)
    val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
    val notificationsAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Text("Settings", style = MaterialTheme.typography.headlineMedium) }
        item {
            SettingCard(
                title = "Notifications",
                description = if (notificationsAllowed) "Allowed — medication reminders can be shown." else "Required to show medication reminders.",
                action = if (!notificationsAllowed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) "Allow" else null,
                onAction = { notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) },
            )
        }
        item {
            SettingCard(
                title = "Exact alarms",
                description = if (exactAllowed) "Allowed — reminders are scheduled for their exact minute." else "Not allowed — Android may delay reminders to save battery.",
                action = if (!exactAllowed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) "Allow" else null,
                onAction = {
                    context.startActivity(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).setData(Uri.parse("package:${context.packageName}")),
                    )
                },
            )
        }
        item {
            SettingCard(
                title = "Privacy",
                description = "Medication details, photos, and history are stored only on this device. No account or cloud sync is used.",
            )
        }
    }
}

@Composable
private fun SettingCard(title: String, description: String, action: String? = null, onAction: () -> Unit = {}) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (action != null) {
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = onAction) { Text(action) }
            }
        }
    }
}

@Composable
private fun EditMedicationScreen(plan: MedicationPlan?, viewModel: MedicationViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val initialTimes = plan?.times?.map { it.time } ?: listOf(LocalTime.of(8, 0), LocalTime.of(14, 0), LocalTime.of(20, 0))
    val times = remember(plan?.medication?.id) { mutableStateListOf<LocalTime>().apply { addAll(initialTimes) } }
    var name by remember(plan?.medication?.id) { mutableStateOf(plan?.medication?.name.orEmpty()) }
    var dosage by remember(plan?.medication?.id) { mutableStateOf(plan?.medication?.dosageText.orEmpty()) }
    var note by remember(plan?.medication?.id) { mutableStateOf(plan?.medication?.note.orEmpty()) }
    var enabled by remember(plan?.medication?.id) { mutableStateOf(plan?.medication?.enabled ?: true) }
    var weekdays by remember(plan?.medication?.id) { mutableStateOf(plan?.schedule?.weekdays ?: DayOfWeek.entries.toSet()) }
    var zoneMode by remember(plan?.medication?.id) { mutableStateOf(plan?.schedule?.timeZoneMode ?: TimeZoneMode.DEVICE) }
    var manualZone by remember(plan?.medication?.id) { mutableStateOf(plan?.schedule?.manualZoneId ?: ZoneId.systemDefault().id) }
    var selectedPhotoUri by remember(plan?.medication?.id) { mutableStateOf<Uri?>(null) }
    var removePhoto by remember(plan?.medication?.id) { mutableStateOf(false) }
    var showSourceDialog by remember { mutableStateOf(false) }
    var showZoneDialog by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            selectedPhotoUri = uri
            removePhoto = false
        }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        if (saved) {
            selectedPhotoUri = pendingCameraUri
            removePhoto = false
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(if (plan == null) "Add medication" else "Edit medication") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
                actions = { TextButton(onClick = {
                    viewModel.saveMedication(
                        draft = MedicationDraft(
                            id = plan?.medication?.id,
                            name = name,
                            dosageText = dosage,
                            note = note,
                            enabled = enabled,
                            weekdays = weekdays,
                            timeZoneMode = zoneMode,
                            manualZoneId = manualZone.takeIf { zoneMode == TimeZoneMode.MANUAL },
                            times = times.toList(),
                            existingPhotoPath = plan?.medication?.photoPath,
                        ),
                        previousPlan = plan,
                        newImageUri = selectedPhotoUri,
                        removePhoto = removePhoto,
                        onSaved = onBack,
                    )
                }) { Text("Save") } },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Medication name") }, singleLine = true)
            }
            item {
                OutlinedTextField(dosage, { dosage = it }, Modifier.fillMaxWidth(), label = { Text("Amount / dosage") }, placeholder = { Text("e.g. 1 tablet or 5 mL") }, singleLine = true)
            }
            item {
                OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), label = { Text("Note (optional)") }, minLines = 3, maxLines = 6)
            }
            item {
                val preview = selectedPhotoUri ?: plan?.medication?.photoPath?.takeIf { !removePhoto }?.let { Uri.fromFile(File(it)) }
                Column {
                    Text("Medication photo", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    if (preview != null) MedicationImage(preview, Modifier.fillMaxWidth().height(180.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { showSourceDialog = true }) { Text(if (preview == null) "Add photo" else "Change photo") }
                        if (preview != null) TextButton(onClick = { selectedPhotoUri = null; removePhoto = true }) { Text("Remove") }
                    }
                }
            }
            item {
                Text("Daily times", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                times.sorted().forEach { time ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = {
                            TimePickerDialog(context, { _, hour, minute ->
                                val index = times.indexOf(time)
                                if (index >= 0) times[index] = LocalTime.of(hour, minute)
                            }, time.hour, time.minute, false).show()
                        }) { Text(time.format(TIME_FORMATTER), style = MaterialTheme.typography.titleMedium) }
                        Spacer(Modifier.weight(1f))
                        if (times.size > 1) TextButton(onClick = { times.remove(time) }) { Text("Remove") }
                    }
                }
                OutlinedButton(onClick = { times.add((times.maxOrNull() ?: LocalTime.of(8, 0)).plusHours(4)) }) { Text("Add time") }
            }
            item {
                Text("Repeat on", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DayOfWeek.entries.forEach { day ->
                        FilterChip(
                            selected = day in weekdays,
                            onClick = { weekdays = if (day in weekdays) weekdays - day else weekdays + day },
                            label = { Text(day.getDisplayName(TextStyle.SHORT, locale)) },
                        )
                    }
                }
            }
            item {
                Text("Time zone", style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(selected = zoneMode == TimeZoneMode.DEVICE, onClick = { zoneMode = TimeZoneMode.DEVICE }, label = { Text("Follow device") })
                    Spacer(Modifier.width(8.dp))
                    FilterChip(selected = zoneMode == TimeZoneMode.MANUAL, onClick = { zoneMode = TimeZoneMode.MANUAL }, label = { Text("Manual") })
                }
                if (zoneMode == TimeZoneMode.MANUAL) {
                    TextButton(onClick = { showZoneDialog = true }) { Text("Selected: $manualZone") }
                }
            }
            item {
                FilterChip(selected = enabled, onClick = { enabled = !enabled }, label = { Text(if (enabled) "Reminders enabled" else "Reminders paused") })
            }
            if (plan != null) item {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { confirmDelete = true }) { Text("Delete medication", color = MaterialTheme.colorScheme.error) }
            }
        }
    }

    if (showSourceDialog) {
        AlertDialog(
            onDismissRequest = { showSourceDialog = false },
            title = { Text("Add medication photo") },
            text = { Text("Photos are copied into private app storage.") },
            confirmButton = {
                TextButton(onClick = {
                    showSourceDialog = false
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) { Text("Choose photo") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showSourceDialog = false
                    pendingCameraUri = viewModel.newCameraCaptureUri()
                    camera.launch(pendingCameraUri!!)
                }) { Text("Take photo") }
            },
        )
    }
    if (showZoneDialog) {
        TimeZonePickerDialog(manualZone, onSelect = { manualZone = it; showZoneDialog = false }, onDismiss = { showZoneDialog = false })
    }
    if (confirmDelete && plan != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${plan.medication.name}?") },
            text = { Text("Its schedule, photo, and stored history will be removed from this device.") },
            confirmButton = { TextButton(onClick = { viewModel.deleteMedication(plan, onBack) }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun TimeZonePickerDialog(selectedZone: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val zones = remember { ZoneId.getAvailableZoneIds().sorted() }
    val filtered = remember(query, zones) { zones.filter { it.contains(query, ignoreCase = true) }.take(150) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose time zone") },
        text = {
            Column {
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("Search") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(filtered) { zone ->
                        Text(
                            zone,
                            modifier = Modifier.fillMaxWidth().clickable { onSelect(zone) }.padding(vertical = 12.dp),
                            color = if (zone == selectedZone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun MedicationImage(uri: Uri, modifier: Modifier) {
    AndroidView(
        factory = { context -> android.widget.ImageView(context).apply { scaleType = android.widget.ImageView.ScaleType.CENTER_CROP } },
        update = { it.setImageURI(uri) },
        modifier = modifier.clip(RoundedCornerShape(12.dp)),
    )
}

@Composable
private fun minuteTicker() = produceState(initialValue = Instant.now()) {
    while (true) {
        value = Instant.now()
        delay(60_000)
    }
}

private fun com.example.medicationreminder.domain.model.MedicationSchedule.zoneId(): ZoneId = when (timeZoneMode) {
    TimeZoneMode.DEVICE -> ZoneId.systemDefault()
    TimeZoneMode.MANUAL -> manualZoneId?.let(ZoneId::of) ?: ZoneId.systemDefault()
}

private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")
private val DISPLAY_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d • h:mm a")
private fun formatInstant(instant: Instant, zoneId: ZoneId): String = DISPLAY_FORMATTER.format(instant.atZone(zoneId))
