# 4pril BitChord Android v1.0.0

First public **non-prerelease** build of the 4pril Android fork, based on BitChord v1.9.1.

## What's included
- **Built-in app-local WireGuard** with encrypted `.conf` import and automatic restart, without using a Morphe patcher or Android system VPN.
- **Proxy-first onboarding**: select a `.conf` before setting a display name or signing in to Google. Signed-out Home has a proxy shortcut alongside Google sign-in. The in-app proxy options are **Off** or **Use WireGuard proxy**.
- **Google sign-in WebView proxy integration**, including HTTP CONNECT proxy routing, a targeted local proxy auth challenge handler, and fail-closed checks when routing cannot be applied.
- **Original BitChord audio/playback/source-selection engine unchanged.** New song selection opens the full player by default (existing saved user choices remain).
- Mini-player downward swipe **shrinks and merges into the bottom tabs**, then fully stops playback and clears the media queue. Aborted gestures spring back.
- Normal Liquid Glass, Haze/blur, bottom navigation animations, and streaming functions remain unchanged.
- **R8 optimized** APK: release-equivalent code shrinking and resource optimization, instead of the previous debug build.

## Which APK?
- **BitChord-4pril-v1.0.0-arm64-v8a.apk** — modern 64-bit Android phones.
- **BitChord-4pril-v1.0.0-universal.apk** — compatible multi-ABI APK; use if the arm64 build is reported incompatible.
- **BitChord-4pril-v1.0.0-x86_64.apk** — Android x86_64 emulators.

The application is `com.dev.bitchord` and is **signed using the build runner's debug signing key**. It is R8-optimized, but is **not** signed with a long-lived production signing certificate. Updates over APKs from a different signing certificate can fail: keep a copy of your original `.conf` before uninstalling. Reinstalling clears encrypted profiles and AndroidKeyStore keys.

## Important limits and known issues
- **On-device validation is still required** for WireGuard peer handshake, DNS/exit IP, and Google login WebView proxy authentication. A successful in-app login alone does not establish that the sign-in used the tunnel; providers may handle HTTP 407 differently.
- WireGuard is **app-local**, not a system-wide VPN or OS kill switch. Other Android apps are not protected by this integration.
- CI built the R8-optimized APK and its embedded WireGuard `.so` successfully. The broader Android check failed **17 locale-string completeness tests** (1259 tests, 1 skipped), while other tests passed. This should not be presented as a fully green test suite.
- Existing licenses/attribution from upstream and the native WireGuard dependencies are retained with the source and packaged assets.

**Build provenance:** `04pril/BitChord-Android@b1f5cb2f780a02a94131ae94e7a09c72e6d39fea`, GitHub Actions [optimized APK run #32](https://github.com/04pril/BitChord-Android/actions/runs/44068966971). Artifacts reproduced here from the exact same verified release, without rebuilding or changing APK bytes.
