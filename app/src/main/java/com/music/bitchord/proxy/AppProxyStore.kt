package com.music.bitchord.proxy

import android.content.Context
import com.music.bitchord.data.AppProxyKind
import com.music.bitchord.data.AppProxyRoute

/** Stores non-secret endpoints only; no WireGuard private keys or passwords. */
object AppProxyStore {
    private const val PREFS = "bitchord_app_proxy_v1"
    private const val KEY_WG_DEFAULT_MIGRATED = "wireguard_default_v2_applied"

    /** The new default is app-local WireGuard, never unauthenticated direct traffic. */
    private val defaultWireGuard = AppProxyRoute(
        AppProxyKind.WIREGUARD_LOCAL, "127.0.0.1", 9,
    )

    @Synchronized
    fun read(context: Context): AppProxyRoute {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val previous = prefs.getString("mode", null)

        // One-time migration for the original test APK where importing a
        // WireGuard profile left the default radio on DIRECT. Existing HTTP
        // and SOCKS5 choices are not touched. Future explicit OFF choices win.
        if (!prefs.getBoolean(KEY_WG_DEFAULT_MIGRATED, false)) {
            val useWireGuard = previous == null || (
                previous == AppProxyKind.DIRECT.name &&
                    WireGuardProfileStore.hasProfile(context)
            )
            val changes = prefs.edit().putBoolean(KEY_WG_DEFAULT_MIGRATED, true)
            if (useWireGuard) {
                changes.putString("mode", AppProxyKind.WIREGUARD_LOCAL.name)
                    .putString("host", "")
                    .putInt("port", 0)
            }
            changes.apply()
        }

        val kind = prefs.getString("mode", null)
            ?: AppProxyKind.WIREGUARD_LOCAL.name
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

    @Synchronized
    fun save(context: Context, route: AppProxyRoute) {
        val checked = route.validated()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_WG_DEFAULT_MIGRATED, true)
            .putString("mode", checked.kind.name)
            // Never persist the randomly bound WireGuard port.
            .putString("host", if (checked.kind == AppProxyKind.WIREGUARD_LOCAL) "" else checked.host)
            .putInt("port", if (checked.kind == AppProxyKind.WIREGUARD_LOCAL) 0 else checked.port)
            .commit().also { saved ->
                // When relaunching immediately, async apply() could lose the
                // selected mode before it reaches disk.
                check(saved) { "Could not persist proxy mode before restart" }
            }
    }
}
