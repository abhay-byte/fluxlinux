# Phase 1: Foundation & App Shell

**Goal:** Build the basic Android application structure (`com.fluxlinux.app`) and the Asset Management system to store automation scripts.

## 1.1 Android Project Setup
*   **Language:** Kotlin
*   **Minimum SDK:** Android 8.0 (API 26) - Required for Termux compatibility.
*   **Target SDK:** Android 34 (Latest) - *Note: We use the Orchestrator pattern, so targeting 34 is safe.*
*   **UI Framework:** Jetpack Compose (Material 3) for a modern, fluid interface.

## 1.2 Core Dependencies
Add these to `build.gradle.kts`:
*   `androidx.compose:compose-bom` (UI)
*   `com.google.accompanist` (Permissions)
*   `androidx.lifecycle` (ViewModel for managing state)
*   **UI FX:** `dev.chrisbanes.haze:haze` (For critical glass/blur effects).

## 1.3 UI & Design System (Glassmorphism)
**Strict Requirement:** The app must implement the design specs defined in `docs/ui_design.md`.
1.  **Main Layout:** Scaffold with a **Full-Screen Gradient Background** (Z-Index 0).
2.  **Navigation:** `GlassScaffold` holding a **Floating Bottom Bar**.
3.  **Theme:** Dark Theme ONLY.

## 1.4 Asset Management (The Script Vault)
We need a robust system to manage the shell scripts that Termux will execute.
*   **Directory:** `app/src/main/assets/scripts/`
*   **Required Scripts:**
    *   `setup_termux.sh`: Sets up basic packages (`proot-distro`, `x11-repo`).
    *   `install_distro.sh`: Wrapper for `proot-distro install`.
    *   `launch_gui.sh`: Sets up `termux-x11` and `pulse-audio` before launching the DE.

## 1.4 Deliverables for Phase 1
1.  **Repo Structure:** A working Android Studio project.
2.  **Home Screen UI:** A simple Dashboard showing "Termux Status" (Not Installed / Ready).
3.  **Asset Copier:** A utility function `ScriptManager.extractScripts()` that copies our assets to the App's private file storage (ready to be sent to Termux later).
