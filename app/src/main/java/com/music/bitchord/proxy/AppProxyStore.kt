package com.music.bitchord.proxy

import android.content.Context
import com.music.bitchord.data.AppProxyKind
import com.music.bitchord.data.AppProxyRoute

/** Stores non-secret endpoints only; no WireGuard private keys or passwords. */
object AppProxyStore {
    private const val PREFS = "bitchord_app_proxy_v1"

    fun read(context: Context): AppProxyRoute {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val kind = prefs.getString("mode", null) ?: return AppProxyRoute.DIRECT
        return runCatching {
            val selected = AppProxyKind.valueOf(kind)
            if (selected == AppProxyKind.WIREGUARD_LOCAL) {
                // Only the choice persists. Native binds a random private
                // loopback port on each app process start.
                AppProxyRoute(selected, "127.0.0.1", 9)
            } else {
                AppProxyRoute(
                    selected,
                    prefs.getString("host", "").orEmpty(),
                    prefs.getInt("port", 0),
                ).validated()
            }
        }.getOrElse { AppProxyRoute.FAIL_CLOSED }
    }

    fun save(context: Context, route: AppProxyRoute) {
        val checked = route.validated()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("mode", checked.kind.name)
            // Never persist the randomly bound WireGuard port.
            .putString("host", if (checked.kind == AppProxyKind.WIREGUARD_LOCAL) "" else checked.host)
            .putInt("port", if (checked.kind == AppProxyKind.WIREGUARD_LOCAL) 0 else checked.port)
            .apply()
    }
}
