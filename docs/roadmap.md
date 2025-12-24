# Implementation Strategy & Roadmap

## 1. Implementation Strategy
Our application will function as an **"Orchestrator"**. It will not rewrite the Linux kernel; it will package and manage these complex subsystems.

### Phase 1: The "Manager" App
Similar to *AnLinux* or *UserLAnd*, but with modern integrations.
1.  **Check Dependencies:** App checks if Termux and Termux:X11 are installed.
2.  **Script Generation:** The App contains "Recipes" (Shell scripts).
    *   *Example Recipe:* "Install Ubuntu 22.04 with XFCE"
3.  **Execution:** Pass the script to Termux.
    *   `pkg install proot-distro`
    *   `proot-distro install ubuntu`
    *   `proot-distro login ubuntu -- shared-tmp -- bash setup_xfce.sh`
4.  **Launch Interface:** One-click button "Start Ubuntu".
    *   Intent -> Starts Termux:X11 Activity.
    *   Intent -> Starts Termux Command: `termux-x11 :0 -xstartup "dbus-launch --exit-with-session xfce4-session"`

### Phase 2: Advanced Features (The "Wow" Factor)
*   **Hardware Acceleration Toggle:** Automate the setup of `virgl` or `turnip`.
    *   *Script logic:* Check `/proc/cpuinfo` -> If Qualcomm, install Turnip drivers.
*   **Box64/Wine Setup:** "Install Windows Support" button.
    *   Automates the complex compilation/installation of Box64 and Wine.
*   **Audio Forwarding:** Ensure `pulseaudio` runs on the host (Termux) and the container points `PULSE_SERVER` to the host socket.

## 2. Updated Implementation Roadmap
1.  **Phase 1: Foundation (The App)**
    *   Build Android App (Kotlin).
    *   Create `AssetManager` to store setup shell scripts (e.g., `setup_ubuntu.sh`, `start_xfce.sh`).
## 2. Updated Implementation Roadmap

Each phase has a detailed implementation guide. Click the links below to view them.

### [UI Design System (Glassmorphism)](ui_design.md)
*   **Goal:** Define the visual language (Blur, Translucency, Floating Nav).
*   **Reference:** Based on uploaded concepts and modern Glassmorphism principles.

### [Phase 1: Foundation & App Shell](phases/phase1_foundation.md)
*   **Goal:** Build the basic Android App structure and Asset Management system.
*   **Key Tasks:** Project setup, UI Dashboard, Script Vault logic.

### [Phase 2: The Bridge (Termux Integration)](phases/phase2_bridge.md)
*   **Goal:** Establish secure communication using **Intents**.
*   **Key Tasks:** Permission handling (`RUN_COMMAND`), `TermuxIntentFactory`, Connection testing.

### [Phase 3: Distro Management](phases/phase3_distro_management.md)
*   **Goal:** The core user flow - Install, Start, Desktop.
*   **Key Tasks:** Distro JSON definitions, Installation logic, complex "Launch Desktop" scripts.

### [Phase 4: Advanced Features](phases/phase4_advanced.md)
*   **Goal:** High-performance & "Wow" factors.
*   **Key Tasks:** Hardware Acceleration (Virgl/Turnip), **Rooted Chroot** mode, Windows/Box64 emulation.
