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
            AppProxyRoute(
                AppProxyKind.valueOf(kind),
                prefs.getString("host", "").orEmpty(),
                prefs.getInt("port", 0),
            ).validated()
        }.getOrElse { AppProxyRoute.FAIL_CLOSED }
    }

    fun save(context: Context, route: AppProxyRoute) {
        val checked = route.validated()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("mode", checked.kind.name)
            .putString("host", checked.host)
            .putInt("port", checked.port)
            .apply()
    }
}
