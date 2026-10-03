package com.foxyvpn.app.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foxyvpn.app.FoxyVpnApp
import com.foxyvpn.app.data.GUARDIAN_ENDPOINT_DEFAULT
import com.foxyvpn.app.data.GuardianClient
import com.foxyvpn.app.data.formatBytes
import com.foxyvpn.app.data.model.ConnectionState
import com.foxyvpn.app.ui.components.FoxyGradient
import com.foxyvpn.app.ui.components.FoxyWordmark
import com.foxyvpn.app.ui.components.foxyBackground
import com.foxyvpn.app.ui.theme.FoxSeed
import com.foxyvpn.app.ui.theme.LocalFoxyStatusColors
import com.foxyvpn.app.ui.theme.ThemeController
import com.foxyvpn.app.ui.theme.ThemeMode
import com.foxyvpn.app.vpn.FoxyVpnService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private sealed interface QuotaUi {
    data object Loading : QuotaUi
    data object Error : QuotaUi
    data object Unlimited : QuotaUi
    data class Data(val remaining: Long?, val max: Long?) : QuotaUi
}

private suspend fun loadQuota(app: FoxyVpnApp): QuotaUi = withContext(Dispatchers.IO) {
    runCatching<QuotaUi> {
        val token = app.authRepository.currentAccessToken() ?: return@runCatching QuotaUi.Error
        val e = GuardianClient().fetchUserInfo(GUARDIAN_ENDPOINT_DEFAULT, token)
        if (!e.limitedBandwidth) QuotaUi.Unlimited else QuotaUi.Data(e.quotaRemaining, e.maxBytes)
    }.getOrElse { QuotaUi.Error }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    app: FoxyVpnApp,
    themeController: ThemeController,
    onRequestConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onOpenServers: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by FoxyVpnService.state.collectAsState()
    val lastError by FoxyVpnService.lastError.collectAsState()
    val selectedProxy by app.proxyStateStore.selectedProxyFlow.collectAsState()

    var quota by remember { mutableStateOf<QuotaUi>(QuotaUi.Loading) }
    LaunchedEffect(state) {
        quota = loadQuota(app)
        while (state == ConnectionState.CONNECTED) {
            delay(60_000)
            quota = loadQuota(app)
        }
    }

    val systemInDarkTheme = isSystemInDarkTheme()
    val haptics = LocalHapticFeedback.current
    val cs = MaterialTheme.colorScheme
    val isConnected = state == ConnectionState.CONNECTED
    val isConnecting = state == ConnectionState.CONNECTING
    val serverName = selectedProxy?.let {
        it.countryName.ifBlank { it.countryCode.ifBlank { "Recommended" } }
    } ?: "Recommended"

    Box(Modifier.fillMaxSize().foxyBackground()) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            FoxyWordmark(size = 30.sp)
                            Text(
                                "Private connection",
                                style = MaterialTheme.typography.labelSmall,
                                color = cs.onSurfaceVariant,
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    actions = {
                        val mode = themeController.mode
                        val showingDark = themeController.resolveDark(systemInDarkTheme)
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .combinedClickable(
                                    role = Role.Button,
                                    onClick = { themeController.toggle(systemInDarkTheme) },
                                    onLongClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        themeController.set(ThemeMode.SYSTEM)
                                    },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = when (mode) {
                                    ThemeMode.SYSTEM -> Icons.Filled.BrightnessAuto
                                    ThemeMode.LIGHT -> Icons.Filled.LightMode
                                    ThemeMode.DARK -> Icons.Filled.DarkMode
                                },
                                contentDescription = "Theme",
                                tint = cs.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = cs.onSurfaceVariant)
                        }
                    },
                )
            },
        ) { padding ->
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    ConnectionStatusCard(state)
                }

                item {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        PowerButton(state) {
                            if (state == ConnectionState.DISCONNECTED) onRequestConnect() else onDisconnect()
                        }
                        Text(
                            text = when (state) {
                                ConnectionState.CONNECTED -> "Connected"
                                ConnectionState.CONNECTING -> "Connecting…"
                                ConnectionState.DISCONNECTED -> "Ready to connect"
                            },
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = when (state) {
                                ConnectionState.CONNECTED -> "Your VPN tunnel is active"
                                ConnectionState.CONNECTING -> "Establishing a secure tunnel…"
                                ConnectionState.DISCONNECTED -> "Tap the power button to start"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = cs.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        val error = lastError
                        if (error != null && !isConnecting) {
                            Spacer(Modifier.height(8.dp))
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = cs.errorContainer,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    error,
                                    modifier = Modifier.padding(12.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = cs.onErrorContainer,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        MiniInfoCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Filled.Public,
                            title = "Server",
                            value = serverName,
                            onClick = onOpenServers,
                        )
                        MiniInfoCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Filled.Security,
                            title = "Protection",
                            value = when {
                                isConnected -> "Active"
                                isConnecting -> "Starting"
                                else -> "Standby"
                            },
                            onClick = onOpenSettings,
                        )
                    }
                }

                item {
                    QuotaCard(quota)
                }

                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        color = cs.surfaceContainerLow,
                    ) {
                        Row(
                            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier.size(40.dp).clip(CircleShape).background(cs.secondaryContainer),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Filled.Speed, contentDescription = null, tint = cs.onSecondaryContainer)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Connection", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                                Text(
                                    if (isConnected) "Tunnel is running normally" else "Choose a server when you're ready",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                            IconButton(onClick = onOpenServers) {
                                Icon(Icons.Filled.Tune, contentDescription = "Choose server")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionStatusCard(state: ConnectionState) {
    val cs = MaterialTheme.colorScheme
    val statusColor = when (state) {
        ConnectionState.CONNECTED -> LocalFoxyStatusColors.current.connected
        ConnectionState.CONNECTING -> LocalFoxyStatusColors.current.connecting
        ConnectionState.DISCONNECTED -> cs.primary
    }
    val title = when (state) {
        ConnectionState.CONNECTED -> "Foxy is protecting your connection"
        ConnectionState.CONNECTING -> "Preparing your connection"
        ConnectionState.DISCONNECTED -> "Your connection is ready"
    }
    val subtitle = when (state) {
        ConnectionState.CONNECTED -> "VPN tunnel active"
        ConnectionState.CONNECTING -> "Please wait a moment"
        ConnectionState.DISCONNECTED -> "Connect whenever you need privacy"
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = cs.surfaceContainerHigh,
        tonalElevation = 2.dp,
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(statusColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(statusColor))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun MiniInfoCard(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = cs.surfaceContainer,
        tonalElevation = 1.dp,
    ) {
        Column(Modifier.padding(14.dp)) {
            Box(
                Modifier.size(38.dp).clip(CircleShape).background(cs.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = cs.onPrimaryContainer)
            }
            Spacer(Modifier.height(12.dp))
            Text(title, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
            Spacer(Modifier.height(2.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun QuotaCard(quota: QuotaUi) {
    val cs = MaterialTheme.colorScheme
    val fraction = (quota as? QuotaUi.Data)?.let { d ->
        if (d.remaining != null && d.max != null && d.max > 0) (d.remaining.toFloat() / d.max).coerceIn(0f, 1f) else null
    }
    val animated by animateFloatAsState(fraction ?: 0f, label = "quota-fraction")
    val low = fraction != null && fraction < 0.1f

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = cs.surfaceContainerHigh,
        tonalElevation = 2.dp,
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(
                "DATA REMAINING",
                style = MaterialTheme.typography.labelMedium,
                color = cs.onSurfaceVariant,
                letterSpacing = 1.5.sp,
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = when (quota) {
                        QuotaUi.Loading -> "\u2026"
                        QuotaUi.Error -> "\u2014"
                        QuotaUi.Unlimited -> "Unlimited"
                        is QuotaUi.Data -> when {
                            quota.remaining != null -> formatBytes(quota.remaining)
                            quota.max != null -> "Up to " + formatBytes(quota.max)
                            else -> "Limited"
                        }
                    },
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black,
                    color = if (low) cs.error else cs.onSurface,
                )
                if (quota is QuotaUi.Data && quota.remaining != null && quota.max != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "of " + formatBytes(quota.max),
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
            }
            if (fraction != null) {
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(cs.surfaceVariant),
                ) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(animated)
                            .clip(CircleShape)
                            .background(if (low) Brush.linearGradient(listOf(cs.error, cs.error)) else FoxyGradient),
                    )
                }
            }
        }
    }
}

@Composable
private fun PowerButton(state: ConnectionState, onClick: () -> Unit) {
    val sc = LocalFoxyStatusColors.current
    val accent by animateColorAsState(
        targetValue = when (state) {
            ConnectionState.CONNECTED -> sc.connected
            ConnectionState.CONNECTING -> sc.connecting
            ConnectionState.DISCONNECTED -> FoxSeed
        },
        label = "power-accent",
    )
    val active = state != ConnectionState.DISCONNECTED
    val pulse = rememberInfiniteTransition(label = "pulse")
    val ring by pulse.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable<Float>(
            animation = tween<Float>(
                durationMillis = if (state == ConnectionState.CONNECTING) 1100 else 2600,
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulse-ring",
    )

    Box(Modifier.size(240.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val base = 80.dp.toPx()
            val outer = size.minDimension / 2f
            val c = Offset(size.width / 2f, size.height / 2f)
            drawCircle(accent.copy(alpha = 0.12f), radius = base * 1.22f, center = c)
            if (active) {
                for (i in 0..1) {
                    val t = (ring + i * 0.5f) % 1f
                    drawCircle(
                        accent.copy(alpha = (1f - t) * 0.4f),
                        radius = base + (outer - base) * t,
                        center = c,
                        style = Stroke(width = 3.dp.toPx()),
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .size(160.dp)
                .shadow(
                    elevation = if (active) 24.dp else 10.dp,
                    shape = CircleShape,
                    ambientColor = accent,
                    spotColor = accent,
                )
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(lerp(accent, Color.White, 0.25f), accent, lerp(accent, Color.Black, 0.25f)),
                    ),
                )
                .clickable(role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.PowerSettingsNew,
                contentDescription = if (state == ConnectionState.DISCONNECTED) "Connect" else "Disconnect",
                tint = Color.White,
                modifier = Modifier.size(72.dp),
            )
        }
    }
}

@Composable
private fun LocationCard(name: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        color = cs.surfaceContainerHigh,
        tonalElevation = 2.dp,
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(cs.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Public, contentDescription = null, tint = cs.onPrimaryContainer)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Location", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = cs.onSurfaceVariant)
        }
    }
}
