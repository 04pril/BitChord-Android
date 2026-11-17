package com.music.bitchord.proxy

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import com.music.bitchord.data.AppProxyKind
import com.music.bitchord.data.Http
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Android WebView does not inherit OkHttp proxy settings. */
object AppProxyWebView {
    suspend fun prepare(context: Context): Boolean {
        val route = Http.configuredRoute // process-frozen route, not unsaved editor state
        if (route.kind == AppProxyKind.DIRECT) return true
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) return false
        val scheme = if (route.kind == AppProxyKind.SOCKS5) "socks" else "http"
        val host = if (":" in route.host) "[${route.host}]" else route.host
        // Do not permit the standard localhost / link-local bypass to
        // silently escape an explicitly selected app-local tunnel.
        val config = ProxyConfig.Builder()
            .addProxyRule("$scheme://$host:${route.port}")
            .removeImplicitRules()
            .build()
        return suspendCancellableCoroutine { continuation ->
            try {
                ProxyController.getInstance().setProxyOverride(
                    config,
                    ContextCompat.getMainExecutor(context),
                    Runnable { if (continuation.isActive) continuation.resume(true) },
                )
            } catch (_: Exception) {
                if (continuation.isActive) continuation.resume(false)
            }
        }
    }
}
