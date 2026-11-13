# STYLEGUIDE.md — AltShift Engineering Standard

This document defines the strict architectural, performance, and code-style standards for the entire AltShift project (`ru.b1sted.altshift`). Every component—from low-level input dispatchers to UI dialogs—must comply with these guidelines.

---

## 1. Core Principles & Resource Budgets

AltShift is a background hardware utility. It must operate like a native C/Rust daemon with the safety of modern Java 21.

*   **Idle RAM:** $\le$ 64 MB total PSS in the attached typing or detached watchdog state, with Java + native heap allocation under 15 MB (Java heap ~2–4 MB). Active UI and app-picker memory is transient and must return to the idle budget after the activity exits.
*   **Idle CPU:** Exactly 0.0% CPU when no keys are pressed. No background polling loops, no continuous timers.
*   **Keystroke Latency:** $\le$ 1 ms dispatch time. Zero heap allocations during typing (`onKeyDown`).
*   **UI Thread Guarantee:** 60/120 FPS without frame drops. No Binder IPC calls, synchronous disk I/O, or icon decoding on the main thread.
*   **Readability:** Code must be structured in distinct logical paragraphs. Clean, idiomatic Java 21 without syntactic noise.

---

## 2. Formatting & Visual Hygiene

### 2.1. Code Paragraphs (Vertical Rhythm)
Never write solid walls of code. Every method must breathe. Separate logical steps with **exactly one blank line**:
1. Guard clauses and parameter validation.
2. State extraction and local variable setup.
3. Core transformation / business logic.
4. Mutation, dispatch, or return statement.

```java
// ❌ REJECTED: Solid wall of text
public void saveBinding(HotkeyBinding binding) {
    Objects.requireNonNull(binding, "binding");
    synchronized (lock) {
        HotkeyBinding[] current = bindings;
        HotkeyBinding[] updated = upsertBinding(current, binding);
        persistBindings(updated);
        bindings = updated;
    }
}

// ✅ MANDATORY: Distinct logical paragraphs
public void saveBinding(HotkeyBinding binding) {
    Objects.requireNonNull(binding, "binding");

    synchronized (lock) {
        HotkeyBinding[] updated = upsertBinding(bindings, binding);
        persistBindings(updated);
        bindings = updated;
    }
}
```

### 2.2. The `final` Keyword Policy (Elimination of Syntactic Clutter)
Do not clutter method bodies with redundant keywords. Modern Java variables are effectively final by default.

*   **USE `final` FOR:**
    *   Constants: `private static final String TAG = "AltShift";`
    *   Class fields (immutability): `private final SharedPreferences preferences;`
    *   Explicit immutable class declarations: `public final class HardwareInputService`
*   **NEVER USE `final` FOR:**
    *   Method parameters: `void onBindViewHolder(HotkeyViewHolder holder, int position)`
    *   Local variables: `int keyCode = event.getKeyCode();`
    *   Loop iterators: `for (int index = 0; index < size; index++)` or `for (AppEntry entry : items)`
    *   Catch blocks: `catch (RuntimeException exception)`

### 2.3. Guard Clauses & Inverted Conditions
Eliminate nested `if-else` pyramids. The maximum indentation depth inside any method body is **2 levels**.
* Validate preconditions and error states first.
* Return, break, or continue immediately.
* Keep the main execution path at the lowest possible indentation level.

```java
// ❌ REJECTED: Deeply nested pyramid
@Override
public boolean onKeyDown(int keyCode, KeyEvent event) {
    if (event != null) {
        if (event.getRepeatCount() == 0) {
            if (isLanguageToggle(keyCode, event)) {
                isRussian = !isRussian;
                return true;
            }
        }
    }
    return super.onKeyDown(keyCode, event);
}

// ✅ MANDATORY: Clean guard clauses
@Override
public boolean onKeyDown(int keyCode, KeyEvent event) {
    if (event == null) {
        return false;
    }

    if (event.getRepeatCount() == 0 && isLanguageToggle(keyCode, event)) {
        isRussian = !isRussian;
        return true;
    }

    // Main execution path...
    return super.onKeyDown(keyCode, event);
}
```

---

## 3. Input Engine & Hot-Path Rules (`service/`, `model/`)

The keystroke path (`onKeyDown`, `lookup`, `commitMapped`) executes on every physical press. It must never trigger Garbage Collection (GC) or IPC bottlenecks.

### 3.1. Zero Allocations in `onKeyDown()`
*   **No Object Creation:** Absolutely no `new`, no autoboxing (`int` $\to$ `Integer`), and no `String` concatenations (`+`, `StringBuilder`).
*   **No Iterator Instantiation:** Never use enhanced `for-each` loops over collections in the hot path. Use indexed loops with pre-cached lengths or static arrays.
*   **Fast Rejection:** Do not evaluate hotkey lists if no modifier keys (`Ctrl`, `Alt`, `Meta`) are held down.

### 3.2. Cached Hardware State (Eliminate Binder IPC in Hot Paths)
*   **Never call `InputManager.getInputDeviceIds()` or `manager.getInputDevice()` inside hot paths** (such as `onEvaluateInputViewShown()` or `onKeyDown()`). These are synchronous Binder IPC calls to `system_server`.
*   Maintain keyboard state in a cached boolean field (`keyboardPresent`).
*   Update this state **only** when notified by `InputDeviceListener` callbacks (`onInputDeviceAdded`, `onInputDeviceRemoved`) or `onConfigurationChanged()`.

### 3.3. Transliteration Table Optimization
*   Lookup tables must use direct array index lookups over keycodes: `TABLE[keyCode]`.
*   Store pre-allocated immutable `String` instances or char arrays.
*   Validate bounds using primitive comparisons before accessing arrays.

---

## 4. Concurrency & Persistence Rules (`storage/`)

### 4.1. Non-Blocking I/O
*   **Never use `SharedPreferences.Editor.commit()`.** It executes synchronous, blocking disk writes on the calling thread, causing UI stutters and IME frame drops.
*   **Always use `SharedPreferences.Editor.apply()`.** It mutates memory synchronously and schedules disk I/O on a background worker thread.

### 4.2. Lock-Free Reads
*   Typing must never wait for storage locks.
*   Store hotkeys and settings in `volatile` references to immutable arrays or lists.
*   `synchronized(lock)` blocks are permitted **only** during write mutations (`saveBinding`, `deleteBinding`). Keystroke validation must read from the volatile snapshot without synchronization.

```java
// ✅ MANDATORY: Lock-free read, isolated synchronized write
public final class HotkeyRepository {
    private final Object lock = new Object();
    private volatile HotkeyBinding[] bindings;

    public HotkeyBinding[] getBindings() {
        return bindings; // Direct reference read, zero contention
    }

    public void saveBinding(HotkeyBinding binding) {
        Objects.requireNonNull(binding, "binding");

        synchronized (lock) {
            HotkeyBinding[] updated = upsert(bindings, binding);
            persist(updated);
            bindings = updated; // Atomic volatile swap
        }
    }
}
```

---

## 5. UI & Presentation Layer Standards (`ui/`, `adapters/`, `dialogs/`)

### 5.1. Prohibition of Legacy Android 2.x Patterns
*   **No `BaseAdapter` with `setTag()`/`getTag()`:** Replace all legacy list adapters with `RecyclerView.Adapter` or `ListAdapter` utilizing `DiffUtil`.
*   **No One-Element Array Closures:** Never create mutable single-element arrays (e.g. `final int[] capturedKeyCode = {0};` or `final boolean[] capturedCtrl = {false};`) to bypass closure finality. Use dedicated static state holder classes or private record/POJO models.

```java
// ❌ REJECTED: Single-element array closure hack
final int[] capturedKeyCode = {0};
final boolean[] capturedCtrl = {false};
dialog.setOnKeyListener((d, keyCode, event) -> {
    capturedKeyCode[0] = keyCode;
    capturedCtrl[0] = event.isCtrlPressed();
    return true;
});

// ✅ MANDATORY: Clean mutable state object
static final class KeyCaptureState {
    int keyCode = 0;
    boolean ctrl;
    boolean alt;
    boolean shift;
    boolean meta;

    boolean isValid() {
        return keyCode > 0;
    }
}
```

### 5.2. Asynchronous Package & Icon Loading
*   **Never call `PackageManager.queryIntentActivities()` on the Main UI thread.** In devices with 200+ apps, this takes 200–800 ms and causes Application Not Responding (ANR) warnings.
*   **Never call `packageManager.getApplicationIcon()` inside `onBindViewHolder()`.** Decoding application drawables during scrolling destroys scroll performance.
*   Load app listings and icons asynchronously in background threads via an `ExecutorService` and publish results to the UI thread.

### 5.3. ViewBinding Lifecycle Safety
*   Avoid memory leaks in Activities and Dialogs.
*   Dialog view bindings must be cleared or scoped strictly to dialog lifecycles.
*   In `RecyclerView.ViewHolder`, bind views directly through generated binding references without `findViewById` traversal.

### 5.4. Dynamic Island Overlay Conventions
*   A floating `WindowManager` overlay must use a transparent outer `FrameLayout` with at least `16dp` padding, `android:clipToPadding="false"`, and `android:clipChildren="false"`.
*   Put elevation and the subtle `1dp` `?attr/colorOutline` stroke on the inner `MaterialCardView` (`28dp` radius, `?attr/colorSurface`), never on the `WRAP_CONTENT` window root, so the shadow is never clipped into a rectangle.
*   Keep `Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL`, account for the outer padding in the bottom offset, and let phones use the inset width while `sw600dp+` keeps the `420dp` minimum.

---

## 6. Modern Java 21 Language Idioms

### 6.1. Modern Switch Expressions
Replace legacy `switch` statements containing verbose `case ...: return ...; default: return ...;` with Java 21 arrow switch expressions.

```java
// ❌ REJECTED: Verbose legacy switch
public static boolean isModifierKey(final int keyCode) {
    switch (keyCode) {
        case KeyEvent.KEYCODE_SHIFT_LEFT:
        case KeyEvent.KEYCODE_SHIFT_RIGHT:
        case KeyEvent.KEYCODE_ALT_LEFT:
        case KeyEvent.KEYCODE_ALT_RIGHT:
            return true;
        default:
            return false;
    }
}

// ✅ MANDATORY: Modern Java 21 switch expression
public static boolean isModifierKey(int keyCode) {
    return switch (keyCode) {
        case KeyEvent.KEYCODE_SHIFT_LEFT,
             KeyEvent.KEYCODE_SHIFT_RIGHT,
             KeyEvent.KEYCODE_ALT_LEFT,
             KeyEvent.KEYCODE_ALT_RIGHT,
             KeyEvent.KEYCODE_CTRL_LEFT,
             KeyEvent.KEYCODE_CTRL_RIGHT,
             KeyEvent.KEYCODE_META_LEFT,
             KeyEvent.KEYCODE_META_RIGHT -> true;
        default -> false;
    };
}
```

### 6.2. Immutability via `record`
*   All pure data carriers must be declared as `public record`.
*   Validation must reside in the compact constructor.
*   Do not write repetitive boilerplate getters, `hashCode()`, or `equals()`.

---

## 7. Defensive Programming & Error Handling

### 7.1. IME Isolation Guarantee
An unhandled exception in an `InputMethodService` crashes Android's system input framework, freezing user input across all apps.
*   All calls to `startActivity()`, `getCurrentInputConnection()`, and system service dispatchers must be guarded with explicit `try-catch` blocks.
*   Log caught exceptions with meaningful tags and context: `Log.w(TAG, "Failed to launch intent for: " + packageName, e);`.
*   Never write empty catch blocks (`catch (Exception ignored) {}`). Always log or handle.

### 7.2. Clean Fallbacks Over Nested Try-Catch
Do not chain nested `try-catch` blocks for permission fallbacks. Encapsulate multi-intent fallbacks into clean helper methods with clear intent sequencing.

```java
// ❌ REJECTED: Chained nested try-catch
try {
    startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
} catch (final RuntimeException first) {
    try {
        startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION));
    } catch (final RuntimeException second) {
        toast(R.string.overlay_settings_failed);
    }
}

// ✅ MANDATORY: Clean sequential intent resolution
private void openOverlaySettings() {
    Intent packageIntent = new Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:" + getPackageName()));

    if (tryStartActivity(packageIntent)) {
        return;
    }

    Intent genericIntent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
    if (!tryStartActivity(genericIntent)) {
        toast(R.string.overlay_settings_failed);
    }
}

private boolean tryStartActivity(Intent intent) {
    try {
        startActivity(intent);
        return true;
    } catch (RuntimeException e) {
        Log.w(TAG, "Cannot resolve activity for intent: " + intent, e);
        return false;
    }
}
```

---

## 8. Cached Apps Freezer & Watchdog Foreground Service Standard

AltShift owns no `Activity` while the user types in another application. Once the platform unbinds `HardwareInputService`, `ru.b1sted.altshift` becomes a cached process. On Android 12+ the Cached Apps Freezer may then set `isFrozen=true` and `oom_adj=900` (`cch LAST`), even when battery optimization is disabled for the app.

*   **Frozen processes miss input callbacks.** A frozen process receives no `InputManager.InputDeviceListener` Binder callbacks. A pogo-pin or external keyboard can reconnect while the process is frozen, leaving the device stuck on the soft keyboard with no way for AltShift to react.
*   **The watchdog is a temporary thaw, not a permanent daemon.** `KeyboardWatchdogService` exists solely to keep the process unfrozen while a physical keyboard is detached and reconnect monitoring is active. It is not a background keep-alive for the whole application.

### 8.1. When `KeyboardWatchdogService` May Run
Start the watchdog only when **all** of the following are true:
1. Onboarding is completed.
2. No physical keyboard is currently attached.
3. AltShift is enabled in the system IME settings but is **not** the active IME.
4. At least one reconnect mechanism is usable: `autoSwitchEnabled` with `WRITE_SECURE_SETTINGS`, or the `SYSTEM_ALERT_WINDOW` overlay permission.

### 8.2. Mandatory Shutdown Rules
*   Call `stopSelf()` (or `KeyboardWatchdogService.stop()`) immediately when a physical keyboard is attached. **Zero foreground-service notifications may remain while a keyboard is attached.**
*   Stop when AltShift becomes the active IME, because the live `InputMethodService` binding already keeps the process unfrozen.
*   Stop when onboarding is reset, AltShift is disabled, or no reconnect mechanism remains available.
*   Reconcile the watchdog from the existing process-wide monitor whenever the keyboard state or input-method settings change; never rely on a polling loop.

### 8.3. Android 14 Foreground Service Declaration
*   Declare `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_SPECIAL_USE` in `AndroidManifest.xml`.
*   Use `android:foregroundServiceType="specialUse"` with the `android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE` property. Do **not** use `connectedDevice`: it requires Bluetooth, USB, or Wi-Fi permissions that AltShift intentionally avoids.
*   On API 34+, call `startForeground(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)`; older releases use the two-argument overload.
*   Use the `NotificationChannels.KEYBOARD_WATCHDOG` channel (`IMPORTANCE_MIN`, no vibration, no sound, no badge), keep the notification ongoing, and set `FOREGROUND_SERVICE_IMMEDIATE` on API 31+. Silence comes from the channel, so the framework `Notification.Builder` stays compatible with `minSdk 26`.

### 8.4. Defensive Foreground-Service Starts
Every `startForegroundService()` and `startForeground()` call must be wrapped in `try-catch`. The platform may reject a background start with `ForegroundServiceStartNotAllowedException` (API 31+, a `RuntimeException`) or with a `SecurityException` when the FGS type or permission is unavailable. Log the rejection and continue; a denied watchdog must never crash the IME or the boot receiver.

```java
// ✅ MANDATORY: Guarded background foreground-service start
try {
    appContext.startForegroundService(new Intent(appContext, KeyboardWatchdogService.class));
} catch (SecurityException exception) {
    Log.w(TAG, "Unable to start the keyboard watchdog service", exception);
} catch (RuntimeException exception) {
    // ForegroundServiceStartNotAllowedException on API 31+.
    Log.w(TAG, "Background foreground-service start was denied", exception);
}
```

### 8.5. Inactive-Keyboard Notification Convention
*   The `KeyboardNotification` reminder uses the versioned `altshift_keyboard_alerts_v2` channel at `IMPORTANCE_DEFAULT`: it appears in the shade but never as a heads-up banner.
*   Do not set `Notification.PRIORITY_HIGH` or `Notification.DEFAULT_ALL`; keep the reminder swipe-dismissible with `setAutoCancel(true)` and no `setOngoing(true)`.
*   Post it only after the user closes or times out the Dynamic Island, or cancels the system picker. An explicit **Switch back** action and every programmatic dismissal must not post it.
*   Cancel it when AltShift becomes the active IME or the physical keyboard detaches, so the reminder never outlives the condition it describes.

---

## 9. Code Review Compliance Checklist

Before committing any Java file, verify each checkpoint:

- [ ] **Visual Spacing:** Are method bodies broken into logical paragraphs with single blank lines?
- [ ] **No `final` Noise:** Are method parameters, local variables, and loop iterators stripped of `final`?
- [ ] **No Blocking I/O:** Is `preferences.edit().apply()` used instead of `.commit()`?
- [ ] **No Hot-Path Allocations:** Is `onKeyDown()` completely free of `new`, string operations, and boxing?
- [ ] **No IPC in Hot Paths:** Does `onEvaluateInputViewShown()` check a cached boolean instead of calling `InputManager`?
- [ ] **Modern Switches:** Are all switches written as Java 21 arrow expressions (`case X -> true;`)?
- [ ] **No One-Element Arrays:** Are dialogs free of `int[]` and `boolean[]` closure workarounds?
- [ ] **Async UI:** Are package queries and app icons loaded off the main thread?
- [ ] **Fail-Safe IME:** Are all `startActivity` and `InputConnection` interactions wrapped in defensive exception guards?
- [ ] **Watchdog Scope:** Does `KeyboardWatchdogService` stop immediately once a physical keyboard is attached, leaving no FGS notification behind?
- [ ] **Guarded FGS Starts:** Are all `startForegroundService` and `startForeground` calls wrapped against `ForegroundServiceStartNotAllowedException` / `SecurityException` / `RuntimeException`?