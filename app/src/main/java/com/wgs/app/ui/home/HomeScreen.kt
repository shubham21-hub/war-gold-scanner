package com.wgs.app.ui.home

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.IBinder
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wgs.app.service.ScanPhase
import com.wgs.app.service.ScreenCaptureService
import java.text.NumberFormat
import java.util.Locale

@Composable
fun HomeScreen(
    onNavigateToRecords: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val phase by viewModel.scanPhase.collectAsStateWithLifecycle()
    val warId by viewModel.warId.collectAsStateWithLifecycle(initialValue = "")

    var captureService by remember { mutableStateOf<ScreenCaptureService?>(null) }
    var isServiceBound by remember { mutableStateOf(false) }

    val connection = remember {
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                captureService = (binder as ScreenCaptureService.LocalBinder).getService()
                isServiceBound = true
            }
            override fun onServiceDisconnected(name: ComponentName) {
                captureService = null
                isServiceBound = false
            }
        }
    }

    DisposableEffect(Unit) {
        val intent = Intent(context, ScreenCaptureService::class.java)
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        onDispose {
            if (isServiceBound) context.unbindService(connection)
        }
    }

    val overlayPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {}

    val projectionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            ScreenCaptureService.startCapture(context, result.resultCode, result.data!!)
        }
    }

    fun startScan() {
        if (!Settings.canDrawOverlays(context)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
            overlayPermLauncher.launch(intent)
            return
        }
        val mpm = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projectionLauncher.launch(mpm.createScreenCaptureIntent())
    }

    fun stopScan() {
        ScreenCaptureService.stopCapture(context)
    }

    val isActive = phase !is ScanPhase.Idle

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(24.dp))

                AppHeader(warId)

                Spacer(Modifier.height(32.dp))

                ScanStatusCard(phase = phase, onRetry = { viewModel.retryAfterError() })

                Spacer(Modifier.height(32.dp))

                if (!isActive) {
                    PrimaryActionButton(
                        label = "Start Scan",
                        icon = Icons.Default.PlayArrow,
                        color = MaterialTheme.colorScheme.primary,
                        onClick = { startScan() }
                    )
                } else {
                    PrimaryActionButton(
                        label = "Stop Scan",
                        icon = Icons.Default.Stop,
                        color = MaterialTheme.colorScheme.error,
                        onClick = { stopScan() }
                    )
                }

                Spacer(Modifier.height(24.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    QuickActionCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.TableRows,
                        label = "Records",
                        onClick = onNavigateToRecords
                    )
                    QuickActionCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Settings,
                        label = "Settings",
                        onClick = onNavigateToSettings
                    )
                }
            }

            if (phase is ScanPhase.Duplicate) {
                val d = phase as ScanPhase.Duplicate
                DuplicateDialog(
                    baseNumber = d.baseNumber,
                    playerName = d.playerName,
                    gold = d.gold,
                    onOverwrite = { viewModel.dismissDuplicate(true, captureService) },
                    onSkip = { viewModel.dismissDuplicate(false, captureService) }
                )
            }
        }
    }
}

@Composable
private fun AppHeader(warId: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "⚔ War Gold Scanner",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        if (warId.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                warId,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ScanStatusCard(phase: ScanPhase, onRetry: () -> Unit) {
    val (title, subtitle, containerColor, pulse) = when (phase) {
        is ScanPhase.Idle -> Quad(
            "Ready",
            "Tap Start Scan to begin scouting",
            MaterialTheme.colorScheme.surfaceVariant, false
        )
        is ScanPhase.WaitingForBase -> Quad(
            "Open Enemy Base",
            "Then tap SCAN on the overlay button",
            MaterialTheme.colorScheme.surface, true
        )
        is ScanPhase.BaseCapturing -> Quad(
            "Scanning Base...",
            "Reading base number and player name",
            MaterialTheme.colorScheme.surface, true
        )
        is ScanPhase.WaitingForGold -> Quad(
            "Base #${phase.baseNumber} — ${phase.playerName}",
            "Open Gold Storage, then tap SCAN",
            MaterialTheme.colorScheme.surface, true
        )
        is ScanPhase.GoldCapturing -> Quad(
            "Reading Gold...",
            "Analyzing Gold Storage value",
            MaterialTheme.colorScheme.surface, true
        )
        is ScanPhase.Saving -> Quad(
            "Saving...",
            "Storing record",
            MaterialTheme.colorScheme.surface, false
        )
        is ScanPhase.Success -> Quad(
            "Saved!",
            "Base #${phase.baseNumber} — ${phase.playerName}\n${formatGold(phase.gold)} gold",
            Color(0xFF1A3A1A), false
        )
        is ScanPhase.Failure -> Quad(
            "Scan Failed",
            phase.message,
            Color(0xFF3A1A1A), false
        )
        is ScanPhase.Duplicate -> Quad(
            "Duplicate Detected",
            "Base #${(phase as ScanPhase.Duplicate).baseNumber} already scanned",
            MaterialTheme.colorScheme.surfaceVariant, false
        )
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "alpha"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            Modifier.padding(20.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (pulse) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha))
                )
                Spacer(Modifier.height(12.dp))
            }
            Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                subtitle,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (phase is ScanPhase.Failure) {
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onRetry) { Text("Retry") }
            }
        }
    }
}

@Composable
private fun PrimaryActionButton(
    label: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color),
        shape = RoundedCornerShape(14.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    }
}

@Composable
private fun QuickActionCard(
    modifier: Modifier,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier.height(80.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun DuplicateDialog(
    baseNumber: Int,
    playerName: String,
    gold: Long,
    onOverwrite: () -> Unit,
    onSkip: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onSkip,
        title = { Text("Already Scanned") },
        text = {
            Text("Base #$baseNumber ($playerName) already has a record.\nOverwrite with ${formatGold(gold)} gold?")
        },
        confirmButton = {
            TextButton(onClick = onOverwrite) {
                Text("YES", color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onSkip) { Text("NO") }
        }
    )
}

private fun formatGold(gold: Long): String =
    NumberFormat.getNumberInstance(Locale.US).format(gold)

private data class Quad(
    val title: String,
    val subtitle: String,
    val color: Color,
    val pulse: Boolean
)
