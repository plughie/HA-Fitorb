package io.github.ichwars.fitorb.relay.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.ichwars.fitorb.relay.BuildConfig
import io.github.ichwars.fitorb.relay.R
import io.github.ichwars.fitorb.relay.data.RelaySampleDto
import io.github.ichwars.fitorb.relay.data.RelaySampleValue
import io.github.ichwars.fitorb.relay.settings.MAX_STEP_GOAL_STEPS
import io.github.ichwars.fitorb.relay.network.RelayUploadException
import io.github.ichwars.fitorb.relay.settings.MAX_SYNC_INTERVAL_MINUTES
import io.github.ichwars.fitorb.relay.settings.MIN_STEP_GOAL_STEPS
import io.github.ichwars.fitorb.relay.settings.MIN_SYNC_INTERVAL_MINUTES
import io.github.ichwars.fitorb.relay.settings.RelaySettings
import io.github.ichwars.fitorb.relay.settings.STEP_GOAL_INCREMENT_STEPS
import io.github.ichwars.fitorb.relay.sync.RelaySyncResult
import java.text.NumberFormat
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
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
private val SleepDeepColor = Color(0xFF25D4BE)
private val SleepLightColor = Color(0xFF17A8D8)
private val SleepRemColor = Color(0xFF3AF24C)
private val SleepAwakeColor = Color(0xFFFFA629)

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
    @StringRes val labelRes: Int,
    val mark: String,
) {
    Home("home", R.string.tab_home, "H"),
    Activity("activity", R.string.tab_activity, "A"),
    Sleep("sleep", R.string.tab_sleep, "S"),
    More("more", R.string.tab_more, "M"),
}

@Composable
fun FitorbRelayApp(
    initialSettings: RelaySettings,
    defaultRelayId: String,
    appVersion: String,
    onSave: (RelaySettings) -> Unit,
    onUpload: suspend (RelaySettings) -> RelaySyncResult,
    onLoadSamples: suspend (RelaySettings) -> List<RelaySampleDto>,
) {
    var homeAssistantUrl by rememberSaveable {
        mutableStateOf(initialSettings.homeAssistantUrl)
    }
    var relayToken by rememberSaveable { mutableStateOf(initialSettings.relayToken) }
    var relayId by rememberSaveable { mutableStateOf(initialSettings.relayId) }
    var ringId by rememberSaveable { mutableStateOf(initialSettings.ringId) }
    var ringName by rememberSaveable { mutableStateOf(initialSettings.ringName) }
    var syncInterval by rememberSaveable {
        mutableStateOf(
            initialSettings.syncIntervalMinutes.coerceIn(
                MIN_SYNC_INTERVAL_MINUTES,
                MAX_SYNC_INTERVAL_MINUTES,
            ),
        )
    }
    var stepGoal by rememberSaveable {
        mutableStateOf(
            initialSettings.stepGoal.coerceIn(
                MIN_STEP_GOAL_STEPS,
                MAX_STEP_GOAL_STEPS,
            ),
        )
    }
    var setupStep by rememberSaveable {
        mutableStateOf(if (initialSettings.isReadyForUpload()) 2 else 0)
    }
    var activeTab by rememberSaveable { mutableStateOf(RelayTab.Home.key) }
    var uploadState by remember { mutableStateOf("ready") }
    var uploadError by rememberSaveable { mutableStateOf("") }
    var acceptedCount by rememberSaveable { mutableStateOf<Int?>(null) }
    var duplicateCount by rememberSaveable { mutableStateOf<Int?>(null) }
    var rejectedCount by rememberSaveable { mutableStateOf<Int?>(null) }
    var hasUploaded by rememberSaveable { mutableStateOf(false) }
    var mobileRelayActive by rememberSaveable { mutableStateOf(false) }
    var uploading by remember { mutableStateOf(false) }
    var latestRingSamples by remember { mutableStateOf<List<RelaySampleDto>>(emptyList()) }
    val scope = rememberCoroutineScope()

    fun currentSettings() = RelaySettings(
        homeAssistantUrl = homeAssistantUrl,
        relayToken = relayToken,
        relayId = relayId.ifBlank { defaultRelayId },
        ringId = ringId,
        syncIntervalMinutes = syncInterval,
        ringName = ringName,
        stepGoal = stepGoal,
    )

    fun saveCurrentSettings() = onSave(currentSettings())

    fun uploadCurrentSettings() {
        val settings = currentSettings()
        saveCurrentSettings()
        uploading = true
        uploadState = "sending"
        uploadError = ""
        scope.launch {
            try {
                val result = onUpload(settings)
                val ack = result.ack
                latestRingSamples = result.capturedSamples.ifEmpty { result.uploadedSamples }
                acceptedCount = ack.accepted.size
                duplicateCount = ack.duplicates.size
                rejectedCount = ack.rejected.size
                mobileRelayActive = result.capturedSamples.isNotEmpty()
                hasUploaded = true
                uploadState = "ok"
            } catch (exception: RelayUploadException) {
                uploadError = exception.message.orEmpty()
                mobileRelayActive = false
                uploadState = "error"
            } catch (exception: IllegalArgumentException) {
                uploadError = exception.message.orEmpty()
                mobileRelayActive = false
                uploadState = "error"
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                uploadError = exception.message.orEmpty()
                mobileRelayActive = false
                uploadState = "error"
            } finally {
                uploading = false
            }
        }
    }

    val settings = currentSettings()
    val ringSampleKey = settings.ringId.trim()
    LaunchedEffect(setupStep, ringSampleKey) {
        if (setupStep >= 2 && ringSampleKey.isNotBlank()) {
            latestRingSamples = runCatching {
                onLoadSamples(settings.copy(ringId = ringSampleKey))
            }.getOrDefault(emptyList())
            if (latestRingSamples.isNotEmpty()) {
                mobileRelayActive = true
            }
        }
    }
    val canUpload = settings.isReadyForUpload() && !uploading
    val unknownError = stringResource(R.string.unknown_error)
    val uploadStatus = when (uploadState) {
        "sending" -> stringResource(R.string.status_sending)
        "ok" -> stringResource(
            R.string.status_ok,
            acceptedCount ?: 0,
            duplicateCount ?: 0,
            rejectedCount ?: 0,
        )
        "error" -> stringResource(
            R.string.status_error,
            uploadError.ifBlank { unknownError },
        )
        else -> stringResource(R.string.status_ready)
    }
    val uploadOk = uploadState == "ok"
    val lastSync = if (hasUploaded) {
        stringResource(R.string.last_sync_now)
    } else {
        stringResource(R.string.last_sync_none)
    }
    val dashboardSnapshot = RingDashboardSnapshot.from(latestRingSamples)

    MaterialTheme(colorScheme = FitorbColorScheme) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AppBackgroundBrush())
                .statusBarsPadding(),
        ) {
            if (setupStep < 2) {
                SetupFlow(
                    step = setupStep,
                    ringId = ringId,
                    onRingIdChange = { ringId = it },
                    ringName = ringName,
                    onRingNameChange = { ringName = it },
                    homeAssistantUrl = homeAssistantUrl,
                    onHomeAssistantUrlChange = { homeAssistantUrl = it },
                    relayToken = relayToken,
                    onRelayTokenChange = { relayToken = it },
                    relayId = relayId.ifBlank { defaultRelayId },
                    onRelayIdChange = { relayId = it },
                    syncInterval = syncInterval,
                    onSyncIntervalChange = { syncInterval = it },
                    uploadStatus = uploadStatus,
                    uploadOk = uploadOk,
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
                    appVersion = appVersion,
                    mobileRelayActive = mobileRelayActive,
                    lastSync = lastSync,
                    uploadStatus = uploadStatus,
                    uploadOk = uploadOk,
                    acceptedCount = acceptedCount,
                    duplicateCount = duplicateCount,
                    rejectedCount = rejectedCount,
                    dashboardSnapshot = dashboardSnapshot,
                    latestRingSamples = latestRingSamples,
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
                    onRingNameChange = { ringName = it },
                    onSyncIntervalChange = { syncInterval = it },
                    onStepGoalChange = { stepGoal = it },
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
    ringName: String,
    onRingNameChange: (String) -> Unit,
    homeAssistantUrl: String,
    onHomeAssistantUrlChange: (String) -> Unit,
    relayToken: String,
    onRelayTokenChange: (String) -> Unit,
    relayId: String,
    onRelayIdChange: (String) -> Unit,
    syncInterval: Int,
    onSyncIntervalChange: (Int) -> Unit,
    uploadStatus: String,
    uploadOk: Boolean,
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
                ringName = ringName,
                onRingNameChange = onRingNameChange,
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
                ringName = ringName,
                onRingNameChange = onRingNameChange,
                syncInterval = syncInterval,
                onSyncIntervalChange = onSyncIntervalChange,
                uploadStatus = uploadStatus,
                uploadOk = uploadOk,
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
                .navigationBarsPadding()
                .padding(bottom = 10.dp),
        )
    }
}

@Composable
private fun RingSearchStep(
    ringId: String,
    onRingIdChange: (String) -> Unit,
    ringName: String,
    onRingNameChange: (String) -> Unit,
    onContinue: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TopStatusRow(label = stringResource(R.string.setup_bluetooth))
        Spacer(modifier = Modifier.height(30.dp))
        Text(
            text = stringResource(R.string.setup_search_title),
            style = MaterialTheme.typography.headlineLarge,
            color = AppText,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.setup_search_subtitle),
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
            value = ringName,
            onValueChange = onRingNameChange,
            label = stringResource(R.string.ring_name_label),
            placeholder = stringResource(R.string.ring_name_placeholder),
            keyboardType = KeyboardType.Text,
        )
        Spacer(modifier = Modifier.height(12.dp))
        RelayTextField(
            value = ringId,
            onValueChange = onRingIdChange,
            label = stringResource(R.string.ring_id_label),
            placeholder = stringResource(R.string.ring_id_placeholder),
            keyboardType = KeyboardType.Text,
        )
        Spacer(modifier = Modifier.height(18.dp))
        PrimaryRelayButton(
            text = if (ringId.isBlank()) {
                stringResource(R.string.button_next)
            } else {
                stringResource(R.string.button_use_ring)
            },
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
    ringName: String,
    onRingNameChange: (String) -> Unit,
    syncInterval: Int,
    onSyncIntervalChange: (Int) -> Unit,
    uploadStatus: String,
    uploadOk: Boolean,
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
            text = stringResource(R.string.setup_ha_title),
            style = MaterialTheme.typography.headlineLarge,
            color = AppText,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            text = stringResource(R.string.setup_ha_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = AppMuted,
        )
        SetupCard {
            RelayTextField(
                value = homeAssistantUrl,
                onValueChange = onHomeAssistantUrlChange,
                label = stringResource(R.string.ha_link_label),
                placeholder = "https://ha.example.net",
                keyboardType = KeyboardType.Uri,
            )
            RelayTextField(
                value = relayToken,
                onValueChange = onRelayTokenChange,
                label = stringResource(R.string.relay_token_label),
                placeholder = "fitorb_relay_...",
                keyboardType = KeyboardType.Password,
                password = true,
            )
            RelayTextField(
                value = relayId,
                onValueChange = onRelayIdChange,
                label = stringResource(R.string.relay_id_label),
                placeholder = "android-pixel",
                keyboardType = KeyboardType.Text,
            )
            RelayTextField(
                value = ringName,
                onValueChange = onRingNameChange,
                label = stringResource(R.string.ring_name_label),
                placeholder = stringResource(R.string.ring_name_placeholder),
                keyboardType = KeyboardType.Text,
            )
            RelayTextField(
                value = ringId,
                onValueChange = onRingIdChange,
                label = stringResource(R.string.ring_id_label),
                placeholder = stringResource(R.string.ring_id_placeholder),
                keyboardType = KeyboardType.Text,
            )
            IntervalSlider(
                syncInterval = syncInterval,
                onSyncIntervalChange = onSyncIntervalChange,
            )
        }
        StatusPill(
            label = uploadStatus,
            active = uploadOk,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SecondaryRelayButton(
                text = stringResource(R.string.button_back),
                onClick = onBack,
                modifier = Modifier.weight(1f),
            )
            PrimaryRelayButton(
                text = if (uploading) {
                    stringResource(R.string.button_sending)
                } else {
                    stringResource(R.string.button_test_send)
                },
                enabled = canUpload,
                onClick = onUpload,
                modifier = Modifier.weight(1f),
            )
        }
        PrimaryRelayButton(
            text = stringResource(R.string.button_open_dashboard),
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
    appVersion: String,
    mobileRelayActive: Boolean,
    lastSync: String,
    uploadStatus: String,
    uploadOk: Boolean,
    acceptedCount: Int?,
    duplicateCount: Int?,
    rejectedCount: Int?,
    dashboardSnapshot: RingDashboardSnapshot,
    latestRingSamples: List<RelaySampleDto>,
    uploading: Boolean,
    canUpload: Boolean,
    onUpload: () -> Unit,
    onEditSetup: () -> Unit,
    onOpenSetupFlow: () -> Unit,
    onHomeAssistantUrlChange: (String) -> Unit,
    onRelayTokenChange: (String) -> Unit,
    onRelayIdChange: (String) -> Unit,
    onRingIdChange: (String) -> Unit,
    onRingNameChange: (String) -> Unit,
    onSyncIntervalChange: (Int) -> Unit,
    onStepGoalChange: (Int) -> Unit,
    onSave: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 22.dp, top = 28.dp, end = 22.dp, bottom = 24.dp),
            ) {
                when (activeTab) {
                    RelayTab.Activity.key -> ActivityScreen(
                        snapshot = dashboardSnapshot,
                        samples = latestRingSamples,
                        mobileRelayActive = mobileRelayActive,
                        stepGoal = settings.stepGoal,
                    )
                    RelayTab.Sleep.key -> SleepScreen(
                        snapshot = dashboardSnapshot,
                        samples = latestRingSamples,
                        mobileRelayActive = mobileRelayActive,
                    )
                    RelayTab.More.key -> MoreScreen(
                        settings = settings,
                        uploadStatus = uploadStatus,
                        uploadOk = uploadOk,
                        uploading = uploading,
                        canUpload = canUpload,
                        onUpload = onUpload,
                        onOpenSetupFlow = onOpenSetupFlow,
                        onHomeAssistantUrlChange = onHomeAssistantUrlChange,
                        onRelayTokenChange = onRelayTokenChange,
                        onRelayIdChange = onRelayIdChange,
                        onRingIdChange = onRingIdChange,
                        onRingNameChange = onRingNameChange,
                        onSyncIntervalChange = onSyncIntervalChange,
                        onStepGoalChange = onStepGoalChange,
                        onSave = onSave,
                    )
                    else -> HomeScreen(
                        settings = settings,
                        mobileRelayActive = mobileRelayActive,
                        dashboardSnapshot = dashboardSnapshot,
                    )
                }
            }
        }
        FloatingBottomBar(
            activeTab = activeTab,
            onTabSelected = onTabSelected,
            modifier = Modifier
                .navigationBarsPadding()
                .padding(start = 18.dp, top = 8.dp, end = 18.dp, bottom = 22.dp),
        )
    }
}

@Composable
private fun HomeScreen(
    settings: RelaySettings,
    mobileRelayActive: Boolean,
    dashboardSnapshot: RingDashboardSnapshot,
) {
    HeaderBlock(
        title = stringResource(R.string.app_name),
        subtitle = ringDisplayName(settings),
        batteryPercent = dashboardSnapshot.batteryPercent,
        mobileRelayActive = mobileRelayActive,
    )
    Spacer(modifier = Modifier.height(22.dp))
    RingDashboardCard(
        snapshot = dashboardSnapshot,
        stepGoal = settings.stepGoal,
    )
}

@Composable
private fun HeaderBlock(
    title: String,
    subtitle: String,
    batteryPercent: Int?,
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
            MiniStatus(
                label = batteryPercent?.let { "$it%" } ?: "--%",
                active = batteryPercent != null,
            )
            MiniStatus(
                label = if (mobileRelayActive) {
                    stringResource(R.string.connected)
                } else {
                    stringResource(R.string.disconnected)
                },
                active = mobileRelayActive,
            )
        }
    }
}

@Composable
private fun RingDashboardCard(
    snapshot: RingDashboardSnapshot,
    stepGoal: Int,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(520.dp)
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
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            MetricRow(
                stringResource(R.string.metric_steps),
                ringIntText(snapshot.steps),
                stringResource(R.string.unit_steps),
                ringSampleDetail(snapshot.steps),
                progressFrom(snapshot.steps, stepGoal),
            )
            MetricRow(
                stringResource(R.string.metric_calories),
                ringIntText(snapshot.calories),
                stringResource(R.string.unit_kcal),
                ringSampleDetail(snapshot.calories),
                progressFrom(snapshot.calories, 800),
            )
            MetricRow(
                stringResource(R.string.metric_distance),
                ringIntText(snapshot.distanceMeters),
                stringResource(R.string.unit_meter),
                ringSampleDetail(snapshot.distanceMeters),
                progressFrom(snapshot.distanceMeters, 10_000),
            )
            MetricRow(
                stringResource(R.string.metric_heart_rate),
                ringIntText(snapshot.heartRate),
                stringResource(R.string.unit_bpm),
                ringSampleDetail(snapshot.heartRate),
                progressFrom(snapshot.heartRate, 180),
            )
            MetricRow(
                stringResource(R.string.metric_spo2),
                ringIntText(snapshot.spo2),
                stringResource(R.string.unit_percent),
                ringSampleDetail(snapshot.spo2),
                progressFrom(snapshot.spo2, 100),
            )
            MetricRow(
                stringResource(R.string.metric_sleep),
                sleepText(snapshot.sleepMinutes),
                stringResource(R.string.unit_min),
                ringSampleDetail(snapshot.sleepMinutes),
                progressFrom(snapshot.sleepMinutes, 540),
            )
        }
    }
}

@Composable
private fun RelayConnectionCard(
    settings: RelaySettings,
    mobileRelayActive: Boolean,
    uploadStatus: String,
    uploadOk: Boolean,
    onEditSetup: () -> Unit,
) {
    val notSet = stringResource(R.string.not_set)
    val homeAssistantUrl = settings.homeAssistantUrl.trim()
    val ringId = compactRingId(settings.ringId)
    SetupCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.relay_status),
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
        StatusPill(label = uploadStatus, active = uploadOk)
        Spacer(modifier = Modifier.height(12.dp))
        InfoLine(
            stringResource(R.string.info_ha),
            if (homeAssistantUrl.isBlank()) notSet else homeAssistantUrl,
        )
        InfoLine(
            stringResource(R.string.info_ring),
            if (ringId.isBlank()) notSet else ringId,
        )
        InfoLine(
            stringResource(R.string.info_interval),
            "${settings.syncIntervalMinutes} ${stringResource(R.string.unit_min)}",
        )
        Spacer(modifier = Modifier.height(14.dp))
        SecondaryRelayButton(
            text = stringResource(R.string.button_edit_connection),
            onClick = onEditSetup,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ActivityScreen(
    snapshot: RingDashboardSnapshot,
    samples: List<RelaySampleDto>,
    mobileRelayActive: Boolean,
    stepGoal: Int,
) {
    val normalizedStepGoal = stepGoal.coerceIn(MIN_STEP_GOAL_STEPS, MAX_STEP_GOAL_STEPS)
    HeaderBlock(
        title = stringResource(R.string.activity_title),
        subtitle = stringResource(R.string.activity_subtitle),
        batteryPercent = snapshot.batteryPercent,
        mobileRelayActive = mobileRelayActive,
    )
    Spacer(modifier = Modifier.height(22.dp))
    ActivityTrendCard(
        snapshot = snapshot,
        samples = samples,
        stepGoal = normalizedStepGoal,
    )
    Spacer(modifier = Modifier.height(16.dp))
    ActivityGoalCard(
        snapshot = snapshot,
        stepGoal = normalizedStepGoal,
    )
}

@Composable
private fun ActivityTrendCard(
    snapshot: RingDashboardSnapshot,
    samples: List<RelaySampleDto>,
    stepGoal: Int,
) {
    var selectedPeriod by rememberSaveable { mutableStateOf("day") }
    SetupCard {
        ActivityPeriodSelector(
            selectedPeriod = selectedPeriod,
            onSelectedPeriodChange = { selectedPeriod = it },
        )
        ActivityBarChart(
            samples = samples,
            stepGoal = stepGoal,
            modifier = Modifier
                .fillMaxWidth()
                .height(168.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("06", color = AppMuted, style = MaterialTheme.typography.labelSmall)
            Text("12", color = AppMuted, style = MaterialTheme.typography.labelSmall)
            Text("18", color = AppMuted, style = MaterialTheme.typography.labelSmall)
            Text("24", color = AppMuted, style = MaterialTheme.typography.labelSmall)
        }
        ActivityStatsGrid(snapshot = snapshot)
    }
}

@Composable
private fun ActivityPeriodSelector(
    selectedPeriod: String,
    onSelectedPeriodChange: (String) -> Unit,
) {
    val periods = listOf(
        "day" to stringResource(R.string.activity_period_day),
        "week" to stringResource(R.string.activity_period_week),
        "month" to stringResource(R.string.activity_period_month),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(AppPanelSoft.copy(alpha = 0.92f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        periods.forEach { (key, label) ->
            val selected = selectedPeriod == key
            val interactionSource = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (selected) AppSilver.copy(alpha = 0.14f) else Color.Transparent)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                    ) { onSelectedPeriodChange(key) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    color = if (selected) AppText else AppMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun ActivityBarChart(
    samples: List<RelaySampleDto>,
    stepGoal: Int,
    modifier: Modifier = Modifier,
) {
    val bars = remember(samples) { activityHourBars(samples) }
    Canvas(modifier = modifier) {
        val gridRows = 4
        val leftInset = 6f
        val rightInset = 6f
        val chartWidth = size.width - leftInset - rightInset
        val bottom = size.height - 8f
        repeat(gridRows) { index ->
            val y = 8f + (bottom - 8f) * index / (gridRows - 1)
            drawLine(
                color = AppLine.copy(alpha = 0.42f),
                start = Offset(leftInset, y),
                end = Offset(size.width - rightInset, y),
                strokeWidth = 1.2f,
            )
        }
        listOf(6, 12, 18).forEach { hour ->
            val x = leftInset + chartWidth * hour / 24f
            drawLine(
                color = AppLine.copy(alpha = 0.30f),
                start = Offset(x, 8f),
                end = Offset(x, bottom),
                strokeWidth = 1.2f,
            )
        }
        val maxValue = maxOf(bars.maxOrNull() ?: 0f, stepGoal / 3f, 1f)
        val slotWidth = chartWidth / 24f
        val strokeWidth = (slotWidth * 0.42f).coerceIn(3f, 12f)
        bars.forEachIndexed { index, value ->
            if (value > 0f) {
                val normalized = (value / maxValue).coerceIn(0.06f, 1f)
                val x = leftInset + slotWidth * index + slotWidth / 2f
                val y = bottom - (bottom - 14f) * normalized
                drawLine(
                    color = AppGreen,
                    start = Offset(x, bottom),
                    end = Offset(x, y),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

@Composable
private fun ActivityStatsGrid(snapshot: RingDashboardSnapshot) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActivityStatCell(
                label = stringResource(R.string.metric_steps),
                value = formattedInt(snapshot.steps),
                unit = stringResource(R.string.unit_steps),
                modifier = Modifier.weight(1f),
            )
            ActivityStatCell(
                label = stringResource(R.string.metric_distance),
                value = distanceValueText(snapshot.distanceMeters),
                unit = distanceUnitText(snapshot.distanceMeters),
                modifier = Modifier.weight(1f),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(AppLine.copy(alpha = 0.45f)),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActivityStatCell(
                label = stringResource(R.string.metric_calories),
                value = ringIntText(snapshot.calories),
                unit = stringResource(R.string.unit_kcal),
                modifier = Modifier.weight(1f),
            )
            ActivityStatCell(
                label = stringResource(R.string.activity_duration),
                value = ringIntText(snapshot.activityMinutes),
                unit = stringResource(R.string.unit_min),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ActivityStatCell(
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = AppMuted,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 3.dp),
        ) {
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
            )
        }
    }
}

@Composable
private fun ActivityGoalCard(
    snapshot: RingDashboardSnapshot,
    stepGoal: Int,
) {
    val progress = stepProgress(snapshot.steps, stepGoal)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        AppPanelSoft.copy(alpha = 0.95f),
                        AppInk.copy(alpha = 0.98f),
                    ),
                ),
            )
            .border(1.dp, AppLine.copy(alpha = 0.32f), RoundedCornerShape(22.dp))
            .padding(18.dp),
    ) {
        Column {
            Text(
                text = stringResource(R.string.activity_title),
                style = MaterialTheme.typography.titleLarge,
                color = AppText,
            )
            Spacer(modifier = Modifier.height(16.dp))
            WeekdayProgressRow(progress = progress)
            Spacer(modifier = Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    ActivitySideMetric(
                        label = stringResource(R.string.metric_calories),
                        value = ringIntText(snapshot.calories),
                        unit = stringResource(R.string.unit_kcal),
                    )
                    ActivitySideMetric(
                        label = stringResource(R.string.activity_duration),
                        value = ringIntText(snapshot.activityMinutes),
                        unit = stringResource(R.string.unit_min),
                    )
                }
                StepProgressRing(
                    steps = snapshot.steps,
                    stepGoal = stepGoal,
                    progress = progress,
                )
            }
        }
    }
}

@Composable
private fun WeekdayProgressRow(progress: Float) {
    val todayIndex = (LocalDate.now().dayOfWeek.value - 1).coerceIn(0, 6)
    val labels = listOf(
        stringResource(R.string.weekday_mon),
        stringResource(R.string.weekday_tue),
        stringResource(R.string.weekday_wed),
        stringResource(R.string.weekday_thu),
        stringResource(R.string.weekday_fri),
        stringResource(R.string.weekday_sat),
        stringResource(R.string.weekday_sun),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(AppSilver.copy(alpha = 0.09f))
            .padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        labels.forEachIndexed { index, label ->
            DayProgressItem(
                label = label,
                progress = if (index == todayIndex) progress else 0f,
                selected = index == todayIndex,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun DayProgressItem(
    label: String,
    progress: Float,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) AppSilver.copy(alpha = 0.11f) else Color.Transparent)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(
            modifier = Modifier
                .width(34.dp)
                .height(18.dp),
        ) {
            drawArc(
                color = AppLine,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                style = Stroke(width = 4f, cap = StrokeCap.Round),
            )
            if (progress > 0f) {
                drawArc(
                    color = AppGreen,
                    startAngle = 180f,
                    sweepAngle = 180f * progress.coerceIn(0f, 1f),
                    useCenter = false,
                    style = Stroke(width = 4f, cap = StrokeCap.Round),
                )
            }
        }
        Text(
            text = label,
            color = if (selected) AppText else AppMuted,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}

@Composable
private fun ActivitySideMetric(
    label: String,
    value: String,
    unit: String,
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = AppMuted,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 5.dp),
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                color = AppText,
                fontWeight = FontWeight.Light,
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = unit,
                style = MaterialTheme.typography.bodySmall,
                color = AppMuted,
            )
        }
    }
}

@Composable
private fun StepProgressRing(
    steps: Int?,
    stepGoal: Int,
    progress: Float,
) {
    Box(
        modifier = Modifier.size(150.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawArc(
                color = AppLine.copy(alpha = 0.72f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = 14f, cap = StrokeCap.Round),
            )
            if (progress > 0f) {
                drawArc(
                    color = AppGreen,
                    startAngle = -90f,
                    sweepAngle = 360f * progress.coerceIn(0f, 1f),
                    useCenter = false,
                    style = Stroke(width = 14f, cap = StrokeCap.Round),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = formattedInt(steps),
                style = MaterialTheme.typography.headlineSmall,
                color = AppText,
                fontWeight = FontWeight.Light,
            )
            Text(
                text = stringResource(R.string.unit_steps),
                style = MaterialTheme.typography.bodyMedium,
                color = AppMuted,
            )
            Text(
                text = stringResource(R.string.step_goal_short, formattedInt(stepGoal)),
                style = MaterialTheme.typography.labelSmall,
                color = AppDim,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun SleepScreen(
    snapshot: RingDashboardSnapshot,
    samples: List<RelaySampleDto>,
    mobileRelayActive: Boolean,
) {
    val sleepSegments = remember(samples) { sleepStageSegments(samples) }
    var selectedPeriod by rememberSaveable { mutableStateOf("day") }
    SleepOverviewCard(
        snapshot = snapshot,
        samples = samples,
        segments = sleepSegments,
        selectedPeriod = selectedPeriod,
        onSelectedPeriodChange = { selectedPeriod = it },
    )
    Spacer(modifier = Modifier.height(16.dp))
    SleepQualityCard(
        snapshot = snapshot,
        segments = sleepSegments,
    )
    Spacer(modifier = Modifier.height(10.dp))
    Text(
        text = if (mobileRelayActive) stringResource(R.string.connected) else stringResource(R.string.disconnected),
        style = MaterialTheme.typography.labelSmall,
        color = if (mobileRelayActive) AppGreen else AppDim,
        modifier = Modifier.padding(start = 8.dp),
    )
}

@Composable
private fun SleepOverviewCard(
    snapshot: RingDashboardSnapshot,
    samples: List<RelaySampleDto>,
    segments: List<SleepStageSegment>,
    selectedPeriod: String,
    onSelectedPeriodChange: (String) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        AppPanelSoft.copy(alpha = 0.98f),
                        Color(0xFF1B2928).copy(alpha = 0.96f),
                        AppInk.copy(alpha = 0.98f),
                    ),
                ),
            )
            .border(1.dp, AppLine.copy(alpha = 0.34f), RoundedCornerShape(24.dp))
            .padding(16.dp),
    ) {
        Column {
            Text(
                text = stringResource(R.string.sleep_title),
                style = MaterialTheme.typography.titleMedium,
                color = AppText,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Text(
                text = sleepDateText(samples),
                style = MaterialTheme.typography.labelSmall,
                color = AppMuted,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .align(Alignment.CenterHorizontally),
            )
            Spacer(modifier = Modifier.height(18.dp))
            ActivityPeriodSelector(
                selectedPeriod = selectedPeriod,
                onSelectedPeriodChange = onSelectedPeriodChange,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.sleep_total_time),
                style = MaterialTheme.typography.labelSmall,
                color = AppMuted,
            )
            Text(
                text = sleepClockText(snapshot.sleepMinutes),
                style = MaterialTheme.typography.displaySmall,
                color = AppText,
                fontWeight = FontWeight.Light,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                text = sleepDateText(samples),
                style = MaterialTheme.typography.bodySmall,
                color = AppText,
                modifier = Modifier.padding(top = 2.dp),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 16.dp)
                    .height(1.dp)
                    .background(AppSilver.copy(alpha = 0.22f)),
            )
            SleepStageTimeline(
                segments = segments,
                samples = samples,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(236.dp),
            )
            SleepStageLegend(
                modifier = Modifier.padding(top = 12.dp),
            )
            if (segments.isEmpty()) {
                Text(
                    text = stringResource(R.string.sleep_stage_missing),
                    style = MaterialTheme.typography.bodySmall,
                    color = AppDim,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun SleepStageTimeline(
    segments: List<SleepStageSegment>,
    samples: List<RelaySampleDto>,
    modifier: Modifier = Modifier,
) {
    val stages = listOf(
        "awake" to stringResource(R.string.sleep_stage_awake),
        "rem" to stringResource(R.string.sleep_stage_rem),
        "light" to stringResource(R.string.sleep_stage_light),
        "deep" to stringResource(R.string.sleep_stage_deep),
    )
    val axisLabels = remember(samples, segments) { sleepAxisLabels(samples, segments) }
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .width(76.dp)
                    .fillMaxHeight()
                    .padding(vertical = 10.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                stages.forEach { (_stage, label) ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = AppMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
                val top = 14f
                val bottom = size.height - 16f
                val rowStep = (bottom - top) / (stages.size - 1).coerceAtLeast(1)
                fun yFor(stage: String): Float {
                    val index = stages.indexOfFirst { (key, _label) -> key == stage }
                        .takeIf { it >= 0 } ?: 2
                    return top + rowStep * index
                }

                stages.indices.forEach { index ->
                    val y = top + rowStep * index
                    drawLine(
                        color = AppSilver.copy(alpha = 0.20f),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.3f,
                    )
                }
                listOf(0.25f, 0.5f, 0.75f).forEach { fraction ->
                    val x = size.width * fraction
                    drawLine(
                        color = AppLine.copy(alpha = 0.20f),
                        start = Offset(x, top),
                        end = Offset(x, bottom),
                        strokeWidth = 1f,
                    )
                }

                if (segments.isEmpty()) {
                    return@Canvas
                }

                val totalMinutes = segments.sumOf { it.minutes }.coerceAtLeast(1)
                val chartStart = 4f
                val chartEnd = size.width - 5f
                val chartWidth = (chartEnd - chartStart).coerceAtLeast(1f)
                var x = chartStart
                var currentY = yFor(segments.first().stage)
                segments.forEachIndexed { index, segment ->
                    val nextX = if (index == segments.lastIndex) {
                        chartEnd
                    } else {
                        (x + chartWidth * segment.minutes / totalMinutes.toFloat())
                            .coerceAtMost(chartEnd)
                    }
                    val color = sleepStageColor(segment.stage)
                    val segmentWidth = (nextX - x).coerceAtLeast(1f)
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                color.copy(alpha = if (segment.stage == "awake") 0.0f else 0.22f),
                                color.copy(alpha = if (segment.stage == "awake") 0.0f else 0.08f),
                                Color.Transparent,
                            ),
                            startY = currentY,
                            endY = bottom + 20f,
                        ),
                        topLeft = Offset(x, currentY),
                        size = Size(segmentWidth, (bottom - currentY + 20f).coerceAtLeast(1f)),
                    )
                    drawLine(
                        color = color.copy(alpha = 0.22f),
                        start = Offset(x, currentY),
                        end = Offset(nextX, currentY),
                        strokeWidth = 13f,
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        color = color,
                        start = Offset(x, currentY),
                        end = Offset(nextX, currentY),
                        strokeWidth = 4.2f,
                        cap = StrokeCap.Round,
                    )

                    val nextSegment = segments.getOrNull(index + 1)
                    if (nextSegment != null) {
                        val nextY = yFor(nextSegment.stage)
                        val connectorColor = sleepStageColor(nextSegment.stage)
                        drawLine(
                            color = connectorColor.copy(alpha = 0.22f),
                            start = Offset(nextX, currentY),
                            end = Offset(nextX, nextY),
                            strokeWidth = 13f,
                            cap = StrokeCap.Round,
                        )
                        drawLine(
                            color = connectorColor,
                            start = Offset(nextX, currentY),
                            end = Offset(nextX, nextY),
                            strokeWidth = 4.2f,
                            cap = StrokeCap.Round,
                        )
                        currentY = nextY
                    }
                    x = nextX
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 88.dp, top = 7.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            axisLabels.forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = AppDim,
                )
            }
        }
    }
}

@Composable
private fun SleepStageLegend(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SleepLegendItem(color = SleepDeepColor, label = stringResource(R.string.sleep_legend_deep))
        SleepLegendItem(color = SleepLightColor, label = stringResource(R.string.sleep_legend_light))
        SleepLegendItem(color = SleepRemColor, label = stringResource(R.string.sleep_legend_rem))
        SleepLegendItem(color = SleepAwakeColor, label = stringResource(R.string.sleep_legend_awake))
    }
}

@Composable
private fun SleepLegendItem(
    color: Color,
    label: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = AppMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SleepQualityCard(
    snapshot: RingDashboardSnapshot,
    segments: List<SleepStageSegment>,
) {
    val stageMinutes = remember(segments) { sleepStageMinutes(segments) }
    val lightMinutes = snapshot.sleepLightMinutes ?: stageMinutes["light"]
    val deepMinutes = snapshot.sleepDeepMinutes ?: stageMinutes["deep"]
    val remMinutes = snapshot.sleepRemMinutes ?: stageMinutes["rem"]
    val totalSleep = snapshot.sleepMinutes ?: listOfNotNull(
        lightMinutes,
        deepMinutes,
        remMinutes,
    ).sum().takeIf { it > 0 }
    val quality = remember(snapshot, segments) { sleepQuality(snapshot, segments) }
    val qualityText = when {
        quality.score == null -> stringResource(R.string.metric_missing_ring_sample)
        quality.score >= 80 -> stringResource(R.string.sleep_quality_good)
        quality.score >= 60 -> stringResource(R.string.sleep_quality_ok)
        else -> stringResource(R.string.sleep_quality_low)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        AppPanel.copy(alpha = 0.96f),
                        AppPanelSoft.copy(alpha = 0.94f),
                        AppInk.copy(alpha = 0.98f),
                    ),
                ),
            )
            .border(1.dp, AppLine.copy(alpha = 0.34f), RoundedCornerShape(22.dp))
            .padding(18.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(15.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.sleep_quality),
                    style = MaterialTheme.typography.titleMedium,
                    color = AppText,
                )
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .border(1.dp, AppMuted.copy(alpha = 0.8f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("?", color = AppMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    SleepQualityMetric(
                        label = stringResource(R.string.sleep_effectiveness),
                        value = quality.effectiveness?.let { "$it%" } ?: "--",
                    )
                    SleepQualityMetric(
                        label = stringResource(R.string.sleep_quality_time),
                        value = sleepClockText(totalSleep),
                    )
                }
                SleepQualityGauge(
                    score = quality.score,
                    label = qualityText,
                    modifier = Modifier.size(132.dp),
                )
            }
            SleepPhaseRow(
                label = stringResource(R.string.sleep_stage_deep),
                minutes = deepMinutes,
                totalMinutes = totalSleep,
                color = SleepDeepColor,
            )
            SleepPhaseRow(
                label = stringResource(R.string.sleep_stage_rem),
                minutes = remMinutes,
                totalMinutes = totalSleep,
                color = SleepRemColor,
            )
            SleepPhaseRow(
                label = stringResource(R.string.sleep_stage_light),
                minutes = lightMinutes,
                totalMinutes = totalSleep,
                color = SleepLightColor,
            )
        }
    }
}

@Composable
private fun SleepQualityMetric(
    label: String,
    value: String,
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = AppMuted,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            color = AppText,
            fontWeight = FontWeight.Light,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun SleepQualityGauge(
    score: Int?,
    label: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 10f
            drawArc(
                color = AppLine.copy(alpha = 0.75f),
                startAngle = 130f,
                sweepAngle = 280f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
            val progress = ((score ?: 0).toFloat() / 100f).coerceIn(0f, 1f)
            drawArc(
                brush = Brush.sweepGradient(
                    listOf(
                        AppGreen,
                        SleepRemColor,
                        AppGreen,
                    ),
                ),
                startAngle = 130f,
                sweepAngle = 280f * progress,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = score?.toString() ?: "--",
                style = MaterialTheme.typography.headlineMedium,
                color = AppText,
                fontWeight = FontWeight.Light,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = AppMuted,
            )
        }
    }
}

@Composable
private fun SleepPhaseRow(
    label: String,
    minutes: Int?,
    totalMinutes: Int?,
    color: Color,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = AppText,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = minutes?.toString() ?: "--",
                    style = MaterialTheme.typography.titleMedium,
                    color = AppText,
                    fontWeight = FontWeight.Light,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.unit_min),
                    style = MaterialTheme.typography.bodySmall,
                    color = AppMuted,
                )
            }
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp)
                .padding(top = 8.dp),
        ) {
            drawLine(
                color = AppLine.copy(alpha = 0.72f),
                start = Offset(0f, size.height / 2f),
                end = Offset(size.width, size.height / 2f),
                strokeWidth = 8f,
                cap = StrokeCap.Round,
            )
            val progress = if (minutes != null && totalMinutes != null && totalMinutes > 0) {
                (minutes.toFloat() / totalMinutes.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }
            if (progress > 0f) {
                drawLine(
                    color = color,
                    start = Offset(0f, size.height / 2f),
                    end = Offset(size.width * progress, size.height / 2f),
                    strokeWidth = 8f,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

@Composable
private fun EmptyDataCard() {
    SetupCard {
        Text(
            text = stringResource(R.string.empty_ring_values_title),
            style = MaterialTheme.typography.titleMedium,
            color = AppText,
        )
        Text(
            text = stringResource(R.string.empty_ring_values_body),
            style = MaterialTheme.typography.bodyMedium,
            color = AppMuted,
        )
    }
}

@Composable
private fun MoreScreen(
    settings: RelaySettings,
    uploadStatus: String,
    uploadOk: Boolean,
    uploading: Boolean,
    canUpload: Boolean,
    onUpload: () -> Unit,
    onOpenSetupFlow: () -> Unit,
    onHomeAssistantUrlChange: (String) -> Unit,
    onRelayTokenChange: (String) -> Unit,
    onRelayIdChange: (String) -> Unit,
    onRingIdChange: (String) -> Unit,
    onRingNameChange: (String) -> Unit,
    onSyncIntervalChange: (Int) -> Unit,
    onStepGoalChange: (Int) -> Unit,
    onSave: () -> Unit,
) {
    Text(
        text = stringResource(R.string.more_title),
        style = MaterialTheme.typography.headlineLarge,
        color = AppText,
    )
    Text(
        text = stringResource(R.string.more_subtitle),
        style = MaterialTheme.typography.bodyLarge,
        color = AppMuted,
        modifier = Modifier.padding(top = 6.dp, bottom = 18.dp),
    )
    SetupCard {
        RelayTextField(
            value = settings.homeAssistantUrl,
            onValueChange = onHomeAssistantUrlChange,
            label = stringResource(R.string.ha_link_label),
            placeholder = "https://ha.example.net",
            keyboardType = KeyboardType.Uri,
        )
        RelayTextField(
            value = settings.relayToken,
            onValueChange = onRelayTokenChange,
            label = stringResource(R.string.relay_token_label),
            placeholder = "fitorb_relay_...",
            keyboardType = KeyboardType.Password,
            password = true,
        )
        RelayTextField(
            value = settings.relayId,
            onValueChange = onRelayIdChange,
            label = stringResource(R.string.relay_id_label),
            placeholder = "android-pixel",
            keyboardType = KeyboardType.Text,
        )
        RelayTextField(
            value = settings.ringName,
            onValueChange = onRingNameChange,
            label = stringResource(R.string.ring_name_label),
            placeholder = stringResource(R.string.ring_name_placeholder),
            keyboardType = KeyboardType.Text,
        )
        RelayTextField(
            value = settings.ringId,
            onValueChange = onRingIdChange,
            label = stringResource(R.string.ring_id_label),
            placeholder = stringResource(R.string.ring_id_placeholder),
            keyboardType = KeyboardType.Text,
        )
        IntervalSlider(
            syncInterval = settings.syncIntervalMinutes,
            onSyncIntervalChange = onSyncIntervalChange,
        )
        StepGoalSlider(
            stepGoal = settings.stepGoal,
            onStepGoalChange = onStepGoalChange,
        )
        StatusPill(label = uploadStatus, active = uploadOk)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SecondaryRelayButton(
                text = stringResource(R.string.button_save),
                onClick = onSave,
                modifier = Modifier.weight(1f),
            )
            PrimaryRelayButton(
                text = if (uploading) {
                    stringResource(R.string.button_sending)
                } else {
                    stringResource(R.string.button_test)
                },
                enabled = canUpload,
                onClick = onUpload,
                modifier = Modifier.weight(1f),
            )
        }
    }
    Spacer(modifier = Modifier.height(14.dp))
    SecondaryRelayButton(
        text = stringResource(R.string.button_reopen_setup),
        onClick = onOpenSetupFlow,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun MetricRow(
    label: String,
    value: String,
    unit: String,
    detail: String?,
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
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                )
            }
            if (!detail.isNullOrBlank()) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
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
                Row(verticalAlignment = Alignment.CenterVertically) {
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
        modifier = modifier
            .fillMaxWidth()
            .height(84.dp),
        color = AppPanel.copy(alpha = 0.62f),
        shape = RoundedCornerShape(24.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            AppSilver.copy(alpha = 0.26f),
        ),
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RelayTab.entries.forEach { tab ->
                val selected = activeTab == tab.key
                val label = stringResource(tab.labelRes)
                val interactionSource = remember { MutableInteractionSource() }
                val hovered by interactionSource.collectIsHoveredAsState()
                val tabColor = when {
                    selected -> AppGreen
                    hovered -> AppText
                    else -> AppMuted
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(62.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                        ) { onTabSelected(tab) }
                        .padding(top = 5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    TabIcon(
                        tab = tab,
                        color = tabColor,
                        modifier = Modifier.size(23.dp),
                    )
                    Text(
                        text = label,
                        color = tabColor,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.padding(top = 5.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun TabIcon(
    tab: RelayTab,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val stroke = 2.4f
        when (tab) {
            RelayTab.Home -> {
                drawLine(color, Offset(size.width * 0.18f, size.height * 0.48f), Offset(size.width * 0.5f, size.height * 0.2f), stroke)
                drawLine(color, Offset(size.width * 0.5f, size.height * 0.2f), Offset(size.width * 0.82f, size.height * 0.48f), stroke)
                drawLine(color, Offset(size.width * 0.28f, size.height * 0.45f), Offset(size.width * 0.28f, size.height * 0.82f), stroke)
                drawLine(color, Offset(size.width * 0.72f, size.height * 0.45f), Offset(size.width * 0.72f, size.height * 0.82f), stroke)
                drawLine(color, Offset(size.width * 0.28f, size.height * 0.82f), Offset(size.width * 0.72f, size.height * 0.82f), stroke)
            }
            RelayTab.Activity -> {
                drawLine(color, Offset(size.width * 0.08f, size.height * 0.58f), Offset(size.width * 0.28f, size.height * 0.58f), stroke, cap = StrokeCap.Round)
                drawLine(color, Offset(size.width * 0.28f, size.height * 0.58f), Offset(size.width * 0.40f, size.height * 0.30f), stroke, cap = StrokeCap.Round)
                drawLine(color, Offset(size.width * 0.40f, size.height * 0.30f), Offset(size.width * 0.57f, size.height * 0.78f), stroke, cap = StrokeCap.Round)
                drawLine(color, Offset(size.width * 0.57f, size.height * 0.78f), Offset(size.width * 0.70f, size.height * 0.48f), stroke, cap = StrokeCap.Round)
                drawLine(color, Offset(size.width * 0.70f, size.height * 0.48f), Offset(size.width * 0.92f, size.height * 0.48f), stroke, cap = StrokeCap.Round)
            }
            RelayTab.Sleep -> {
                drawArc(
                    color = color,
                    startAngle = 105f,
                    sweepAngle = 230f,
                    useCenter = false,
                    topLeft = Offset(size.width * 0.18f, size.height * 0.12f),
                    size = Size(size.width * 0.62f, size.height * 0.70f),
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
                drawArc(
                    color = color.copy(alpha = 0.65f),
                    startAngle = 95f,
                    sweepAngle = 110f,
                    useCenter = false,
                    topLeft = Offset(size.width * 0.38f, size.height * 0.16f),
                    size = Size(size.width * 0.38f, size.height * 0.58f),
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
            RelayTab.More -> {
                val radius = 2.5f
                listOf(
                    Offset(size.width * 0.32f, size.height * 0.32f),
                    Offset(size.width * 0.68f, size.height * 0.32f),
                    Offset(size.width * 0.32f, size.height * 0.68f),
                    Offset(size.width * 0.68f, size.height * 0.68f),
                ).forEach { center ->
                    drawCircle(color = color, radius = radius, center = center)
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
                text = stringResource(R.string.sync_interval),
                style = MaterialTheme.typography.bodyMedium,
                color = AppMuted,
            )
            Text(
                text = "$syncInterval ${stringResource(R.string.unit_min)}",
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
private fun StepGoalSlider(
    stepGoal: Int,
    onStepGoalChange: (Int) -> Unit,
) {
    val normalizedGoal = stepGoal.coerceIn(MIN_STEP_GOAL_STEPS, MAX_STEP_GOAL_STEPS)
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.step_goal),
                style = MaterialTheme.typography.bodyMedium,
                color = AppMuted,
            )
            Text(
                text = "${formattedInt(normalizedGoal)} ${stringResource(R.string.unit_steps)}",
                style = MaterialTheme.typography.bodyMedium,
                color = AppText,
            )
        }
        Slider(
            value = normalizedGoal.toFloat(),
            onValueChange = { value ->
                val rounded = (
                    (value / STEP_GOAL_INCREMENT_STEPS).toInt() *
                        STEP_GOAL_INCREMENT_STEPS
                    ).coerceIn(MIN_STEP_GOAL_STEPS, MAX_STEP_GOAL_STEPS)
                onStepGoalChange(rounded)
            },
            valueRange = MIN_STEP_GOAL_STEPS.toFloat()..MAX_STEP_GOAL_STEPS.toFloat(),
            steps = ((MAX_STEP_GOAL_STEPS - MIN_STEP_GOAL_STEPS) / STEP_GOAL_INCREMENT_STEPS) - 1,
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
            text = stringResource(R.string.app_name),
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

@Composable
private fun ringDisplayName(settings: RelaySettings): String =
    settings.ringName.trim().ifBlank { stringResource(R.string.default_ring_name) }

@Composable
private fun ringSampleDetail(value: Int?): String? =
    if (value == null) {
        stringResource(R.string.metric_missing_ring_sample)
    } else {
        null
    }

private fun ringIntText(value: Int?): String =
    value?.toString() ?: "--"

private fun formattedInt(value: Int?): String =
    value?.let { NumberFormat.getIntegerInstance().format(it) } ?: "--"

private fun distanceValueText(meters: Int?): String =
    meters?.let {
        if (it >= 1_000) {
            String.format(Locale.getDefault(), "%.2f", it / 1_000.0)
        } else {
            it.toString()
        }
    } ?: "--"

@Composable
private fun distanceUnitText(meters: Int?): String =
    if ((meters ?: 0) >= 1_000) {
        "km"
    } else {
        stringResource(R.string.unit_meter)
    }

private fun sleepText(minutes: Int?): String =
    minutes?.let { "${it / 60} h ${(it % 60).toString().padStart(2, '0')}" } ?: "--"

private fun sleepClockText(minutes: Int?): String =
    minutes?.let { "${it / 60}:${(it % 60).toString().padStart(2, '0')}:00" } ?: "--:--:--"

@Composable
private fun sleepDateText(samples: List<RelaySampleDto>): String {
    val sleepDate = latestSleepLocalDate(samples)
        ?: return stringResource(R.string.metric_missing_ring_sample)
    val formatter = DateTimeFormatter.ofPattern("d MMMM yyyy, EEEE", Locale.getDefault())
    return formatter.format(sleepDate)
}

private fun progressFrom(value: Int?, maxValue: Int): Float =
    value?.let {
        (it.toFloat() / maxValue.toFloat()).coerceIn(0.08f, 1f)
    } ?: 0.08f

private fun stepProgress(steps: Int?, stepGoal: Int): Float =
    steps?.let {
        (it.toFloat() / stepGoal.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
    } ?: 0f

private fun activityHourBars(samples: List<RelaySampleDto>): List<Float> {
    val bars = MutableList(24) { 0f }
    samples
        .filter { sample -> sample.metric == "steps" }
        .forEach { sample ->
            val hour = runCatching {
                Instant.parse(sample.timestamp).atZone(ZoneId.systemDefault()).hour
            }.getOrNull() ?: return@forEach
            val value = sample.numericValue() ?: return@forEach
            bars[hour] = maxOf(bars[hour], value.toFloat())
        }
    return bars
}

private fun RelaySampleDto.numericValue(): Int? =
    when (val sampleValue = value) {
        is RelaySampleValue.IntValue -> sampleValue.value
        is RelaySampleValue.DoubleValue -> sampleValue.value.toInt()
        is RelaySampleValue.StringValue -> sampleValue.value.toIntOrNull()
        is RelaySampleValue.BoolValue -> null
    }

private data class SleepStageSegment(
    val stage: String,
    val minutes: Int,
)

private data class SleepQuality(
    val score: Int?,
    val effectiveness: Int?,
)

private fun sleepStageSegments(samples: List<RelaySampleDto>): List<SleepStageSegment> {
    val stageSamples = samples
        .filter { sample -> sample.metric == "sleep_stage" }
        .mapNotNull { sample ->
            val instant = sample.timestampInstantOrNull() ?: return@mapNotNull null
            val stage = sample.stringValue()?.takeIf { it in setOf("awake", "rem", "light", "deep") }
                ?: return@mapNotNull null
            Triple(sample, instant, stage)
        }
        .sortedBy { (_sample, instant, _stage) -> instant }

    return stageSamples.mapIndexed { index, (sample, instant, stage) ->
        val inferredMinutes = stageSamples.getOrNull(index + 1)
            ?.let { (_nextSample, nextInstant, _nextStage) ->
                Duration.between(instant, nextInstant).toMinutes().toInt()
                    .takeIf { it in 1..720 }
            }
        SleepStageSegment(
            stage = stage,
            minutes = (sample.stageMinutesFromRawHex() ?: inferredMinutes ?: 15)
                .coerceAtLeast(1),
        )
    }
}

private fun sleepStageMinutes(segments: List<SleepStageSegment>): Map<String, Int> =
    segments
        .groupBy { it.stage }
        .mapValues { (_stage, stageSegments) -> stageSegments.sumOf { it.minutes } }

private fun sleepQuality(
    snapshot: RingDashboardSnapshot,
    segments: List<SleepStageSegment>,
): SleepQuality {
    val stageMinutes = sleepStageMinutes(segments)
    val totalMinutes = snapshot.sleepMinutes
        ?: stageMinutes.values.sum().takeIf { it > 0 }
    val asleepMinutes = snapshot.sleepAsleepMinutes
        ?: listOfNotNull(stageMinutes["light"], stageMinutes["deep"], stageMinutes["rem"])
            .sum()
            .takeIf { it > 0 }
    val effectiveness = if (totalMinutes != null && totalMinutes > 0 && asleepMinutes != null) {
        (asleepMinutes.toFloat() / totalMinutes.toFloat() * 100f)
            .roundToInt()
            .coerceIn(0, 100)
    } else {
        null
    }
    if (totalMinutes == null || totalMinutes <= 0 || effectiveness == null) {
        return SleepQuality(score = null, effectiveness = effectiveness)
    }

    val deepMinutes = snapshot.sleepDeepMinutes ?: stageMinutes["deep"] ?: 0
    val remMinutes = snapshot.sleepRemMinutes ?: stageMinutes["rem"] ?: 0
    val durationScore = (totalMinutes.toFloat() / 480f * 100f).coerceIn(0f, 100f)
    val restorativeScore = if (asleepMinutes != null && asleepMinutes > 0) {
        ((deepMinutes + remMinutes).toFloat() / asleepMinutes.toFloat() / 0.35f * 100f)
            .coerceIn(0f, 100f)
    } else {
        0f
    }
    val score = (durationScore * 0.45f + effectiveness * 0.35f + restorativeScore * 0.20f)
        .roundToInt()
        .coerceIn(0, 100)
    return SleepQuality(score = score, effectiveness = effectiveness)
}

private fun sleepAxisLabels(
    samples: List<RelaySampleDto>,
    segments: List<SleepStageSegment>,
): List<String> {
    val start = samples
        .asSequence()
        .filter { sample -> sample.metric == "sleep_stage" }
        .mapNotNull { sample -> sample.timestampInstantOrNull() }
        .minOrNull()
        ?: return listOf("1:00", "3:00", "5:00", "7:00")
    val totalMinutes = segments.sumOf { it.minutes }.takeIf { it > 0 } ?: 360
    val formatter = DateTimeFormatter.ofPattern("H:mm", Locale.getDefault())
    return listOf(0f, 1f / 3f, 2f / 3f, 1f).map { fraction ->
        val instant = start.plusSeconds((totalMinutes * fraction * 60f).roundToInt().toLong())
        formatter.format(roundedToFiveMinutes(instant).atZone(ZoneId.systemDefault()))
    }
}

private fun roundedToFiveMinutes(instant: Instant): Instant {
    val stepSeconds = 5L * 60L
    val roundedEpoch = ((instant.epochSecond + stepSeconds / 2L) / stepSeconds) * stepSeconds
    return Instant.ofEpochSecond(roundedEpoch)
}

private fun latestSleepLocalDate(samples: List<RelaySampleDto>): LocalDate? {
    val today = LocalDate.now()
    val localDate = samples
        .asSequence()
        .filter { sample -> sample.metric in sleepMetricNames }
        .mapNotNull { sample -> sample.localDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } }
        .maxOrNull()
    if (localDate != null) {
        return localDate.coerceAtMost(today)
    }

    return latestSampleInstant(samples, "sleep_summary", "sleep_stage")
        ?.atZone(ZoneId.systemDefault())
        ?.toLocalDate()
        ?.coerceAtMost(today)
}

private fun RelaySampleDto.stringValue(): String? =
    (value as? RelaySampleValue.StringValue)?.value

private fun RelaySampleDto.stageMinutesFromRawHex(): Int? =
    rawHex
        ?.takeIf { it.length >= 4 }
        ?.substring(2, 4)
        ?.toIntOrNull(16)
        ?.takeIf { it > 0 }

private fun latestSampleInstant(
    samples: List<RelaySampleDto>,
    vararg metrics: String,
): Instant? =
    samples
        .filter { sample -> sample.metric in metrics }
        .mapNotNull { sample -> sample.timestampInstantOrNull() }
        .maxOrNull()

private fun RelaySampleDto.timestampInstantOrNull(): Instant? =
    runCatching { Instant.parse(timestamp) }.getOrNull()

private val sleepMetricNames = setOf(
    "sleep_summary",
    "sleep_stage",
    "sleep_asleep",
    "sleep_awake",
    "sleep_light",
    "sleep_deep",
    "sleep_rem",
)

private fun sleepStageColor(stage: String): Color =
    when (stage) {
        "awake" -> SleepAwakeColor
        "rem" -> SleepRemColor
        "light" -> SleepLightColor
        "deep" -> SleepDeepColor
        else -> AppGreen
    }

private fun RelaySettings.isReadyForUpload(): Boolean =
    (homeAssistantUrl.trim().startsWith("https://", ignoreCase = true) ||
        (BuildConfig.ALLOW_CLEARTEXT_HTTP &&
            homeAssistantUrl.trim().startsWith("http://", ignoreCase = true))) &&
        relayToken.trim().startsWith("fitorb_relay_") &&
        relayId.trim().isNotEmpty() &&
        ringId.trim().isNotEmpty()

private fun compactRingId(ringId: String): String =
    ringId.trim()

private fun countProgress(count: Int?): Float =
    when {
        count == null -> 0.18f
        count <= 0 -> 0.24f
        else -> (0.35f + (count.coerceAtMost(10) / 10f) * 0.55f).coerceAtMost(0.9f)
    }
