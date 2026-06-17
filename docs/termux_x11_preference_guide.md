# Termux:X11 Preferences Guide

This guide documents the correct usage of the `termux-x11-preference` tool, used to configure the Termux:X11 Android application from the command line or external scripts.

## Command Syntax

The correct syntax for applying preferences uses a **colon (`:`)** separator between the key and value.

```bash
termux-x11-preference key:value [key2:value2] ...
```

### Examples

**Enable Fullscreen:**
```bash
termux-x11-preference fullscreen:true
```

**Set Display Scale and Hide Cutout:**
```bash
termux-x11-preference displayScale:150 hideCutout:true
```

**Configure Input Mode:**
```bash
termux-x11-preference touchMode:Trackpad pointerCapture:true
```

## Critical Requirements

1.  **Single Command Execution:**
    You should apply multiple preferences in a **single command** string. Chaining multiple `termux-x11-preference` calls sequentially can cause race conditions or the tool to hang if the X11 app is not in the foreground.

    *   ✅ **Correct:** `termux-x11-preference fullscreen:true showAdditionalKbd:false`
    *   ❌ **Avoid:**
        ```bash
        termux-x11-preference fullscreen:true
        termux-x11-preference showAdditionalKbd:false
        ```

2.  **External App Access (Manual Step):**
    For FluxLinux Pro (or any external app) to send commands to Termux, **you must manually configure this permission**.

    1.  Open Termux.
    2.  Run: `nano ~/.termux/termux.properties`
    3.  Add or uncomment the line:
        ```properties
        allow-external-apps = true
        ```
    4.  Save (`Ctrl+O`, `Enter`) and Exit (`Ctrl+X`).
    5.  Run: `termux-reload-settings`

    > **Note:** The setup script does *not* do this for you automatically.

3.  **Active Session:**
    The Termux:X11 application must be running (foreground or background) for the preferences to be applied.

## Valid Preference Keys

The following keys have been verified as valid configuration options:

### Display
*   `fullscreen` (true/false): Toggle full screen mode.
*   `displayScale` (integer): Percentage scale (e.g., `100`, `150`).
*   `hideCutout` (true/false): Hide camera cutout/notch.
*   `keepScreenOn` (true/false): Prevent screen from sleeping.
*   `displayResolutionMode` (native/scaled/custom): Resolution strategy.
*   `displayResolutionExact` (e.g., `1920x1080`): Exact resolution when mode is custom.

### Input
*   `touchMode` (Touchscreen/Trackpad): How touch input is handled.
*   `scaleTouchpad` (true/false): Whether trackpad movement is scaled.
*   `pointerCapture` (true/false): Capture mouse pointer.
*   `showAdditionalKbd` (true/false): Show the extra key row (ESC, TAB, etc.).
*   `showIMEWhileExternalConnected` (true/false): Show soft keyboard even with physical keyboard.
*   `clipboardEnable` (true/false): Sync clipboard between Android and X11.
*   `preferScancodes` (true/false): Use raw scancodes for keyboard input.

### Other
*   `volumeUpAction` / `volumeDownAction` (no action/toggle soft keyboard/...): Map volume keys.
*   `backButtonAction`: Map the Android back button.

## Troubleshooting

If the command prints the usage help message instead of applying settings, it means the syntax is incorrect. Ensure you are using the **colon (`:`)** separator and not an equals sign (`=`).

**Check Logs:**
You can verify valid keys on your specific version by running:
```bash
termux-x11-preference list
```
