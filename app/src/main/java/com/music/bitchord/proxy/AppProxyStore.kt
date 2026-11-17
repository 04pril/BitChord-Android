package com.music.bitchord.proxy

import android.content.Context
import com.music.bitchord.data.AppProxyKind
import com.music.bitchord.data.AppProxyRoute

/** Stores non-secret endpoints only; no WireGuard private keys or passwords. */
object AppProxyStore {
    private const val PREFS = "bitchord_app_proxy_v1"
    private const val KEY_WG_DEFAULT_MIGRATED = "wireguard_default_v2_applied"
    private const val KEY_TWO_MODE_MIGRATED = "wireguard_only_options_v1"

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

        // Legacy HTTP/SOCKS5 options are no longer offered. Migrate those
        // saved choices ONCE so the UI never says "Off" while an old external
        // proxy is still active. A saved .conf takes precedence; otherwise use
        // explicit Direct. Unknown or corrupted modes continue to fail closed.
        if (!prefs.getBoolean(KEY_TWO_MODE_MIGRATED, false)) {
            val legacy = prefs.getString("mode", null)
            val changes = prefs.edit().putBoolean(KEY_TWO_MODE_MIGRATED, true)
            if (legacy == AppProxyKind.HTTP.name || legacy == AppProxyKind.SOCKS5.name) {
                val newMode = if (WireGuardProfileStore.hasProfile(context)) {
                    AppProxyKind.WIREGUARD_LOCAL.name
                } else {
                    AppProxyKind.DIRECT.name
                }
                changes.putString("mode", newMode).putString("host", "").putInt("port", 0)
            }
            changes.commit()
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
            .putBoolean(KEY_TWO_MODE_MIGRATED, true)
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
