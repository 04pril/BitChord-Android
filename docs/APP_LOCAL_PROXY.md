# BitChord Android app-local proxy: Phase 1

Status: experimental / build and real-device validation pending. The project is based on BitChord v1.9.1 and ships a direct-install debug APK through Actions.

- Proxy options: Direct (default), HTTP, SOCKS5.
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

### Unimplemented
**WireGuard is NOT built in yet.** This branch does not provide the Go userspace engine, JNI bridge, native libraries, encrypted .conf storage, random proxy credentials, or a WireGuard switch. HTTPS proxy authentication is also not implemented. This prototype is not a complete VPN or kill switch. Uncovered WebView requests, third-party networking, SMB, and Cast require device validation.

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
