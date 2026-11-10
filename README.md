<h3 align="center">AltShift</h3>

<p align="center">
    <strong>
    A crash-resilient hardware keyboard IME for Russian transliteration and app hotkeys.
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
    <a href="#feature-matrix">Feature Matrix</a> &bull;
    <a href="#requirements">Requirements</a> &bull;
    <a href="#building-from-cli">Building from CLI</a> &bull;
    <a href="#local-signing">Local Signing</a> &bull;
    <a href="#adb-setup">ADB Setup</a> &bull;
    <a href="#configuration">Configuration</a> &bull;
    <a href="#license">License</a>
</p>
<hr>

<p align="center">
    <img src="docs/screenshots/gui.png" alt="AltShift Dashboard" width="760" />
</p>

A minimal input method service for physical keyboards on Android. AltShift maps Latin scancodes to Cyrillic characters through a single, allocation-free lookup table and launches applications from user-defined hardware key combinations. No cloud services, no telemetry, no background daemons.

## Overview

AltShift is a hardware-first IME. It does not draw a full soft keyboard; it sits between the physical keyboard and the focused application, translates key events when Russian mode is active, and consumes only the combinations explicitly configured by the user. Everything else is passed through untouched.

Version **v0.4** completes the transition to a universal AOSP architecture. The service relies exclusively on public Android APIs and behaves identically across Lenovo, Samsung, Xiaomi, Google Pixel, and other Android 8.0+ devices, with no vendor-specific branches or OEM workarounds. When a physical keyboard is attached or removed, AltShift can either switch input methods autonomously through `WRITE_SECURE_SETTINGS` or fall back to a one-tap Dynamic Island prompt and the native system IME picker.

The service is written in pure Java 21 with native XML views and Material 3 components. Configuration is stored locally in `SharedPreferences` as JSON. The project has no Kotlin source files, no Compose, and no annotation processors.

## Feature Matrix

| Area | Capability | v0.4 behavior |
| --- | --- | --- |
| Input engine | Full Russian PC layout emulation | Complete ЙЦУКЕН mapping for letters, punctuation, and the number row (`1..0`, `-`, `=`), with strict CapsLock isolation: CapsLock only affects alphabetic keys and never inverts digits or symbols. |
| Input engine | Zero-allocation typing loop | Static primitive-indexed lookup tables and preallocated strings; no iterators, string concatenation, or runtime allocation while typing. |
| Hotkeys | Custom application hotkeys | Bind any physical key combination to an installed app. Shortcuts are dispatched directly through the launcher intent, avoiding vendor background-launch prompts. |
| Switching | Tablet Dynamic Island | A 420dp floating capsule with a one-tap action that opens the direct system IME picker, designed for tablets and large screens. |
| Switching | Dual-mode input switching | On keyboard detach, AltShift returns to the native soft keyboard with zero permissions. With `WRITE_SECURE_SETTINGS`, autonomous attach/detach switching is also available. |
| Alerts | Standardized Heads-Up banners | Production channels `altshift_keyboard_alerts` and `altshift_safety_warnings` surface keyboard and safety events as Telegram-style drop-down heads-up alerts. |
| Alerts | Smart deferred inactive alert | The inactive-keyboard alert appears only after the user interacts with the picker and selects another keyboard. Ignored prompts produce zero spam. |
| Safety | Single-IME lockout protection | A dedicated safety warning fires when AltShift is the only enabled IME, preventing a lockout after the hardware keyboard is removed. |
| Safety | Fail-safe service | External intents, input connection calls, and secure-setting writes are guarded; an IME exception must never take down system input. |
| Compatibility | Universal AOSP architecture | 100% public API implementation with zero vendor lock-in. The same build runs identically on Lenovo, Samsung, Xiaomi, Google Pixel, and other AOSP-derived devices. |
| UI | Material 3 dashboard | Toolbar status badge, language shortcut card, auto-switch controls, hotkey list, key-capture dialog, and application picker. |
| UI | Theme selector | Light, Dark, and System modes, applied application-wide and persisted across launches. |
| UI | About dialog | Live hardware diagnostics (keyboard name, VID/PID, active IME, auto-switch mode), version details, repository link, and a re-run onboarding action. |
| UI | Fullscreen onboarding | Three-step wizard with large iconography, step indicator dots, permission verification, and one-tap actions. |
| Footprint | Zero telemetry, zero cloud | No analytics, no network permissions, no background daemons. Configuration stays in local `SharedPreferences`. |
| Footprint | Lean runtime | Pure Java 21, under 25 MB RAM, and 0% CPU while idle. No DI framework, no Kotlin, no Compose. |

## Requirements

- **JDK 21** for compilation and Gradle.
- **Android SDK Platform 34** and **Build-Tools 34.0.0**.
- **ADB** for device installation and IME activation.
- An Android device with a physical keyboard, running Android 8.0 (API 26) or newer.

## Building from CLI

The Gradle wrapper is pinned and committed. Point Gradle at your SDK with `local.properties`:

```properties
sdk.dir=/path/to/Android/Sdk
```

Build the debug APK:

```bash
./gradlew assembleDebug
```

The artifact is written to:

```text
app/build/outputs/apk/debug/AltShift-v0.4-debug.apk
```

Build an unsigned or environment-signed release APK:

```bash
./gradlew assembleRelease
```

The artifact is written to:

```text
app/build/outputs/apk/release/AltShift-v0.4.apk
```

Run the unit tests and Android Lint before shipping:

```bash
./gradlew test lintDebug
```

Clean the build outputs:

```bash
./gradlew clean
```

## Local Signing

Release signing is driven entirely by environment variables, so no keystore or password is ever committed. The repository ships a documented template at `scripts/build-release.sh.example`:

```bash
cp scripts/build-release.sh.example scripts/build-release.sh
chmod +x scripts/build-release.sh
```

Edit `KEYSTORE_FILE` and `KEY_ALIAS` in the local copy, then run:

```bash
./scripts/build-release.sh
```

The template exports `KEYSTORE_FILE` and `KEY_ALIAS`, reads `KEYSTORE_PASSWORD` from the environment (or prompts interactively), reuses it for `KEY_PASSWORD` unless overridden, and invokes `./gradlew assembleRelease`. The local script itself is ignored by Git through `scripts/build-release.sh` in `.gitignore`.

Equivalent manual invocation:

```bash
KEYSTORE_FILE="$HOME/.android/keystores/altshift-release.jks" \
KEYSTORE_PASSWORD="..." \
KEY_ALIAS="altshift" \
KEY_PASSWORD="..." \
./gradlew assembleRelease
```

CI uses the same contract: it decodes `KEYSTORE_BASE64` into `app/release.jks`, maps the keystore secrets to the environment variables above, builds both debug and release APKs, and publishes them to the GitHub release for `v*` tags.

## ADB Setup

Verify that the device is connected:

```bash
adb devices
```

Install or update the debug build:

```bash
adb install -r app/build/outputs/apk/debug/AltShift-v0.4-debug.apk
```

Grant the secure settings permission to enable autonomous input method switching:

```bash
adb shell pm grant ru.b1sted.altshift android.permission.WRITE_SECURE_SETTINGS
```

This development permission is optional. Without it, AltShift falls back to the Dynamic Island prompt, a tap-to-switch notification, and the native keyboard picker. Android 13 and newer also request notification permission on first launch.

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
| Theme | System default | Light, dark, or system theme, applied application-wide and persisted. |
| Seamless auto-switching | On | Switches between AltShift and the soft keyboard when a hardware keyboard attaches or detaches. Requires `WRITE_SECURE_SETTINGS`. |

The fullscreen onboarding wizard reports whether AltShift is enabled and selected, requests notification permission on Android 13+, and provides a one-tap overlay permission action. The dashboard shows a toolbar status badge, the language shortcut card, the auto-switch toggle, and the app hotkey list. App shortcuts are stored per package and can be removed from the list.

Configuration data lives in the app's private `SharedPreferences` file. Uninstalling the application removes it.

## License

Distributed under the **GNU General Public License v3.0**.

You are free to use, study, modify, and redistribute this software under the terms of the GPLv3. Derivative works must remain under the same license and preserve the copyright notices.

Full text of the license: [LICENSE](./LICENSE).
