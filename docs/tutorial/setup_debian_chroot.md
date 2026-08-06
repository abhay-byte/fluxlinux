<div align="center">
  <img src="../../assets/logo/logo.webp" width="160" alt="FluxLinux Logo" />
  <h1>🐧 Setting up Debian Chroot (Root Required)</h1>
  <p>This tutorial will guide you step-by-step through configuring and installing a Debian Chroot distribution on your rooted Android device using FluxLinux. Chroot mode provides near-native performance and full hardware access.</p>
</div>

---

## 📖 Table of Contents

0. [🔑 Step 0: Grant Root Access to FluxLinux](#-step-0-grant-root-access-to-fluxlinux)
1. [🧰 Step 1: Install BusyBox NDK](#-step-1-install-busybox-ndk)
2. [🐧 Step 2: Select Debian (Rooted) Distribution](#-step-2-select-debian-rooted-distribution)
3. [⚙️ Step 3: Configure Debian Settings](#️-step-3-configure-debian-settings)
4. [📋 Step 4: Generate and Copy the Setup Command](#-step-4-generate-and-copy-the-setup-command)
5. [⚡ Step 5: Execute the Install in Root Shell](#-step-5-execute-the-install-in-root-shell)
6. [🎉 Step 6: Verify Installation in Home Screen](#-step-6-verify-installation-in-home-screen)
7. [🛑 Step 7: Controlling the Session](#-step-7-controlling-the-session)
8. [💡 Important Tips & Troubleshooting](#-important-tips--troubleshooting)

---

## 🔑 Step 0: Grant Root Access to FluxLinux

Before starting the installation, you must ensure that **FluxLinux** has root access permissions granted by your superuser manager (e.g., Magisk or KernelSU).

1. Open the FluxLinux app.
2. Open the **Debian (Rooted)** card (or **Settings → Root Access**) — FluxLinux will request superuser access.
3. When prompted by your root manager, grant root access permanently to FluxLinux.

| Action / State | Screenshot | Description |
| :--- | :---: | :--- |
| **Grant Root Access** | UI TBD | Grant superuser (root) permissions to FluxLinux to allow the Chroot container to run natively on your Android system. |

---

## 🧰 Step 1: Install BusyBox NDK

Because Chroot relies on low-level Linux utilities, you must install the BusyBox NDK module. You can download and install the BusyBox module directly inside your root manager (e.g. Magisk, KernelSU, or APatch).

1. Click **Download Module** on the BusyBox Installation screen.
2. Your browser will open the download for the BusyBox NDK installer file.
3. Flash the downloaded file inside your root manager's Module page.

| Action / State | Screenshot | Description |
| :--- | :---: | :--- |
| **BusyBox Installation Screen** | <img src="img/step-six-for-root-install-busybox.png" width="500" /> | FluxLinux will prompt you to install BusyBox NDK if it isn't detected. Click **Download Module**. |
| **Download Module** | <img src="img/step-six-download-busybox-module.png" width="500" /> | Your browser will download the BusyBox module package (`UPDATE-Busybox.Installer.zip`) to your phone's storage. |
| **Flash in Root Manager** | <img src="img/step-six-then-flash-module-in-your-root-application.png" width="500" /> | Open your root manager application (e.g. APatch, KernelSU, or Magisk), go to the Modules tab, select the downloaded file, and flash it. |

---

## 🐧 Step 2: Select Debian (Rooted) Distribution

Launch the FluxLinux app and navigate to the **Distributions** tab. Here you will see a list of available Linux distributions.

1. Select the **Debian (Rooted)** option.
2. Tap on it to open its configuration and installation settings.

| Action / State | Screenshot | Description |
| :--- | :---: | :--- |
| **Select Debian (Rooted)** | <img src="img/debian-chroot/step-one-distro-page.png" width="500" /> | Select the **Debian (Rooted)** distribution specifically built for Chroot environments. |

---

## ⚙️ Step 3: Configure Debian Settings

Before generating the installation commands, configure your container profile.

> [!NOTE]
> Chroot provides native performance but requires your device to be rooted. Configure your desktop environment and GPU settings according to your device's capabilities.

1. **Select Mode:** Choose **Chroot** (requires root).
2. **CPU Architecture:** Select your device architecture.
3. **Desktop Environment:** Select your preferred desktop (e.g., XFCE4 or KDE Plasma).
4. **User Configurations:** Define a custom **User Name** and **Password**.
5. **Hardware Acceleration:** Configure GPU/hardware rendering (e.g., Turnip + Zink for Adreno GPUs).

| Action / State | Screenshot | Description |
| :--- | :---: | :--- |
| **Configure Distro (Part 1)** | <img src="img/debian-chroot/step-two-configure-1.png" width="500" /> | Select the mode, desktop environment, and basic user settings. |
| **Configure Distro (Part 2)** | <img src="img/debian-chroot/step-two-configure-2.png" width="500" /> | Configure GPU hardware acceleration and any extra custom modules. |

---

## 📋 Step 4: Configure and Start the Install

Once you finish setting up your preferences, FluxLinux will prepare the Root Shell install.

1. Tap on the **Generate Setup Command** button (preview only).
2. Review the generated script and options.
3. Tap **Install in Root Shell** to start the install inside FluxLinux's Root Shell component.

| Action / State | Screenshot | Description |
| :--- | :---: | :--- |
| **Install in Root Shell** | UI TBD | Review the script and tap **Install in Root Shell** to proceed in-app. |

---

## ⚡ Step 5: Execute the Install in Root Shell

FluxLinux opens an in-app Root Shell session and runs the setup with root privileges (`su`).

1. The Root Shell session uses the same pinned Debian rootfs asset as the PRoot path (no re-download).
2. `setup_debian13_chroot.sh` mounts the native filesystems (idempotently, via the SSOT helper), extracts the rootfs to `/data/local/tmp/chrootDebian13`, and sets up the desktop.
3. When it finishes, FluxLinux marks Debian (Rooted) as installed.

---

## 🎉 Step 6: Verify Installation in Home Screen

Once the installation finishes, return to the FluxLinux app. The home screen will now list your newly installed Debian (Rooted) Chroot environment.

| Action / State | Screenshot | Description |
| :--- | :---: | :--- |
| **Verify Installation** | <img src="img/debian-chroot/step-four-verify-in-home.png" width="500" /> | Your new Debian Chroot installation will be visible on the FluxLinux Home screen. |

---

## 🛑 Step 7: Controlling the Session

You can launch and manage your Debian Chroot session directly from the Home screen.

- **Start Session**: Open Shell (in-app Root Shell → helper `login --user flux`) or GUI mode.
- **Open X11**: Access the graphical display window if it was minimized.
- **Stop Session**: Safely shut down all background Debian processes and unmount native filesystems.

| Action / State | Screenshot | Description |
| :--- | :---: | :--- |
| **Chroot Controls** | <img src="img/debian-chroot/step-five-chroot-controls.png" width="500" /> | Manage the running container session to stop it or reopen the display. |

---

## 💡 Important Tips & Troubleshooting

### ⚡ PRoot vs Chroot Mode
* **PRoot Mode:** Runs entirely in user-space and requires **no root permissions**, simulating root actions through system call interception. Performance is slightly lower.
* **Chroot Mode:** Used in this guide. Requires root permissions. It provides **near-native performance** and full hardware access by directly utilizing the Linux kernel capabilities of your Android device.

### 🛡️ Root Access Issues
* If the installation fails immediately in the Root Shell, verify you granted root to **FluxLinux** (not Termux) in Magisk/KernelSU, and that a root-capable BusyBox is present (Step 1).
* Check your superuser app to ensure FluxLinux has persistent root permissions without a timeout.

### 🏎️ Troubleshooting Hardware Acceleration (GPU)
* Because Chroot runs natively, GPU access is more direct than in PRoot.
* Ensure you select the correct driver (e.g., Turnip + Zink for Adreno, or Panfrost for Mali) that matches your device SoC.
* If your graphical environment fails to start, fallback to **None / Software Rendering** to diagnose the issue.
