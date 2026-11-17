package com.music.bitchord.proxy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.music.bitchord.data.AppProxyKind
import com.music.bitchord.data.AppProxyRoute
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Built-in WireGuard profile import plus standalone HTTP/SOCKS5 options.
 * Mode changes are applied on the next process start; no VpnService is used.
 */
class AppProxySettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val savedSelection = AppProxyStore.read(this)
        setContent {
            var initial by remember { mutableStateOf(savedSelection) }
            var profileInstalled by remember {
                mutableStateOf(WireGuardProfileStore.hasProfile(this@AppProxySettingsActivity))
            }
            var notice by remember { mutableStateOf<String?>(null) }
            val scope = rememberCoroutineScope()

            val profileImporter = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument(),
            ) { uri ->
                if (uri != null) {
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                val input = contentResolver.openInputStream(uri)
                                    ?: throw IOException("Cannot open WireGuard profile")
                                input.use { stream ->
                                    val profile = WireGuardProfileStore.readPlaintext(stream)
                                    WireGuardProfileStore.saveValidated(
                                        this@AppProxySettingsActivity,
                                        profile,
                                    )
                                }
                            }
                            // Importing a profile is an explicit intent to
                            // use the embedded tunnel. Make the second radio
                            // selected and persist it for the next process.
                            val wireGuardRoute = AppProxyRoute(
                                AppProxyKind.WIREGUARD_LOCAL, "127.0.0.1", 9,
                            )
                            AppProxyStore.save(this@AppProxySettingsActivity, wireGuardRoute)
                            initial = wireGuardRoute
                            profileInstalled = true
                            notice = "WireGuard selected. Fully restart BitChord to apply."
                        } catch (_: Exception) {
                            // Do not display parser exceptions or raw profile text.
                            notice = "Import failed. Check .conf format, keys, and secure storage."
                        } catch (_: LinkageError) {
                            notice = "WireGuard native engine is not available on this device."
                        }
                    }
                }
            }

            MaterialTheme(
                colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    // Recreate the editor if a newly imported .conf changes the
                    // selected mode; do not show a stale DIRECT radio.
                    key(initial.kind, initial.host, initial.port) {
                        AppProxyEditor(
                            initial = initial,
                            profileInstalled = profileInstalled,
                            message = notice,
                            onImportProfile = { profileImporter.launch(arrayOf("*/*")) },
                            save = { route ->
                                AppProxyStore.save(this@AppProxySettingsActivity, route)
                                finish()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppProxyEditor(
    initial: AppProxyRoute,
    profileInstalled: Boolean,
    message: String?,
    onImportProfile: () -> Unit,
    save: (AppProxyRoute) -> Unit,
) {
    var selected by remember { mutableStateOf(initial.kind) }
    var host by remember { mutableStateOf(initial.host) }
    var port by remember { mutableStateOf(if (initial.port == 0) "" else initial.port.toString()) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("App-local proxy", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Default: Built-in WireGuard. If no .conf is imported yet, " +
                "protected requests are blocked until a valid profile is set.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text("Uses only BitChord's own networking. No Android VPN permission or separate patcher.")
        Text("Current process: " + when {
            AppWireGuard.isReady -> "WireGuard local proxy ready (handshake not verified)"
            AppWireGuard.isBlocked -> "WireGuard blocked (no direct fallback)"
            else -> "WireGuard not active"
        })
        HorizontalDivider()
        Text("Embedded WireGuard", style = MaterialTheme.typography.titleMedium)
        Text(
            if (profileInstalled) "Encrypted .conf profile saved on this device"
            else "Import a .conf file before enabling WireGuard",
        )
        OutlinedButton(onClick = onImportProfile) {
            Text(if (profileInstalled) "Replace WireGuard .conf" else "Import WireGuard .conf")
        }
        message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        HorizontalDivider()
        for (kind in listOf(
            AppProxyKind.DIRECT,
            AppProxyKind.WIREGUARD_LOCAL,
            AppProxyKind.HTTP,
            AppProxyKind.SOCKS5,
        )) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = selected == kind,
                    onClick = {
                        selected = kind
                        error = null
                    },
                    enabled = kind != AppProxyKind.WIREGUARD_LOCAL || profileInstalled,
                )
                Text(
                    when (kind) {
                        AppProxyKind.DIRECT -> "Off (system/direct networking)"
                        AppProxyKind.WIREGUARD_LOCAL -> "Built-in WireGuard"
                        AppProxyKind.HTTP -> "External HTTP proxy"
                        AppProxyKind.SOCKS5 -> "External SOCKS5 proxy"
                    },
                )
            }
        }
        if (selected == AppProxyKind.HTTP || selected == AppProxyKind.SOCKS5) {
            OutlinedTextField(
                value = host,
                onValueChange = { host = it },
                label = { Text("Proxy host/IP (no scheme)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = port,
                onValueChange = { port = it },
                label = { Text("Proxy port") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (selected == AppProxyKind.WIREGUARD_LOCAL) {
            Text("Google login WebView will use the same app-local tunnel. " +
                "WebView proxy authentication and external IP require on-device verification.")
        }
        Text("Save, close BitChord completely, then reopen it for routing changes.")
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = {
            val route = when (selected) {
                AppProxyKind.DIRECT -> AppProxyRoute.DIRECT
                AppProxyKind.WIREGUARD_LOCAL ->
                    AppProxyRoute(AppProxyKind.WIREGUARD_LOCAL, "127.0.0.1", 9)
                else -> AppProxyRoute(selected, host, port.toIntOrNull() ?: 0)
            }
            if (selected == AppProxyKind.WIREGUARD_LOCAL && !profileInstalled) {
                error = "Import a WireGuard .conf profile first"
            } else {
                runCatching { route.validated() }.fold(
                    onSuccess = save,
                    onFailure = { error = it.message ?: "Invalid proxy" },
                )
            }
        }) { Text("Save (restart required)") }
    }
}
