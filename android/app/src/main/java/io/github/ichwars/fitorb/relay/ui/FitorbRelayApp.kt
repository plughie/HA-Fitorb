package io.github.ichwars.fitorb.relay.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.ichwars.fitorb.relay.data.RelayAckDto
import io.github.ichwars.fitorb.relay.network.RelayUploadException
import io.github.ichwars.fitorb.relay.settings.MAX_SYNC_INTERVAL_MINUTES
import io.github.ichwars.fitorb.relay.settings.MIN_SYNC_INTERVAL_MINUTES
import io.github.ichwars.fitorb.relay.settings.RelaySettings
import kotlinx.coroutines.launch

private val AppBlack = Color(0xFF040707)
private val AppInk = Color(0xFF111413)
private val AppPanel = Color(0xFF171A19)
private val AppPanelSoft = Color(0xFF202421)
private val AppLine = Color(0xFF42443C)
private val AppText = Color(0xFFE2E2E2)
private val AppMuted = Color(0xFFA1A2A1)
private val AppDim = Color(0xFF80837E)
private val AppGreen = Color(0xFF22D13A)
private val AppGreenDark = Color(0xFF0A5A0D)
private val AppTeal = Color(0xFF053C37)
private val AppSilver = Color(0xFFD8D9D4)
private val AppWarning = Color(0xFFFFCC66)

private val FitorbColorScheme = darkColorScheme(
    primary = AppGreen,
    onPrimary = Color.White,
    secondary = AppSilver,
    background = AppBlack,
    onBackground = AppText,
    surface = AppPanel,
    onSurface = AppText,
    surfaceVariant = AppPanelSoft,
    onSurfaceVariant = AppMuted,
    outline = AppLine,
)

private enum class RelayTab(
    val key: String,
    val label: String,
    val mark: String,
) {
    Home("home", "Home", "H"),
    Activity("activity", "Aktiv", "A"),
    Sleep("sleep", "Schlaf", "S"),
    More("more", "Mehr", "M"),
}

@Composable
fun FitorbRelayApp(
    initialSettings: RelaySettings,
    defaultRelayId: String,
    onSave: (RelaySettings) -> Unit,
    onUpload: suspend (RelaySettings) -> RelayAckDto,
) {
    var homeAssistantUrl by rememberSaveable {
        mutableStateOf(initialSettings.homeAssistantUrl)
    }
    var relayToken by rememberSaveable { mutableStateOf(initialSettings.relayToken) }
    var relayId by rememberSaveable { mutableStateOf(initialSettings.relayId) }
    var ringId by rememberSaveable { mutableStateOf(initialSettings.ringId) }
    var syncInterval by rememberSaveable {
        mutableStateOf(
            initialSettings.syncIntervalMinutes.coerceIn(
                MIN_SYNC_INTERVAL_MINUTES,
                MAX_SYNC_INTERVAL_MINUTES,
            ),
        )
    }
    var setupStep by rememberSaveable {
        mutableStateOf(if (initialSettings.isReadyForUpload()) 2 else 0)
    }
    var activeTab by rememberSaveable { mutableStateOf(RelayTab.Home.key) }
    var uploadStatus by rememberSaveable { mutableStateOf("Bereit") }
    var lastSync by rememberSaveable { mutableStateOf("Noch kein Upload") }
    var mobileRelayActive by rememberSaveable { mutableStateOf(false) }
    var uploading by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun currentSettings() = RelaySettings(
        homeAssistantUrl = homeAssistantUrl,
        relayToken = relayToken,
        relayId = relayId.ifBlank { defaultRelayId },
        ringId = ringId,
        syncIntervalMinutes = syncInterval,
    )

    fun saveCurrentSettings() = onSave(currentSettings())

    fun uploadCurrentSettings() {
        val settings = currentSettings()
        saveCurrentSettings()
        uploading = true
        uploadStatus = "Sende Test..."
        scope.launch {
            uploadStatus = try {
                val ack = onUpload(settings)
                mobileRelayActive = true
                lastSync = "Gerade eben"
                "OK: ${ack.accepted.size} neu, " +
                    "${ack.duplicates.size} doppelt, " +
                    "${ack.rejected.size} abgewiesen"
            } catch (exception: RelayUploadException) {
                "Fehler: ${exception.message.orEmpty()}"
            } catch (exception: IllegalArgumentException) {
                "Fehler: ${exception.message.orEmpty()}"
            } finally {
                uploading = false
            }
        }
    }

    val settings = currentSettings()
    val canUpload = settings.isReadyForUpload() && !uploading

    MaterialTheme(colorScheme = FitorbColorScheme) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AppBackgroundBrush()),
        ) {
            if (setupStep < 2) {
                SetupFlow(
                    step = setupStep,
                    ringId = ringId,
                    onRingIdChange = { ringId = it },
                    homeAssistantUrl = homeAssistantUrl,
                    onHomeAssistantUrlChange = { homeAssistantUrl = it },
                    relayToken = relayToken,
                    onRelayTokenChange = { relayToken = it },
                    relayId = relayId.ifBlank { defaultRelayId },
                    onRelayIdChange = { relayId = it },
                    syncInterval = syncInterval,
                    onSyncIntervalChange = { syncInterval = it },
                    uploadStatus = uploadStatus,
                    uploading = uploading,
                    canUpload = canUpload,
                    onBack = { setupStep = 0 },
                    onSave = {
                        saveCurrentSettings()
                        setupStep = 2
                    },
                    onContinueFromRing = {
                        relayId = relayId.ifBlank { defaultRelayId }
                        setupStep = 1
                    },
                    onUpload = ::uploadCurrentSettings,
                )
            } else {
                RelayDashboard(
                    activeTab = activeTab,
                    onTabSelected = { activeTab = it.key },
                    settings = settings,
                    mobileRelayActive = mobileRelayActive,
                    lastSync = lastSync,
                    uploadStatus = uploadStatus,
                    uploading = uploading,
                    canUpload = canUpload,
                    onUpload = ::uploadCurrentSettings,
                    onEditSetup = {
                        activeTab = RelayTab.More.key
                    },
                    onOpenSetupFlow = {
                        setupStep = 0
                    },
                    onHomeAssistantUrlChange = { homeAssistantUrl = it },
                    onRelayTokenChange = { relayToken = it },
                    onRelayIdChange = { relayId = it },
                    onRingIdChange = { ringId = it },
                    onSyncIntervalChange = { syncInterval = it },
                    onSave = ::saveCurrentSettings,
                )
            }
        }
    }
}

@Composable
private fun SetupFlow(
    step: Int,
    ringId: String,
    onRingIdChange: (String) -> Unit,
    homeAssistantUrl: String,
    onHomeAssistantUrlChange: (String) -> Unit,
    relayToken: String,
    onRelayTokenChange: (String) -> Unit,
    relayId: String,
    onRelayIdChange: (String) -> Unit,
    syncInterval: Int,
    onSyncIntervalChange: (Int) -> Unit,
    uploadStatus: String,
    uploading: Boolean,
    canUpload: Boolean,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onContinueFromRing: () -> Unit,
    onUpload: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(22.dp),
    ) {
        if (step == 0) {
            RingSearchStep(
                ringId = ringId,
                onRingIdChange = onRingIdChange,
                onContinue = onContinueFromRing,
            )
        } else {
            HomeAssistantSetupStep(
                homeAssistantUrl = homeAssistantUrl,
                onHomeAssistantUrlChange = onHomeAssistantUrlChange,
                relayToken = relayToken,
                onRelayTokenChange = onRelayTokenChange,
                relayId = relayId,
                onRelayIdChange = onRelayIdChange,
                ringId = ringId,
                onRingIdChange = onRingIdChange,
                syncInterval = syncInterval,
                onSyncIntervalChange = onSyncIntervalChange,
                uploadStatus = uploadStatus,
                uploading = uploading,
                canUpload = canUpload,
                onBack = onBack,
                onSave = onSave,
                onUpload = onUpload,
            )
        }
        SetupDots(
            step = step,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 10.dp),
        )
    }
}

@Composable
private fun RingSearchStep(
    ringId: String,
    onRingIdChange: (String) -> Unit,
    onContinue: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TopStatusRow(label = "Bluetooth")
        Spacer(modifier = Modifier.height(30.dp))
        Text(
            text = "Searching for your ring",
            style = MaterialTheme.typography.headlineLarge,
            color = AppText,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Make sure the ring is charged",
            style = MaterialTheme.typography.bodyMedium,
            color = AppMuted,
            modifier = Modifier.padding(top = 8.dp),
        )
        Spacer(modifier = Modifier.height(36.dp))
        RadarRingVisual(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
        RelayTextField(
            value = ringId,
            onValueChange = onRingIdChange,
            label = "Ring ID",
            placeholder = "AA:BB:CC:DD:EE:FF",
            keyboardType = KeyboardType.Text,
        )
        Spacer(modifier = Modifier.height(18.dp))
        PrimaryRelayButton(
            text = if (ringId.isBlank()) "Weiter" else "Ring übernehmen",
            enabled = ringId.isNotBlank(),
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun HomeAssistantSetupStep(
    homeAssistantUrl: String,
    onHomeAssistantUrlChange: (String) -> Unit,
    relayToken: String,
    onRelayTokenChange: (String) -> Unit,
    relayId: String,
    onRelayIdChange: (String) -> Unit,
    ringId: String,
    onRingIdChange: (String) -> Unit,
    syncInterval: Int,
    onSyncIntervalChange: (Int) -> Unit,
    uploadStatus: String,
    uploading: Boolean,
    canUpload: Boolean,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onUpload: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TopStatusRow(label = "Home Assistant")
        Text(
            text = "Relay verbinden",
            style = MaterialTheme.typography.headlineLarge,
            color = AppText,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            text = "HTTPS Upload, Token und Ring-ID",
            style = MaterialTheme.typography.bodyMedium,
            color = AppMuted,
        )
        SetupCard {
            RelayTextField(
                value = homeAssistantUrl,
                onValueChange = onHomeAssistantUrlChange,
                label = "HA-Link",
                placeholder = "https://ha.example.net",
                keyboardType = KeyboardType.Uri,
            )
            RelayTextField(
                value = relayToken,
                onValueChange = onRelayTokenChange,
                label = "Relay-Token",
                placeholder = "fitorb_relay_...",
                keyboardType = KeyboardType.Password,
                password = true,
            )
            RelayTextField(
                value = relayId,
                onValueChange = onRelayIdChange,
                label = "Relay-ID",
                placeholder = "android-pixel",
                keyboardType = KeyboardType.Text,
            )
            RelayTextField(
                value = ringId,
                onValueChange = onRingIdChange,
                label = "Ring ID",
                placeholder = "AA:BB:CC:DD:EE:FF",
                keyboardType = KeyboardType.Text,
            )
            IntervalSlider(
                syncInterval = syncInterval,
                onSyncIntervalChange = onSyncIntervalChange,
            )
        }
        StatusPill(
            label = uploadStatus,
            active = uploadStatus.startsWith("OK"),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SecondaryRelayButton(
                text = "Zurück",
                onClick = onBack,
                modifier = Modifier.weight(1f),
            )
            PrimaryRelayButton(
                text = if (uploading) "Sende..." else "Test senden",
                enabled = canUpload,
                onClick = onUpload,
                modifier = Modifier.weight(1f),
            )
        }
        PrimaryRelayButton(
            text = "Dashboard öffnen",
            enabled = canUpload,
            onClick = onSave,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(36.dp))
    }
}

@Composable
private fun RelayDashboard(
    activeTab: String,
    onTabSelected: (RelayTab) -> Unit,
    settings: RelaySettings,
    mobileRelayActive: Boolean,
    lastSync: String,
    uploadStatus: String,
    uploading: Boolean,
    canUpload: Boolean,
    onUpload: () -> Unit,
    onEditSetup: () -> Unit,
    onOpenSetupFlow: () -> Unit,
    onHomeAssistantUrlChange: (String) -> Unit,
    onRelayTokenChange: (String) -> Unit,
    onRelayIdChange: (String) -> Unit,
    onRingIdChange: (String) -> Unit,
    onSyncIntervalChange: (Int) -> Unit,
    onSave: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 22.dp, top = 28.dp, end = 22.dp, bottom = 112.dp),
        ) {
            when (activeTab) {
                RelayTab.Activity.key -> ActivityScreen()
                RelayTab.Sleep.key -> SleepScreen()
                RelayTab.More.key -> MoreScreen(
                    settings = settings,
                    uploadStatus = uploadStatus,
                    uploading = uploading,
                    canUpload = canUpload,
                    onUpload = onUpload,
                    onOpenSetupFlow = onOpenSetupFlow,
                    onHomeAssistantUrlChange = onHomeAssistantUrlChange,
                    onRelayTokenChange = onRelayTokenChange,
                    onRelayIdChange = onRelayIdChange,
                    onRingIdChange = onRingIdChange,
                    onSyncIntervalChange = onSyncIntervalChange,
                    onSave = onSave,
                )
                else -> HomeScreen(
                    settings = settings,
                    mobileRelayActive = mobileRelayActive,
                    lastSync = lastSync,
                    uploadStatus = uploadStatus,
                    uploading = uploading,
                    canUpload = canUpload,
                    onUpload = onUpload,
                    onEditSetup = onEditSetup,
                )
            }
        }
        FloatingBottomBar(
            activeTab = activeTab,
            onTabSelected = onTabSelected,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 22.dp, vertical = 18.dp),
        )
    }
}

@Composable
private fun HomeScreen(
    settings: RelaySettings,
    mobileRelayActive: Boolean,
    lastSync: String,
    uploadStatus: String,
    uploading: Boolean,
    canUpload: Boolean,
    onUpload: () -> Unit,
    onEditSetup: () -> Unit,
) {
    HeaderBlock(
        title = "Fitorb Ring",
        subtitle = compactRingId(settings.ringId),
        mobileRelayActive = mobileRelayActive,
    )
    Spacer(modifier = Modifier.height(22.dp))
    RingDashboardCard(
        mobileRelayActive = mobileRelayActive,
        lastSync = lastSync,
    )
    Spacer(modifier = Modifier.height(18.dp))
    RelayConnectionCard(
        settings = settings,
        mobileRelayActive = mobileRelayActive,
        uploadStatus = uploadStatus,
        onEditSetup = onEditSetup,
    )
    Spacer(modifier = Modifier.height(18.dp))
    PrimaryRelayButton(
        text = if (uploading) "Sende..." else "Test senden",
        enabled = canUpload,
        onClick = onUpload,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun HeaderBlock(
    title: String,
    subtitle: String,
    mobileRelayActive: Boolean,
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge,
            color = AppText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = AppMuted,
            modifier = Modifier.padding(top = 6.dp),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 20.dp),
        ) {
            MiniStatus(label = "100%", active = true)
            MiniStatus(
                label = if (mobileRelayActive) "Mobile active" else "Waiting",
                active = mobileRelayActive,
            )
        }
    }
}

@Composable
private fun RingDashboardCard(
    mobileRelayActive: Boolean,
    lastSync: String,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(430.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        AppTeal.copy(alpha = 0.96f),
                        AppInk.copy(alpha = 0.98f),
                        AppBlack,
                    ),
                ),
            )
            .border(1.dp, AppLine.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            .padding(18.dp),
    ) {
        LargeRingVisual(
            modifier = Modifier
                .size(250.dp)
                .align(Alignment.CenterEnd)
                .offset(x = 82.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.72f),
            verticalArrangement = Arrangement.spacedBy(15.dp),
        ) {
            MetricRow("Activity", "15,940", "steps", "124 minutes", 0.70f)
            MetricRow("Readiness", "90", "Perfect", "Stable", 0.84f)
            MetricRow("Sleep", "8 h 40", "min", "Good rhythm", 0.78f)
            MetricRow("Avg. Heart Rate", "89", "BPM", "Last relay sample", 0.45f)
            MetricRow("HRV", "84", "ms", "Balanced", 0.62f)
            MetricRow("Oxygen in blood", "98", "%", "Normal", 0.88f)
            Spacer(modifier = Modifier.weight(1f))
            StatusPill(
                label = if (mobileRelayActive) "Last sync $lastSync" else "Relay standby",
                active = mobileRelayActive,
            )
        }
    }
}

@Composable
private fun RelayConnectionCard(
    settings: RelaySettings,
    mobileRelayActive: Boolean,
    uploadStatus: String,
    onEditSetup: () -> Unit,
) {
    SetupCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Relay Status",
                    style = MaterialTheme.typography.titleMedium,
                    color = AppText,
                )
                Text(
                    text = uploadStatus,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            StatusDot(active = mobileRelayActive)
        }
        Spacer(modifier = Modifier.height(12.dp))
        InfoLine("HA", settings.homeAssistantUrl.ifBlank { "Nicht gesetzt" })
        InfoLine("Ring", compactRingId(settings.ringId))
        InfoLine("Intervall", "${settings.syncIntervalMinutes} min")
        Spacer(modifier = Modifier.height(14.dp))
        SecondaryRelayButton(
            text = "Verbindung bearbeiten",
            onClick = onEditSetup,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ActivityScreen() {
    HeaderBlock(
        title = "Activity",
        subtitle = "Heute",
        mobileRelayActive = true,
    )
    Spacer(modifier = Modifier.height(22.dp))
    InsightCard(
        title = "Steps",
        value = "15,940",
        unit = "steps",
        progress = 0.78f,
    )
    Spacer(modifier = Modifier.height(14.dp))
    InsightCard(
        title = "Calories",
        value = "594",
        unit = "kcal",
        progress = 0.52f,
    )
    Spacer(modifier = Modifier.height(14.dp))
    TimelineCard(
        title = "Movement",
        values = listOf(0.15f, 0.35f, 0.22f, 0.64f, 0.48f, 0.82f, 0.58f),
    )
}

@Composable
private fun SleepScreen() {
    HeaderBlock(
        title = "Sleep",
        subtitle = "Letzte Nacht",
        mobileRelayActive = true,
    )
    Spacer(modifier = Modifier.height(22.dp))
    InsightCard(
        title = "Sleep duration",
        value = "8 h 40",
        unit = "min",
        progress = 0.82f,
    )
    Spacer(modifier = Modifier.height(14.dp))
    InsightCard(
        title = "Readiness",
        value = "90",
        unit = "Perfect",
        progress = 0.90f,
    )
    Spacer(modifier = Modifier.height(14.dp))
    TimelineCard(
        title = "Sleep stages",
        values = listOf(0.62f, 0.38f, 0.78f, 0.52f, 0.86f, 0.44f, 0.70f),
    )
}

@Composable
private fun MoreScreen(
    settings: RelaySettings,
    uploadStatus: String,
    uploading: Boolean,
    canUpload: Boolean,
    onUpload: () -> Unit,
    onOpenSetupFlow: () -> Unit,
    onHomeAssistantUrlChange: (String) -> Unit,
    onRelayTokenChange: (String) -> Unit,
    onRelayIdChange: (String) -> Unit,
    onRingIdChange: (String) -> Unit,
    onSyncIntervalChange: (Int) -> Unit,
    onSave: () -> Unit,
) {
    Text(
        text = "More",
        style = MaterialTheme.typography.headlineLarge,
        color = AppText,
    )
    Text(
        text = "Relay und Verbindung",
        style = MaterialTheme.typography.bodyLarge,
        color = AppMuted,
        modifier = Modifier.padding(top = 6.dp, bottom = 18.dp),
    )
    SetupCard {
        RelayTextField(
            value = settings.homeAssistantUrl,
            onValueChange = onHomeAssistantUrlChange,
            label = "HA-Link",
            placeholder = "https://ha.example.net",
            keyboardType = KeyboardType.Uri,
        )
        RelayTextField(
            value = settings.relayToken,
            onValueChange = onRelayTokenChange,
            label = "Relay-Token",
            placeholder = "fitorb_relay_...",
            keyboardType = KeyboardType.Password,
            password = true,
        )
        RelayTextField(
            value = settings.relayId,
            onValueChange = onRelayIdChange,
            label = "Relay-ID",
            placeholder = "android-pixel",
            keyboardType = KeyboardType.Text,
        )
        RelayTextField(
            value = settings.ringId,
            onValueChange = onRingIdChange,
            label = "Ring ID",
            placeholder = "AA:BB:CC:DD:EE:FF",
            keyboardType = KeyboardType.Text,
        )
        IntervalSlider(
            syncInterval = settings.syncIntervalMinutes,
            onSyncIntervalChange = onSyncIntervalChange,
        )
        StatusPill(label = uploadStatus, active = uploadStatus.startsWith("OK"))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SecondaryRelayButton(
                text = "Speichern",
                onClick = onSave,
                modifier = Modifier.weight(1f),
            )
            PrimaryRelayButton(
                text = if (uploading) "Sende..." else "Test",
                enabled = canUpload,
                onClick = onUpload,
                modifier = Modifier.weight(1f),
            )
        }
    }
    Spacer(modifier = Modifier.height(14.dp))
    SecondaryRelayButton(
        text = "Setup neu öffnen",
        onClick = onOpenSetupFlow,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun MetricRow(
    label: String,
    value: String,
    unit: String,
    detail: String,
    progress: Float,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = AppMuted,
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineSmall,
                    color = AppText,
                    fontWeight = FontWeight.Light,
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = unit,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppMuted,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = AppDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        MiniChart(progress = progress)
    }
}

@Composable
private fun InsightCard(
    title: String,
    value: String,
    unit: String,
    progress: Float,
) {
    SetupCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppMuted,
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.displaySmall,
                        color = AppText,
                        fontWeight = FontWeight.Light,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = unit,
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppMuted,
                        modifier = Modifier.padding(bottom = 9.dp),
                    )
                }
            }
            MiniChart(progress = progress, modifier = Modifier.size(70.dp))
        }
    }
}

@Composable
private fun TimelineCard(
    title: String,
    values: List<Float>,
) {
    SetupCard {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = AppText,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
        ) {
            val step = size.width / (values.size - 1).coerceAtLeast(1)
            val points = values.mapIndexed { index, value ->
                Offset(
                    x = step * index,
                    y = size.height - (size.height * value.coerceIn(0f, 1f)),
                )
            }
            for (index in 0 until points.lastIndex) {
                drawLine(
                    color = AppGreen,
                    start = points[index],
                    end = points[index + 1],
                    strokeWidth = 5f,
                    cap = StrokeCap.Round,
                )
            }
            points.forEach { point ->
                drawCircle(color = AppSilver, radius = 4f, center = point)
            }
        }
    }
}

@Composable
private fun FloatingBottomBar(
    activeTab: String,
    onTabSelected: (RelayTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = AppPanel.copy(alpha = 0.94f),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            AppLine.copy(alpha = 0.6f),
        ),
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            RelayTab.entries.forEach { tab ->
                val selected = activeTab == tab.key
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) AppGreen.copy(alpha = 0.14f) else Color.Transparent)
                        .clickable { onTabSelected(tab) }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(25.dp)
                            .clip(CircleShape)
                            .background(if (selected) AppGreen else AppPanelSoft),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = tab.mark,
                            color = if (selected) Color.White else AppMuted,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Text(
                        text = tab.label,
                        color = if (selected) AppText else AppMuted,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 5.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SetupCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(AppPanel.copy(alpha = 0.88f))
            .border(1.dp, AppLine.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
private fun RelayTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    keyboardType: KeyboardType,
    password: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = AppGreen,
            unfocusedBorderColor = AppLine,
            focusedLabelColor = AppGreen,
            unfocusedLabelColor = AppMuted,
            cursorColor = AppGreen,
            focusedTextColor = AppText,
            unfocusedTextColor = AppText,
            focusedPlaceholderColor = AppDim,
            unfocusedPlaceholderColor = AppDim,
        ),
        shape = RoundedCornerShape(8.dp),
    )
}

@Composable
private fun IntervalSlider(
    syncInterval: Int,
    onSyncIntervalChange: (Int) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Sync-Intervall",
                style = MaterialTheme.typography.bodyMedium,
                color = AppMuted,
            )
            Text(
                text = "$syncInterval min",
                style = MaterialTheme.typography.bodyMedium,
                color = AppText,
            )
        }
        Slider(
            value = syncInterval.toFloat(),
            onValueChange = { value ->
                onSyncIntervalChange(
                    value.toInt().coerceIn(
                        MIN_SYNC_INTERVAL_MINUTES,
                        MAX_SYNC_INTERVAL_MINUTES,
                    ),
                )
            },
            valueRange = MIN_SYNC_INTERVAL_MINUTES.toFloat()..
                MAX_SYNC_INTERVAL_MINUTES.toFloat(),
            steps = MAX_SYNC_INTERVAL_MINUTES - MIN_SYNC_INTERVAL_MINUTES - 1,
        )
    }
}

@Composable
private fun PrimaryRelayButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(52.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = AppGreen,
            contentColor = Color.White,
            disabledContainerColor = AppPanelSoft,
            disabledContentColor = AppDim,
        ),
        shape = RoundedCornerShape(26.dp),
    ) {
        Text(text = text, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SecondaryRelayButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = AppText),
        border = androidx.compose.foundation.BorderStroke(1.dp, AppLine),
        shape = RoundedCornerShape(26.dp),
    ) {
        Text(text = text)
    }
}

@Composable
private fun StatusPill(
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(26.dp))
            .background(if (active) AppGreen.copy(alpha = 0.16f) else AppPanelSoft)
            .border(
                1.dp,
                if (active) AppGreen.copy(alpha = 0.45f) else AppLine,
                RoundedCornerShape(26.dp),
            )
            .padding(horizontal = 13.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatusDot(active = active, size = 7)
        Text(
            text = label,
            color = if (active) AppText else AppMuted,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MiniStatus(
    label: String,
    active: Boolean,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        StatusDot(active = active, size = 8)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = AppText,
        )
    }
}

@Composable
private fun StatusDot(active: Boolean, size: Int = 12) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(if (active) AppGreen else AppWarning),
    )
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = AppMuted,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = AppText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(start = 14.dp)
                .weight(1f),
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun TopStatusRow(label: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "9:41",
            style = MaterialTheme.typography.bodyMedium,
            color = AppText,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = AppMuted,
        )
    }
}

@Composable
private fun SetupDots(
    step: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(2) { index ->
            Box(
                modifier = Modifier
                    .width(if (step == index) 28.dp else 8.dp)
                    .height(8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (step == index) AppGreen else AppLine),
            )
        }
    }
}

@Composable
private fun RadarRingVisual(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val ringRadius = 42f
        val maxRadius = minOf(size.width, size.height) * 0.42f
        for (index in 1..4) {
            drawCircle(
                color = AppSilver.copy(alpha = 0.18f / index),
                radius = maxRadius * index / 4f,
                center = center,
                style = Stroke(width = 2f),
            )
        }
        drawCircle(
            color = AppSilver.copy(alpha = 0.92f),
            radius = ringRadius,
            center = center,
            style = Stroke(width = 13f),
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.45f),
            radius = ringRadius - 10f,
            center = center,
            style = Stroke(width = 2f),
        )
        drawCircle(
            color = AppBlack,
            radius = ringRadius - 18f,
            center = center,
        )
    }
}

@Composable
private fun LargeRingVisual(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val width = size.width * 0.68f
        val height = size.height * 0.96f
        val left = size.width * 0.16f
        val top = size.height * 0.02f
        drawOval(
            brush = Brush.linearGradient(
                listOf(
                    Color.White,
                    AppSilver,
                    AppLine,
                    Color.White.copy(alpha = 0.88f),
                ),
            ),
            topLeft = Offset(left, top),
            size = Size(width, height),
            style = Stroke(width = 32f),
        )
        drawOval(
            color = AppBlack.copy(alpha = 0.98f),
            topLeft = Offset(left + 34f, top + 44f),
            size = Size(width - 68f, height - 88f),
        )
        drawOval(
            color = AppGreen.copy(alpha = 0.28f),
            topLeft = Offset(left + 86f, top + height * 0.58f),
            size = Size(width - 172f, 52f),
            style = Stroke(width = 6f),
        )
        drawCircle(
            color = AppSilver,
            radius = 16f,
            center = Offset(left + width * 0.48f, top + height * 0.42f),
        )
        drawCircle(
            color = AppBlack,
            radius = 7f,
            center = Offset(left + width * 0.48f, top + height * 0.42f),
        )
    }
}

@Composable
private fun MiniChart(
    progress: Float,
    modifier: Modifier = Modifier.size(50.dp),
) {
    Canvas(modifier = modifier) {
        val clamped = progress.coerceIn(0f, 1f)
        drawArc(
            color = AppLine,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            style = Stroke(width = 5f, cap = StrokeCap.Round),
        )
        drawArc(
            color = AppGreen,
            startAngle = 180f,
            sweepAngle = 180f * clamped,
            useCenter = false,
            style = Stroke(width = 5f, cap = StrokeCap.Round),
        )
    }
}

@Composable
private fun AppBackgroundBrush(): Brush =
    Brush.verticalGradient(
        listOf(
            AppTeal,
            AppBlack,
            Color(0xFF101111),
        ),
    )

private fun RelaySettings.isReadyForUpload(): Boolean =
    homeAssistantUrl.trim().startsWith("https://", ignoreCase = true) &&
        relayToken.trim().startsWith("fitorb_relay_") &&
        relayId.trim().isNotEmpty() &&
        ringId.trim().isNotEmpty()

private fun compactRingId(ringId: String): String =
    ringId.ifBlank { "Ring nicht gesetzt" }
