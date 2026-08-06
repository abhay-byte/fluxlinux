<div align="center">
  <img src="../../assets/logo/logo.webp" width="160" alt="FluxLinux Logo" />
  <h1>🐧 Setting up Debian PRoot</h1>
  <p>This tutorial will guide you step-by-step through configuring and installing a Debian PRoot (non-rooted) distribution on your Android device using FluxLinux.</p>
</div>

---

## 📖 Table of Contents

1. [🐧 Step 1: Select Debian Distribution](#-step-1-select-debian-distribution)
2. [⚙️ Step 2: Configure Debian Settings](#️-step-2-configure-debian-settings)
3. [🖥️ Step 3: Install in Flux Terminal](#️-step-3-install-in-flux-terminal)
4. [🎉 Step 4: Post-Installation Redirection](#-step-4-post-installation-redirection)
5. [📦 Step 5: Customizing Modules](#-step-5-customizing-modules)
6. [🚀 Step 6: Select Launch Mode](#-step-6-select-launch-mode)
7. [💻 Step 7: Running in CLI Mode](#-step-7-running-in-cli-mode)
8. [🖥️ Step 8: Running in GUI Mode (XFCE4)](#️-step-8-running-in-gui-mode-xfce4)
9. [🛑 Step 9: Controlling the Session](#-step-9-controlling-the-session)
10. [✨ Step 10: Running Desktop Applications & Benchmarks](#-step-10-running-desktop-applications--benchmarks)
11. [💡 Important Tips & Troubleshooting](#-important-tips--troubleshooting)

---

## 🐧 Step 1: Select Debian Distribution

Launch the FluxLinux app and navigate to the **Distributions** tab or page. Here you will see a list of available Linux distributions that you can install.

1. Select **Debian** from the list of distributions.
2. Tap on the Debian option to open its configuration and installation settings.

| Action / State | Screenshot | Description |
| :--- | :---: | :--- |
| **Select Debian** | <img src="img/debian-proot/step-1-go-to-distro-page.png" width="500" /> | Select **Debian** from the distributions list to configure its setup profile. |

---

## ⚙️ Step 2: Configure Debian Settings

Before generating the installation commands, you need to set up the container configuration profile to fit your device specifications.

> [!NOTE]
> You can configure these options in any way you prefer according to your preferences and device specifications. The configurations described below are simply an example of a minimal setup.

1. **Select Mode:** Choose **PRoot** (this mode does not require root access and runs on any Android device).
2. **CPU Architecture:** Select your device architecture (typically `arm64` for modern devices).
3. **Desktop Environment:** Select **XFCE4** (recommended for a lightweight, feature-rich graphical interface).
4. **User Configurations:**
   - Define a custom **User Name** (e.g. `flux`).
   - Define a secure **Password** for the user account.
5. **Hardware Acceleration:** Configure GPU/hardware rendering:
   - Select **Turnip + Zink** for Adreno GPUs (modern 3D hardware acceleration).
   - Select **VirGL** or **None (Software rendering)** depending on your device capability.

| Action / State | Screenshot | Description |
| :--- | :---: | :--- |
| **Configure Distro (Part 1)** | <img src="img/debian-proot/step-2-configure-distro-1.png" width="500" /> | Select **PRoot** mode, CPU architecture, and choose the desktop environment (XFCE4). |
| **Configure Distro (Part 2)** | <img src="img/debian-proot/step-2-configure-distro-2.png" width="500" /> | Enter your username, password, select GPU Acceleration settings, and tap **Generate Setup Command**. |

---

## 🖥️ Step 3: Install in Flux Terminal

FluxLinux now ships its own embedded Linux host (bootstrap) inside the APK — no separate Termux app is required for Debian installs.

1. On the configuration screen, tap **Install in Flux Terminal**.
2. FluxLinux extracts the embedded host environment and opens an in-app terminal session running the install: the bundled Debian rootfs is installed locally via `proot-distro install <archive> --name debian` — **no network download of the rootfs and no paste-into-Termux**.
3. Watch the progress in the terminal; the session stays alive even when you switch apps (foreground notification).
4. On success the app marks Debian as installed automatically.

> [!TIP]
> The Debian rootfs is pinned (SHA256-verified) and identical for the PRoot and Rooted (chroot) install paths.

| Action / State | Screenshot | Description |
| :--- | :---: | :--- |
| **Install in Flux Terminal** | UI TBD | Review your selections, then tap **Install in Flux Terminal** to start the in-app install. |
| **In-app install session** | UI TBD | The terminal runs `flux_install.sh debian` against the local rootfs archive. |

---

## 🎉 Step 4: Post-Installation Redirection

Once the install script finishes successfully in the in-app terminal, return to the Distros page. You will find that the Debian distribution is now marked as installed and ready to be launched.

| Action / State | Screenshot | Description |
| :--- | :---: | :--- |
| **Post-Installation** | <img src="img/debian-proot/step-5-after-installed-u-will-be-redirected-to-app.png" width="500" /> | After the install session completes, the app automatically updates the status to show Debian is installed. |

---

## 📦 Step 5: Customizing Modules

In the distribution configuration settings, you can customize your installation by enabling or disabling any optional modules you want (such as audio, custom packages, extra drivers, or utilities). Component installs run inside the same in-app Flux Terminal session (PRoot guest).

| Action / State | Screenshot | Description |
| :--- | :---: | :--- |
| **Custom Modules** | <img src="img/debian-proot/step-6-u-can-install-any-module-u-want-in-distro-configure.png" width="500" /> | Toggle optional modules in the distro configuration to add audio, extra utilities, or other packages. |

---

## 🚀 Step 6: Select Launch Mode

Once the installation is complete, tapping the **Launch** button on the Debian distribution page inside FluxLinux will present you with options to choose your launch mode:
- **CLI (Command Line Interface)**: Starts a lightweight terminal session.
- **GUI (Graphical User Interface)**: Launches a full XFCE4 desktop environment.

| Action / State | Screenshot | Description |
| :--- | :---: | :--- |
| **Launch Mode Selection** | <img src="img/debian-proot/step-7-launch-mode.png" width="500" /> | Select whether to start your Debian container in command-line only (CLI) or graphical (GUI) desktop mode. |

---

## 💻 Step 7: Running in CLI Mode

Tap **Open Shell** on the installed Debian card to open an in-app Flux Terminal session logged into the Debian guest as user `flux` (tap **Open Root Shell** for `root`). This is useful for using standard command-line tools, configuring packages via `apt`, or running background servers.

| Action / State | Screenshot | Description |
| :--- | :---: | :--- |
| **Debian CLI Shell** | UI TBD | In-app terminal session logged directly into the Debian shell (`whoami` → `flux`). |

---

## 🖥️ Step 8: Running in GUI Mode (XFCE4)

Selecting GUI mode launches the X11 server backend and starts the XFCE4 desktop environment, opening it automatically via the Termux:X11 display companion.

| Action / State | Screenshot | Description |
| :--- | :---: | :--- |
| **Debian XFCE4 Desktop** | <img src="img/debian-proot/step-9-gui-xfce4.png" width="500" /> | A fully featured Debian XFCE4 graphical user interface running on your Android device. |

---

## 🛑 Step 9: Controlling the Session

While the distribution is running in GUI or CLI mode, you can control and monitor the active background session directly from the FluxLinux interface.
- **Open X11**: Reopen the graphical display viewer window if you accidentally swiped it away (GUI requires the Termux:X11 companion app — optional, CLI shells do not).
- **Stop**: Safely shut down all background Debian processes and host services.

| Action / State | Screenshot | Description |
| :--- | :---: | :--- |
| **Session Control** | <img src="img/debian-proot/step-10-stop-or-open-x11.png" width="500" /> | Manage the running container session to stop it or reopen the X11 display. |

---

## ✨ Step 10: Running Desktop Applications & Benchmarks

Once inside the desktop environment, you have access to a variety of pre-installed applications and can run benchmarks to test performance.

- **Thunar File Manager**: Browse your Debian system and access shared files on your Android local storage.
- **GLMark2 GPU Benchmark**: Test 3D acceleration and rendering performance using Turnip + Zink.

| Action / State | Screenshot | Description |
| :--- | :---: | :--- |
| **Thunar File Manager** | <img src="img/debian-proot/thunar.png" width="500" /> | Manage files, directories, and documents visually on the desktop. |
| **GLMark2 GPU Benchmark** | <img src="img/debian-proot/glmark2.png" width="500" /> | Execute 3D graphics benchmarks to verify hardware-accelerated GPU performance. |

---

## 💡 Important Tips & Troubleshooting

### 🔄 Keep the In-App Terminal Alive
In-app terminal sessions run in a foreground service; they stay alive when you switch apps. Closing the Terminal screen does not kill sessions — close them via the tab × or **Terminal → close all**.

### ⚡ PRoot vs Chroot Mode
* **PRoot Mode:** Used in this guide. It runs entirely in user-space, requires **no root permissions**, and intercepts system calls to simulate root actions.
* **Chroot Mode:** Requires root permissions. It provides near-native performance and full hardware access but requires your Android device to be rooted.

### 🏎️ Troubleshooting Hardware Acceleration (GPU)
* If your graphical environment crashes or has display artifacts:
  1. Try disabling Hardware Acceleration (set it to **None / Software Rendering**) in the distro configuration page.
  2. If you have a Snapdragon device with an Adreno GPU, ensure that your device supports **Turnip + Zink** for optimal Vulkan/OpenGL performance.
