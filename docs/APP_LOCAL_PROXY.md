# BitChord Android app-local proxy: Phase 1

Status: experimental / build and real-device validation pending. The project is based on BitChord v1.9.1 and ships a direct-install debug APK through Actions.

- Proxy options: Built-in WireGuard (default), Direct (explicit opt-out), HTTP, SOCKS5. On a clean install, missing .conf fails closed; import a .conf then restart to enable requests.
- Users can configure proxy host/port under Settings > Audio quality > App-local proxy. Restart is required for changes.
- The shared OkHttp client is used by the upstream API playback resolver, Media3 audio source and downloader. The Coil image fetcher is created from the same configured client.
- Google sign-in WebView has a separate proxy override, applied before loading a login page. If the override is unsupported, login is blocked instead of silently bypassing the proxy.
- An invalid stored proxy selection is mapped to an inaccessible localhost port. Changes to a proxy require restarting the process.
- The application does not use VpnService.

### 4pril UI additions
- Original BitChord streaming engines, source policy, audio cache, quality upgrades and crossfade remain unchanged.
- Settings > Miscellaneous: "Open full player on song tap". Default OFF preserves the upstream mini-player-first behavior; enabled opens Now Playing for album/playlist/search song selections.
- Mini-player swipes: UP opens Now Playing, LEFT/RIGHT changes tracks, DOWN at least 72dp calls Media3 pause + stop + clearMediaItems so playback ends and the mini-player disappears. Works on both standard and Liquid Glass mini-player.
- Playing song's overflow menu offers removal from the **originating** owned playlist only if the per-entry setVideoId is known, preventing accidental removal of another duplicate entry.
- Listen Together control lock guards the downward stop gesture.
- Upstream's LocaleStringsTest currently fails on incomplete localization catalogs; CI reports unit-test failures separately from APK assembly.

### Runtime verification limits
The userspace WireGuard JNI library and encrypted .conf import are integrated into the build. This remains an experimental app-local proxy rather than a system VPN or full kill switch. Google-login WebView 407 authentication, provider-specific WebView support, DNS/egress IP, and uncovered third-party networking clients all require on-device validation.

### Reference for Phase 2
- https://github.com/04pril/morphe-wireguard-patches
- https://github.com/seo-rii/morphe-patches/tree/feat/wireguard-i18n-custom/native/wireguard
- https://github.com/04pril/BitChord-iOS/blob/main/docs/PROXY.md

The Morphe WireGuard implementation exposes an authenticated loopback HTTP/CONNECT server. It must be built from its Go sources for Android ABI arm64-v8a and x86_64, packaged as a shared library, loaded by JNI, and integrated with Android lifecycle, Keystore and network clients. Its loopback proxy MUST require authentication: other installed apps can connect to localhost. Keep GPL-3.0 and third-party NOTICE files.

### Device test plan
1. No proxy: startup, login, browsing, playback, downloads and artwork.
2. HTTP/SOCKS5: verify YouTube API and googlevideo traffic follow the proxy.
3. Stop proxy server: verify intercepted calls do not reconnect directly.
4. Ensure actual Google login WebView works through the proxy.
5. Invalid configuration blocks traffic and direct mode can be restored.
6. Validate HTTPS, IPv4/IPv6, connectivity switches, cached audio and other clients.


## Built-in WireGuard and Google login WebView (2026-11-17)
- The v1.9.1 app already has an in-app Google login WebView; no external browser or Morphe patcher is used.
- Settings > Audio quality > App-local proxy supports importing a .conf via SAF. Configuration is validated using the pinned native engine and stored AES-GCM encrypted in noBackupFilesDir with its key in AndroidKeyStore.
- Select Built-in WireGuard and fully restart BitChord. The native Go engine binds an authenticated random localhost port; a per-process random token is used for OkHttp proxy challenges. The Android VPN slot is untouched.
- Before the login WebView starts, an AndroidX WebKit process-wide proxy override is applied, with implicit localhost-bypass rules removed. A 5-second timeout and native-startup check fail closed.
- WebViewClient answers HTTP-auth challenges **only** when host=127.0.0.1 and realm=App WireGuard. On a 407 or proxy connection error, it blocks login rather than bypassing. **Not all WebView providers expose proxy 407 challenges to this callback; real-device Android WebView compatibility is unverified.** A successful Google login alone does not prove tunneled IP — independently verify DNS and egress IP on-device.
- Native build uses Go 1.26.10 and NDK 28.2.13676358; no binary artifact is claimed until GitHub Actions compiles and packages the .so.
- Protected network paths still need on-device validation (including Google sign-in, cookies/redirects, playback, and download). No system-wide kill switch is claimed.


## Default WireGuard radio and dark theme (2026-11-17)
- On a fresh install, the second radio **Built-in WireGuard** is the default, not Direct. No .conf means blocked app networking until import; this is deliberate fail-closed behavior.
- Existing early-test installations with a saved .conf and previous implicit/explicit DIRECT mode are migrated ONCE to Built-in WireGuard. Saved HTTP/SOCKS5 choices remain untouched. After migration, a user's explicit Off selection is respected across restarts.
- Successful `.conf` import automatically saves the WireGuard mode and updates the selected radio. A full process restart is still required to activate it.
- The settings activity uses a dark/light Compose Material3 color scheme plus a Surface so labels remain visible with the system's dark theme.


## Player UI responsiveness (2026-11-17)
- Original audio/resolver/queue logic is unchanged. For a song tap with the full-player preference enabled, the host opens the sheet **synchronously**, presenting a temporary metadata-only tap preview while the existing Media3 queue is built. A matching live player item replaces the preview; a 4s fail-safe clears stale previews.
- Downward mini-player gestures now track the finger at draw time, translating down and shrinking smoothly (up to 26%) with a subtle fade. If released before the original 72dp stop threshold, the pill springs back. If released past the threshold, it finishes a 190ms ease-out and only then calls pause+stop+clearMediaItems. Works through the shared detector on normal and Liquid Glass mini-players; locked Listen Together controls rebound rather than stopping.
- Playlist deletion was confirmed functional by the tester and is intentionally untouched in this revision.


## Onboarding login: configure WireGuard first (2026-11-17)
- On the initial `Connect to YouTube Music` welcome screen, a `Configure WireGuard / proxy` button now appears **above** the Google sign-in button. It opens the existing built-in settings activity to import a WireGuard .conf and select mode, rather than any external login browser or patcher.
- The welcome flow remains after returning from settings; the user can fully restart BitChord (process restart, not activity-only) before Google login to activate the mode. The UI gives a short Korean/English restart notice.
- The Google login WebView now refuses to start if the persisted proxy mode/endpoint differs from the already-frozen process route, preventing Google authentication from silently using the old direct connection immediately after changing a proxy in onboarding.
- If WireGuard was selected but native startup has not succeeded, the WebView is also blocked rather than opening a direct route. Real-device WebView 407 auth and external IP verification are still required.


## Signed-out Home: Proxy button next to Google sign-in (2026-11-17)
- When Home is shown without a signed-in Google account, the existing mesh login banner now has **two independent pills in one row**: `Sign in with Google` and a compact `Proxy / 프록시` button to its right.
- The new button opens the same built-in WireGuard/HTTP/SOCKS5 editor as Settings and the first-run welcome screen. It does not open a separate browser.
- The signed-in Home banner stays absent. SharedUi callers without a proxy callback retain their original single-action banner behavior.
- Changing mode or importing a WireGuard .conf needs a full BitChord process restart before Google login; the WebView guard prevents a stale route from silently connecting directly.


## Automatic full-process restart after .conf import
- After a **successful** WireGuard .conf import, native validation and encrypted AtomicFile write, automatically persist Built-in WireGuard mode using synchronous SharedPreferences.commit() (never a pending async .apply()).
- Show a non-dismissible localized Material3 AlertDialog: "프록시 적용을 위해 앱을 재시작합니다." / "알겠습니다". No cancel/back/outside-dismiss action. Failed/canceled imports never restart.
- Confirmation opens a one-shot, nonexported `:proxy_relaunch` Activity in a distinct process, which terminates the old main process and launches the app's MainActivity as a new process. This guarantees AppWireGuard.startAtProcessLaunch can load the newly stored profile, rather than only recreating the Activity with the old frozen Http/WebView transport.
- BitChordApplication skips normal startup work in the short-lived relaunch helper process. This is not an Android system VPN and no WireGuard handshake/Google-login egress verification is implied.
- Other changes made by tapping Save without importing a profile continue to require a full process restart.
