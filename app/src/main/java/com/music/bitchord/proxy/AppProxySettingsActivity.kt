package com.music.bitchord.proxy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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

/** Initial standalone configuration for HTTP/SOCKS5, not a built-in WG engine. */
class AppProxySettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val current = AppProxyStore.read(this)
        setContent {
            MaterialTheme {
                AppProxyEditor(current) { route ->
                    AppProxyStore.save(this, route)
                    finish()
                }
            }
        }
    }
}

@Composable
private fun AppProxyEditor(initial: AppProxyRoute, save: (AppProxyRoute) -> Unit) {
    var selected by remember { mutableStateOf(initial.kind) }
    var host by remember { mutableStateOf(initial.host) }
    var port by remember { mutableStateOf(if (initial.port == 0) "" else initial.port.toString()) }
    var error by remember { mutableStateOf<String?>(null) }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("App-local proxy", style = MaterialTheme.typography.headlineSmall)
        Text("HTTP and SOCKS5 endpoints only. Save and fully restart BitChord to apply.")
        for (kind in listOf(AppProxyKind.DIRECT, AppProxyKind.HTTP, AppProxyKind.SOCKS5)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = selected == kind, onClick = { selected = kind; error = null })
                Text(kind.name)
            }
        }
        if (selected != AppProxyKind.DIRECT) {
            OutlinedTextField(
                value = host,
                onValueChange = { host = it },
                label = { Text("Host/IP (no scheme)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = port,
                onValueChange = { port = it },
                label = { Text("Port") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Text("Embedded WireGuard and .conf import are not available in this preliminary build.")
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = {
            val route = if (selected == AppProxyKind.DIRECT) AppProxyRoute.DIRECT
                        else AppProxyRoute(selected, host, port.toIntOrNull() ?: 0)
            runCatching { route.validated() }.fold(
                onSuccess = save,
                onFailure = { error = it.message ?: "Invalid proxy" },
            )
        }) { Text("Save (restart required)") }
    }
}
