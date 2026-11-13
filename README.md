<h3 align="center">AltShift</h3>

<p align="center">
    <strong>
    A lightweight, crash-resilient hardware keyboard driver and hotkey manager for Android.
    </strong>
</p>
<p align="center">
    <a href="https://github.com/b1sted/AltShift/actions/workflows/build.yml">
        <img src="https://img.shields.io/github/actions/workflow/status/b1sted/AltShift/build.yml?branch=main&style=flat-square&label=CI%2FCD" alt="CI/CD" />
    </a>
    <a href="https://github.com/b1sted/AltShift/releases">
        <img src="https://img.shields.io/github/v/release/b1sted/AltShift?style=flat-square&color=blue&label=Release" alt="Release" />
    </a>
    <a href="#license">
        <img src="https://img.shields.io/badge/License-GPLv3-blue?style=flat-square" alt="License" />
    </a>
    <a href="#requirements">
        <img src="https://img.shields.io/badge/Platform-Android%208.0%2B-brightgreen?style=flat-square" alt="Platform" />
    </a>
</p>
<p align="center">
    <a href="#overview">Overview</a> &bull;
    <a href="#features">Features</a> &bull;
    <a href="#standby-reconnect-watchdog">Watchdog</a> &bull;
    <a href="#resource-footprint--verification">Resource Footprint</a> &bull;
    <a href="#requirements">Requirements</a> &bull;
    <a href="#building-from-cli">Building from CLI</a> &bull;
    <a href="#adb-setup">ADB Setup</a> &bull;
    <a href="#configuration">Configuration</a> &bull;
    <a href="#license">License</a>
</p>
<hr>

<p align="center"><img src="docs/screenshots/gui.png" alt="AltShift Dashboard" width="760" /></p>

A minimal, hardware-first input method service for Android that turns a physical keyboard into a first-class input device. No cloud services, no analytics, and no persistent background daemons.

## Overview

AltShift (`ru.b1sted.altshift`) is an input method service (IME) built for devices with a physical keyboard. It does not draw a full soft keyboard. Instead, it sits between the hardware keyboard and the focused application: it translates key events into the active layout, launches application hotkeys from user-defined combinations, and handles soft-keyboard fallbacks when the hardware keyboard is detached. Everything the user has not explicitly configured is passed through to the application untouched.

The driver is written in pure Java 21 with native XML views and Material 3 components. Configuration is stored locally in `SharedPreferences` as JSON. There is no Kotlin, no Compose, no dependency-injection framework, and no annotation processing. The implementation uses only public AOSP APIs, so the same build behaves identically across Android 8.0+ devices regardless of manufacturer or system skin.

When a physical keyboard connects or disconnects, AltShift can switch input methods autonomously through `WRITE_SECURE_SETTINGS`, or fall back to a one-tap Dynamic Island prompt and the native system IME picker. Both paths are crash-guarded: an IME failure must never take down system input or trap the user without a usable keyboard.

## Standby Reconnect Watchdog

Android 12+ may freeze a cached app process after the platform unbinds AltShift's input method service. A frozen process receives no `InputManager.InputDeviceListener` callbacks, so a pogo-pin, dock, or Bluetooth keyboard could reconnect while AltShift is frozen and the device would stay on the soft keyboard with no automatic recovery. In the same frozen state a disconnect callback never runs, so a stale "AltShift is inactive" reminder can stay in the shade.

To cover both gaps, AltShift runs a temporary foreground service (`KeyboardWatchdogService`, `foregroundServiceType="specialUse"`) while **all** of these are true:

- onboarding is complete;
- AltShift is enabled but is not the active input method;
- at least one reconnect or alert path is usable: autonomous switching with `WRITE_SECURE_SETTINGS`, the Dynamic Island overlay permission, or notification delivery.

Physical keyboard attachment is not a start condition. While detached, the watchdog shows the silent `IMPORTANCE_MIN` "Waiting for physical keyboard" status. While a keyboard is attached but another IME such as Gboard is selected, it shows "Monitoring keyboard connection" instead, so the process stays unfrozen and keeps observing device callbacks. Disconnecting the keyboard immediately cancels any stale "AltShift is inactive" reminder; reconnecting cancels it again and shows the Dynamic Island. As soon as AltShift becomes the active IME, onboarding is reset, or the last reconnect/alert path disappears, the service stops itself and removes the notification.

It performs no polling and holds no wake lock: the foreground service simply keeps the process unfrozen so the existing input-device listener can receive callbacks. While AltShift is the active IME and the user is typing, no watchdog runs, so the event-driven typing path and its zero idle overhead are unchanged.

## Features

- **Full Russian PC layout emulation (ЙЦУКЕН)**: Complete letter, punctuation, and number-row mapping (`1..0`, `-`, `=`) with strict CapsLock isolation. CapsLock affects only alphabetic keys and never inverts digits or symbols.
- **Russian localization & per-app language**: A complete `values-ru` translation covers the dashboard, onboarding, dialogs, Dynamic Island, notifications, and watchdog status. The app follows the system locale automatically (`en` / `ru`), and the in-app selector offers `System default`, `English`, and `Русский`. The choice is applied application-wide, reaches non-Activity components such as the overlay and notifications, and stays in sync with Android 13+ System App Language Settings through `AppCompatDelegate.setApplicationLocales` and `localeConfig`.
- **Configurable language switch**: The default `Alt + Shift` combination toggles Russian transliteration and is captured from the next physical key combination in the settings UI. The OS subtype switcher is bypassed.
- **Global app shortcuts**: Bind any hardware key combination to an installed application. Shortcuts are dispatched directly through the launcher intent and consume only the combinations the user defines. App hotkeys and the language switch cannot be assigned the same combination.
- **Responsive Dynamic Island**: A floating Material 3 capsule that resolves the persisted Light, Dark, or System theme before inflating, then uses dedicated `prompt_surface`/`prompt_stroke` tokens with a subtle 1dp hairline and an unclipped 4dp elevation shadow, plus a one-tap action that opens the direct system IME picker. On phones it keeps a clean 16dp screen edge and lets the message wrap to two lines so the action and close buttons stay on-screen without an ellipsis; on tablets and large screens (600dp+) it keeps the 420dp capsule with a single-line message.
- **Dual-mode keyboard switching**: AltShift returns natively to the on-screen keyboard when the hardware keyboard is detached, and can additionally switch autonomously on attach and detach when `WRITE_SECURE_SETTINGS` is granted.
- **Standby reconnect watchdog**: A temporary `specialUse` foreground service keeps the process unfrozen whenever AltShift is enabled but another IME is selected, so disconnect cleanup and repeated Dynamic Island reconnect prompts stay reliable. It stops immediately once AltShift becomes the active IME and leaves no notification or idle overhead behind. See [Standby Reconnect Watchdog](#standby-reconnect-watchdog).
- **Unlock-resilient wake-up**: The receiver validates `BOOT_COMPLETED` and `USER_PRESENT`, wakes the process on screen unlock, restores AltShift immediately when a physical keyboard is attached, and reconciles the reconnect watchdog so background freezing cannot leave hardware input disconnected.
- **Split status alerts**: The `altshift_keyboard_alerts_v2` channel at `IMPORTANCE_DEFAULT` posts a swipe-dismissible, non-heads-up reminder with a status-bar icon when the user closes (`[X]`) or ignores the Dynamic Island while AltShift remains inactive. Cancelling the system IME picker instead posts an `IMPORTANCE_HIGH` heads-up on `altshift_keyboard_alerts_urgent`, so a failed switch attempt is immediately visible. Tapping **Switch back** opens the picker without an immediate notification. Safety lockout warnings stay on the separate `altshift_safety_warnings` channel.
- **Single-IME safety protection**: A dedicated warning fires when AltShift is the only enabled keyboard, preventing a lockout after the hardware keyboard is removed.
- **Fail-safe service**: External intents, input connection calls, and secure-setting writes are guarded; an IME exception must never take down system input.
- **100% universal AOSP architecture**: Public-API-only implementation with zero vendor lock-in. The same build runs on Lenovo, Samsung, Xiaomi, Google Pixel, and other Android 8.0+ devices without OEM-specific branches.
- **Material 3 dashboard**: Toolbar status badge, language shortcut card, auto-switch controls, hotkey list, key-capture dialog, and application picker, with Light, Dark, and System themes plus an app-language selector applied application-wide.
- **Fullscreen onboarding**: A three-step wizard with large iconography, step indicator dots, permission verification, and one-tap actions for IME enablement, selection, notifications, and overlay access. IME state is resolved on a background worker so the first frame stays responsive.
- **About dialog with live diagnostics**: Version details, keyboard name, VID/PID, active IME, and switch mode, plus a repository link and a setup-wizard rerun action.
- **Zero-waste resource policy**: Pure Java 21, ~2 MB Java heap (~10 MB Java + native heap while typing), 0.0% CPU while idle, and zero telemetry. No network permission, no persistent background daemon, and no all-packages visibility: the temporary reconnect watchdog is the only foreground service and stops as soon as AltShift becomes the active IME. App labels are resolved off the main thread and icons are decoded lazily per bound row, so the settings UI peak memory is lower. Configuration stays in local `SharedPreferences`.

## Resource Footprint & Verification

AltShift operates strictly on an event-driven model with zero background polling loops, wake locks, or telemetry services. The reconnect watchdog is a single standby `specialUse` foreground-service state, not a polling loop, and it is stopped immediately once AltShift becomes the active IME. Real-world resource footprint verified on a Lenovo TB710FU (Android 16, 2000×3200, 400 dpi) via `dumpsys meminfo` and `top` (measured on an unstripped debug build):

| Metric | Active IME (Typing Mode) | Standby Watchdog (Inactive IME) | Active UI + App Picker (Peak) | Notes |
| :--- | :--- | :--- | :--- | :--- |
| **Java heap alloc** | **~2.0 MB** (`2041 KB`) | **~3.6 MB** (`3629 KB`) | **~5.1 MB** (`5153 KB`) | `Dalvik Heap` allocation; microscopic Java code and transliteration footprint |
| **Native heap alloc** | **~7.2 MB** (`7382 KB`) | **~9.0 MB** (`9171 KB`) | **~49.6 MB** (`50832 KB`) | Transient view and icon allocations; v0.4.4 defers app-picker icon decoding to bound rows |
| **Total PSS** | **~40.8 MB** (`41773 KB`) | **~51.4 MB** (`52601 KB`) | **~352 MB** (`360592 KB`) | Full process footprint, including shared code and platform overhead |
| **Bitmaps / Graphics (PSS)** | **0 KB** | **0 KB** | **~16.7 MB bitmaps** (`16735 KB`) / **~226.8 MB Graphics** (`226756 KB`) | Bitmap allocations and EGL surface memory are reclaimed on UI exit; the lazy icon cache is bounded to 32–48 entries |
| **Idle CPU** | **0.0%** | **0.0%** | **0.0–1.0%** | `top -b -n 3 -d 1` while the UI is idle; no watchdog timer runs and the process sleeps between callbacks |
| **Network / Sockets** | **0 bytes** (`INTERNET` not declared) | **0 bytes** | **0 bytes** | Hardware input is physically isolated from the network |

### Permissions & Package Visibility

AltShift declares no `INTERNET` permission and does not request `QUERY_ALL_PACKAGES`. The app picker relies on a narrow `<queries>` declaration for the launcher intent, so it can list launchable applications without broad package visibility. The remaining permissions are:

- `SYSTEM_ALERT_WINDOW` for the Dynamic Island overlay.
- `POST_NOTIFICATIONS` for keyboard, safety, and watchdog status on Android 13+.
- `WRITE_SECURE_SETTINGS` as an optional development grant for autonomous input-method switching.
- `RECEIVE_BOOT_COMPLETED` for boot and unlock reconciliation.
- `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_SPECIAL_USE` for the temporary reconnect watchdog.
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` for the onboarding Doze exemption request.

### Reproduce Diagnostics via ADB
```bash
# Inspect live memory breakdown
adb shell dumpsys meminfo ru.b1sted.altshift

# Verify zero idle CPU consumption
adb shell top -b -n 1 | grep ru.b1sted.altshift

# Cross-check the cumulative process CPU time
adb shell dumpsys cpuinfo | grep altshift

# Confirm absence of network permissions
adb shell dumpsys package ru.b1sted.altshift | grep "permission.INTERNET"

# Confirm the watchdog is absent while AltShift is the active IME (no output expected)
adb shell dumpsys activity services ru.b1sted.altshift | grep KeyboardWatchdog
```

## Requirements

- **JDK 21** for compilation and Gradle.
- **Android SDK Platform 34** with **Build-Tools 34.0.0**.
- **ADB** for device installation, IME activation, and the optional secure-settings grant.
- An Android device running **Android 8.0 (API 26) or newer** with a physical keyboard.

## Building from CLI

The Gradle wrapper is pinned and committed. Point Gradle at your SDK by creating `local.properties` in the project root:

```properties
sdk.dir=/path/to/Android/Sdk
```

Build the debug APK:

```bash
./gradlew assembleDebug
```

The artifact is written to `app/build/outputs/apk/debug/` and named `AltShift-v<version>-debug.apk`.

Build a release APK:

```bash
./gradlew assembleRelease
```

The release artifact is written to `app/build/outputs/apk/release/` and named `AltShift-v<version>.apk`. Release signing is driven entirely by environment variables (`KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`), so no keystore or password is ever committed. The repository ships a documented template:

```bash
cp scripts/build-release.sh.example scripts/build-release.sh
chmod +x scripts/build-release.sh
```

Edit `KEYSTORE_FILE` and `KEY_ALIAS` in the local copy, then run `./scripts/build-release.sh`. The local script is ignored by Git. CI follows the same contract: it decodes `KEYSTORE_BASE64` into `app/release.jks`, maps the keystore secrets to the environment variables above, builds both debug and release APKs, and publishes them to the GitHub release for `v*` tags.

Run unit tests and Android Lint before shipping:

```bash
./gradlew test lintDebug
```

Clean the build outputs:

```bash
./gradlew clean
```

## ADB Setup

Verify that the device is connected:

```bash
adb devices
```

Install or update the debug build:

```bash
adb install -r app/build/outputs/apk/debug/AltShift-v<version>-debug.apk
```

Grant the secure settings permission to enable autonomous input method switching:

```bash
adb shell pm grant ru.b1sted.altshift android.permission.WRITE_SECURE_SETTINGS
```

This development permission is **optional**. Without it, AltShift keeps working normally: the Dynamic Island capsule lets the user switch keyboards with a single tap, and the standard notification plus its heads-up variant are available as well. No computer is required for everyday use. Android 13 and newer also request notification permission on first launch.

Enable the input method:

```bash
adb shell ime enable ru.b1sted.altshift/.service.HardwareInputService
```

Select it as the active IME:

```bash
adb shell ime set ru.b1sted.altshift/.service.HardwareInputService
```

List the available and enabled input methods:

```bash
adb shell ime list -a -s
```

The same actions are available from **Settings → System → Languages & input → On-screen keyboard** on the device.

## Configuration

Open **AltShift** from the launcher.

| Setting | Default | Description |
| --- | --- | --- |
| Language switch | `Alt + Shift` | Toggles Russian transliteration. Captured from the next physical combination. |
| App hotkeys | Empty | Each binding maps a modifier mask and key code to an installed application. A combination already used by the language switch is rejected. |
| Theme | System default | Light, Dark, or System theme, applied application-wide and persisted. |
| App language | System default | `System default`, `English`, or `Русский`. Applied application-wide to the dashboard, onboarding, dialogs, Dynamic Island, notifications, and watchdog status, and kept in sync with Android 13+ System App Language Settings. |
| Seamless auto-switching | On | Switches between AltShift and the soft keyboard when a hardware keyboard attaches or detaches. Requires `WRITE_SECURE_SETTINGS`. |

The reconnect watchdog has no user setting. It is reconciled automatically from AltShift's enabled state, the active IME, and the available reconnect/alert paths; its silent status text follows the physical keyboard state and uses the `altshift_keyboard_watchdog` notification channel.

The fullscreen onboarding wizard reports whether AltShift is enabled and selected, requests notification permission on Android 13+, provides a one-tap overlay permission action, and requests a battery optimization exemption so the service survives Doze mode. The dashboard shows a toolbar status badge, the language shortcut card, the auto-switch toggle, and the app hotkey list. App shortcuts are stored per package and can be removed from the list.

On some manufacturer ROMs, the Doze exemption alone is not enough for post-reboot startup: Lenovo ZUI, Xiaomi HyperOS, and ColorOS can block `BOOT_COMPLETED` until **Auto-start** is enabled for AltShift in the system app settings. Enable that vendor toggle once to let monitoring resume automatically after a reboot. AltShift itself stays 100% AOSP with no vendor-specific branches; this is a user-side OEM control, not an app permission.

Configuration data lives in the app's private `SharedPreferences` file (`altshift_prefs`). Uninstalling the application removes it.

## License

Distributed under the **GNU General Public License v3.0**.

You are free to use, study, modify, and redistribute this software under the terms of the GPLv3. Derivative works must remain under the same license and preserve the copyright notices.

Full text of the license: [LICENSE](./LICENSE).
