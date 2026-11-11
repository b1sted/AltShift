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
    <a href="https://github.com/b1sted/AltShift/releases/tag/v0.4.1">
        <img src="https://img.shields.io/badge/Version-0.4.1-blue?style=flat-square" alt="Version" />
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
    <a href="#requirements">Requirements</a> &bull;
    <a href="#building-from-cli">Building from CLI</a> &bull;
    <a href="#adb-setup">ADB Setup</a> &bull;
    <a href="#configuration">Configuration</a> &bull;
    <a href="#license">License</a>
</p>
<hr>

<p align="center"><img src="docs/screenshots/gui.png" alt="AltShift Dashboard" width="760" /></p>

A minimal, hardware-first input method service for Android that turns a physical keyboard into a first-class input device. No cloud services, no analytics, and no background daemons.

## Overview

AltShift (`ru.b1sted.altshift`) is an input method service (IME) built for devices with a physical keyboard. It does not draw a full soft keyboard. Instead, it sits between the hardware keyboard and the focused application: it translates key events into the active layout, launches application hotkeys from user-defined combinations, and handles soft-keyboard fallbacks when the hardware keyboard is detached. Everything the user has not explicitly configured is passed through to the application untouched.

The driver is written in pure Java 21 with native XML views and Material 3 components. Configuration is stored locally in `SharedPreferences` as JSON. There is no Kotlin, no Compose, no dependency-injection framework, and no annotation processing. The implementation uses only public AOSP APIs, so the same build behaves identically across Android 8.0+ devices regardless of manufacturer or system skin.

When a physical keyboard connects or disconnects, AltShift can switch input methods autonomously through `WRITE_SECURE_SETTINGS`, or fall back to a one-tap Dynamic Island prompt and the native system IME picker. Both paths are crash-guarded: an IME failure must never take down system input or trap the user without a usable keyboard.

## Features

- **Full Russian PC layout emulation (ЙЦУКЕН)**: Complete letter, punctuation, and number-row mapping (`1..0`, `-`, `=`) with strict CapsLock isolation. CapsLock affects only alphabetic keys and never inverts digits or symbols.
- **Configurable language switch**: The default `Alt + Shift` combination toggles Russian transliteration and is captured from the next physical key combination in the settings UI. The OS subtype switcher is bypassed.
- **Global app shortcuts**: Bind any hardware key combination to an installed application. Shortcuts are dispatched directly through the launcher intent and consume only the combinations the user defines.
- **Tablet Dynamic Island**: A 420dp floating capsule with a one-tap action that opens the direct system IME picker, designed for tablets and large screens.
- **Dual-mode keyboard switching**: AltShift returns natively to the on-screen keyboard when the hardware keyboard is detached, and can additionally switch autonomously on attach and detach when `WRITE_SECURE_SETTINGS` is granted.
- **Heads-Up alert banners**: The `altshift_keyboard_alerts` and `altshift_safety_warnings` channels surface keyboard and safety events as heads-up notifications. Smart deferred timing shows the inactive-keyboard alert only after the user makes a picker choice, so ignored prompts produce no spam.
- **Single-IME safety protection**: A dedicated warning fires when AltShift is the only enabled keyboard, preventing a lockout after the hardware keyboard is removed.
- **Fail-safe service**: External intents, input connection calls, and secure-setting writes are guarded; an IME exception must never take down system input.
- **100% universal AOSP architecture**: Public-API-only implementation with zero vendor lock-in. The same build runs on Lenovo, Samsung, Xiaomi, Google Pixel, and other Android 8.0+ devices without OEM-specific branches.
- **Material 3 dashboard**: Toolbar status badge, language shortcut card, auto-switch controls, hotkey list, key-capture dialog, and application picker, with Light, Dark, and System themes applied application-wide.
- **About dialog with live diagnostics**: Version details, keyboard name, VID/PID, active IME, and switch mode, plus a repository link and a setup-wizard rerun action.
- **Fullscreen onboarding**: A three-step wizard with large iconography, step indicator dots, permission verification, and one-tap actions for IME enablement, selection, notifications, and overlay access.
- **Zero-waste resource policy**: Pure Java 21, under 25 MB RAM, 0.0% CPU while idle, and zero telemetry. No network permissions and no background daemons; configuration stays in local `SharedPreferences`.

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

This development permission is **optional**. Without it, AltShift keeps working normally: the Dynamic Island capsule lets the user switch keyboards with a single tap, and a tap-to-switch notification is available as well. No computer is required for everyday use. Android 13 and newer also request notification permission on first launch.

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
| App hotkeys | Empty | Each binding maps a modifier mask and key code to an installed application. |
| Theme | System default | Light, Dark, or System theme, applied application-wide and persisted. |
| Seamless auto-switching | On | Switches between AltShift and the soft keyboard when a hardware keyboard attaches or detaches. Requires `WRITE_SECURE_SETTINGS`. |

The fullscreen onboarding wizard reports whether AltShift is enabled and selected, requests notification permission on Android 13+, provides a one-tap overlay permission action, and requests a battery optimization exemption so the service survives Doze mode. The dashboard shows a toolbar status badge, the language shortcut card, the auto-switch toggle, and the app hotkey list. App shortcuts are stored per package and can be removed from the list.

Configuration data lives in the app's private `SharedPreferences` file (`altshift_prefs`). Uninstalling the application removes it.

## License

Distributed under the **GNU General Public License v3.0**.

You are free to use, study, modify, and redistribute this software under the terms of the GPLv3. Derivative works must remain under the same license and preserve the copyright notices.

Full text of the license: [LICENSE](./LICENSE).
