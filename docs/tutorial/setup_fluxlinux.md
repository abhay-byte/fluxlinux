<div align="center">
  <img src="../../assets/logo/logo.webp" width="160" alt="FluxLinux Pro Logo" />
  <h1>🚀 Setting up FluxLinux Pro</h1>
  <p>Welcome to the FluxLinux Pro installation guide! This step-by-step tutorial will walk you through the process of getting FluxLinux Pro up and running on your Android device.</p>
</div>

---

## 📖 Table of Contents

1. [📥 Step 1: Choose Your Download Method](#-step-1-choose-your-download-method)
2. [⚙️ Step 2: Install Required Components (Termux & Termux:X11)](#️-step-2-install-required-components-termux--termuxx11)
3. [🔧 Step 3: Configure Termux](#-step-3-configure-termux)
4. [🔐 Step 4: Grant Required Permissions](#-step-4-grant-required-permissions)
5. [🪟 Step 5: Overlay Permission (Draw Over Other Apps)](#-step-5-overlay-permission-draw-over-other-apps)
6. [👻 Step 6: Disable Phantom Process Killer (Crucial)](#-step-6-disable-phantom-process-killer-crucial)
7. [🧰 Step 7: Install BusyBox (Optional / Rooted Users Only)](#-step-7-install-busybox-optional--rooted-users-only)
8. [📦 Step 8: Environment Setup](#-step-8-environment-setup)
9. [💻 Step 9: System Requirements Check](#-step-9-system-requirements-check)
10. [🎉 Step 10: Launch FluxLinux Pro!](#-step-10-youre-ready)

---

## 📥 Step 1: Choose Your Download Method

FluxLinux Pro is available through three official channels. Choose the one that best suits your needs:

| Method | Price | Updates | Best For |
| :--- | :--- | :--- | :--- |
| **GitHub Releases** | 🟢 Free | ⚡ Direct / Manual | Developers & power users wanting the absolute latest build |
| **F-Droid** | 🟢 Free | 🔄 Automatic (F-Droid client) | Users who prefer free, open-source app stores |
| **Google Play Store** | 🟡 Paid | 🔄 Automatic | Users wanting easy installations while supporting the developer |

---

### Option 1: GitHub Releases (Recommended for Developers)

Download the latest pre-compiled stable/beta release directly from our official repository.

[![GitHub](https://img.shields.io/badge/GitHub-Releases-blue?logo=github&style=for-the-badge)](https://github.com/abhay-byte/fluxlinux/releases)

> [!TIP]
> Always download the file ending with `.apk` (e.g., `app-release.apk`). Avoid files labeled with `idsig` unless you specifically require signature verification.

| Action / State | Screenshot | Description |
| :--- | :---: | :--- |
| **GitHub Release Download** | <img src="img/release-download.png" width="500" /> | Select the latest stable `.apk` package file from the Assets section of the GitHub Release page to download it. |

---

### Option 2: F-Droid (Free & Open Source)

Get FluxLinux Pro from the decentralized Android software repository. You can download the standalone APK or install it via the F-Droid client.

[![F-Droid](https://img.shields.io/badge/F--Droid-Download-green?logo=fdroid&style=for-the-badge)](https://f-droid.org/packages/com.ivarna.fluxlinux)

| Action / State | Screenshot | Description |
| :--- | :---: | :--- |
| **F-Droid Download Page** | <img src="img/fdroid-download.png" width="500" /> | On the F-Droid app or website, find the FluxLinux Pro repository listing and click the version download or install button. |

---

### Option 3: Google Play Store (Support the Project)

If you would like to support the ongoing development of FluxLinux Pro, you can purchase it directly from the Google Play Store. This provides automatic background updates.

[![Google Play](https://img.shields.io/badge/Google_Play-Install-blue?logo=google-play&style=for-the-badge)](https://play.google.com/store/apps/details?id=com.zenithblue.fluxlinux)

| Action / State | Screenshot | Description |
| :--- | :---: | :--- |
| **Google Play Install** | <img src="img/playstore-download.png" width="500" /> | Open the listing on Google Play and click **Install** to download and configure FluxLinux Pro with automatic updates. |

---

> [!IMPORTANT]
> **Unknown Sources Permission:** If you are downloading from GitHub or F-Droid for the first time, your browser/client will prompt you to allow installation from "Unknown Sources". Please enable this permission in your Android system settings to proceed.

| Action / State | Screenshot | Description |
| :--- | :---: | :--- |
| **Install FluxLinux Pro Popup** | <img src="img/install-fluxlinux-popup.png" width="500" /> | Tap **Install** on the browser prompt. If prompted, grant the system permission to install apps from unknown sources. |

---

## ⚙️ Step 2: Install Required Components (Termux & Termux:X11)

Once you open FluxLinux Pro, the application requires **Termux** and **Termux:X11** to run. The setup behavior differs depending on your installation source:

### 1. GitHub Releases & F-Droid (Automatic Download)
The app will automatically download the correct versions of Termux and Termux:X11 and prompt you to install them.
* Grant the necessary permission to install packages from FluxLinux Pro if prompted.

### 2. Google Play Store (Manual Download)
Due to Play Store policy restrictions, the app cannot automatically download external APK files. You must download and install them manually:
* **Termux:** Download and install the version from F-Droid (do not use the outdated Play Store version of Termux).
* **Termux:X11:** Download and install the companion APK.
* Direct download links are provided in the app.

### 🔍 Verification Check
FluxLinux Pro will check if both components are installed on your device. Once both dependencies are detected, you can click **Proceed** to continue with the setup.

| Action / State | Screenshot | Description |
| :--- | :---: | :--- |
| **Download Required Components** | <img src="img/termux-download-page-flux.png" width="500" /> | **Download Prompts:** If Termux or Termux:X11 is not yet installed on your system, the app will provide download prompts. You must install both packages to continue. |
| **Verification Check Done** | <img src="img/step-one-termux.png" width="500" /> | **Verification Check:** Once both components are successfully installed and active on your system, the app displays checkmarks for both. Tap **Continue** to proceed. |

---

## 🔧 Step 3: Configure Termux

FluxLinux Pro needs to communicate with Termux to execute background processes and set up your Linux environment. You must enable external apps in Termux.

1. Click the **Copy & Open Termux** button in the FluxLinux Pro app.
2. The app will copy a command to your clipboard and open Termux.
3. Paste the command into the Termux terminal and hit Enter:
   ```bash
   mkdir -p ~/.termux && echo "allow-external-apps = true" >> ~/.termux/termux.properties && termux-reload-settings
   ```
4. Return to FluxLinux Pro, check the box confirming you ran the command, and click **Continue**.

| Action / State | Screenshot | Description |
| :--- | :---: | :--- |
| **Configure Termux Screen** | <img src="img/step-two-communication.png" width="500" /> | The initial step in the app prompts you to configure Termux communication. Use the **Copy & Open Termux** button to copy the setup script. |
| **Paste Command in Termux** | <img src="img/step-two-termux-paste-and-enter.png" width="500" /> | Paste the clipboard command in the Termux terminal and press enter to create files and reload Termux settings. |
| **Check Box & Continue** | <img src="img/step-two-click-toggle-in-app-i-have-pasted.png" width="500" /> | Check the confirmation box **"I've pasted and run the command in Termux"** and click **Continue**. |

---

## 🔐 Step 4: Grant Required Permissions

FluxLinux Pro requires a few basic Android permissions to function properly.

* **Communication Permission:** Allows FluxLinux Pro to send commands to Termux securely.
* **Storage Permission:** Allows the Linux environment to access your internal storage for managing files.
* **Notification Permission:** Keeps the environment running in the background.

Click **Grant Permission** and accept the prompts that appear on your screen.

| Action / State | Screenshot | Description |
| :--- | :---: | :--- |
| **Grant Permission Screen** | <img src="img/step-three-grant-permission.png" width="500" /> | The app will display a padlock icon requesting permission to communicate securely with Termux. Click **Grant Permission**. |
| **Android Authorization Dialog** | <img src="img/step-three-allow-excecute command.png" width="500" /> | Allow the command execution permission in the Android system pop-up to permit FluxLinux Pro to interact with the Termux shell. |

---

## 🪟 Step 5: Overlay Permission (Draw Over Other Apps)

For the best experience, FluxLinux Pro uses a floating widget/menu over the Linux desktop environment (Termux:X11). This allows you to quickly access controls, keyboards, and settings.

1. Click the **Open Settings** button.
2. Find **FluxLinux Pro** in the list of apps.
3. Toggle the switch to **Allow display over other apps**.
4. Press back to return to FluxLinux Pro.

> [!NOTE]
> **Android Restricted Settings Bypass:** 
> If Android blocks you from enabling the overlay permission, follow the steps below:
> 1. Open your device **Settings** and go to **Apps > All Apps**.
> 2. Find and click on **Termux**.
> 3. Tap the **three vertical dots** in the top right corner.
> 4. Tap **Allow restricted settings** and authenticate.
> 5. Return to the overlay settings page and enable the toggle for **Termux**.

| Action / State | Screenshot | Description |
| :--- | :---: | :--- |
| **Overlay Settings Page** | <img src="img/step-four-display-overlay.png" width="500" /> | The app will display a critical permission screen. Click **Enable Overlay** to open the system settings. |
| **Restricted Settings Bypass (Step 1)** | <img src="img/step-four-go-to-app-info-via-apps-settings.png" width="500" /> | If the overlay permission is blocked (greyed out), open the system **Settings > Apps > Termux** App Info page. |
| **Restricted Settings Bypass (Step 2)** | <img src="img/step-four-allow-restricted-settings-click-on-three-dots-and-allow-it.png" width="500" /> | Tap the three dots menu at the top right of the App Info screen and select **Allow restricted settings**. |
| **Blocked Overlay Toggle** | <img src="img/step-four-display-overlay-for-termux-disabled.png" width="500" /> | If the toggle is greyed out, this is the visual indicator that restricted settings are active. Enable permission via the 3-dots menu as shown above. |
| **Enabling Termux Overlay** | <img src="img/step-four-then-allow-display-over-apps-for-termux-will-be-allowed-now.png" width="500" /> | Go back to the **Display over other apps** list, choose **Termux**, and toggle the switch on. |
| **Permission Granted** | <img src="img/step-four-granted-permission.png" width="500" /> | Return to FluxLinux Pro, where you will see the **Permission Granted ✔** status. Click **Next** to proceed. |

---

## 👻 Step 6: Disable Phantom Process Killer (Crucial)

Android 12 and above introduced an aggressive "Phantom Process Killer" that terminates background processes using too much CPU. Since running a full Linux environment requires sustained resources, this feature *must* be disabled, or your Linux environment will crash unexpectedly.

### Without Root (Using ADB/PC)

If your device is not rooted, you will need a computer (Windows, Mac, or Linux) with ADB installed.

1. Enable **Developer Options** by going to **Settings > About Phone** and tapping the **Build Number** 5 times.
2. In developer options, enable **USB Debugging**.
3. Connect your phone to your PC via USB.
4. Open a command prompt/terminal on your PC and run the following three commands:
   ```bash
   adb shell "/system/bin/device_config set_sync_disabled_for_tests persistent"
   adb shell "/system/bin/device_config put activity_manager max_phantom_processes 2147483647"
   adb shell "settings put global settings_enable_monitor_phantom_procs false"
   ```
5. The FluxLinux Pro app will verify if the command was successful.

### With Root (Automatic)

If your device is rooted (Magisk/KernelSU/APatch), simply click **Apply Fix via Root** and grant Superuser permission when prompted. FluxLinux Pro will handle it automatically.

| Action / State | Screenshot | Description |
| :--- | :---: | :--- |
| **Process Killer Fix Screen** | <img src="img/step-five-process-killer-fix.png" width="500" /> | If root access is not detected, you will see a warning screen with an option to run the fix manually via ADB on your PC. |
| **Enable Developer Mode** | <img src="img/step-five-enable-developer-mode-for-adb-click-on-build-number-for-five-times.png" width="500" /> | On your phone, go to **Settings > About Phone** and click on the **Build number** 5 times to enable Developer Options. |
| **Enable USB Debugging** | <img src="img/step-five-in-developer-option-enable-usb-debugging.png" width="500" /> | Go to **Settings > System > Developer Options** and enable the toggle for **USB debugging**. |
| **Copy ADB Command** | <img src="img/step-five-run-these-commands-through-adb-from-ur-pc.png" width="500" /> | Copy the three configuration commands shown in the app to paste them on your computer. |
| **Run ADB on PC Terminal** | <img src="img/step-five-then-copy-and-paste-commands-on-pc-terminal-with-phone-connected-with-usb-debugging-enabled.png" width="500" /> | Connect your phone to your PC, open a command terminal, and execute all three commands to disable the phantom process killer. |

---

## 🧰 Step 7: Install BusyBox (Optional / Rooted Users Only)

For non-root users, BusyBox is not required on this screen, and you can skip this step or the app will pass it automatically.

If you are using a rooted device, you can download and install the BusyBox module directly inside your root manager (e.g. Magisk, KernelSU, or APatch):

1. Click **Download Module** on the BusyBox Installation screen.
2. Your browser will open the download for the BusyBox NDK installer file.
3. Flash the downloaded file inside your root manager's Module page.

| Action / State | Screenshot | Description |
| :--- | :---: | :--- |
| **BusyBox Installation Screen** | <img src="img/step-six-for-root-install-busybox.png" width="500" /> | For rooted devices, this page prompts you to install BusyBox NDK. Non-root users can skip this. Click **Download Module**. |
| **Download Module** | <img src="img/step-six-download-busybox-module.png" width="500" /> | Your browser will download the BusyBox module package (`UPDATE-Busybox.Installer.zip`) to your phone's storage. |
| **Flash in Root Manager** | <img src="img/step-six-then-flash-module-in-your-root-application.png" width="500" /> | Open your root manager application (e.g. APatch, KernelSU, or Magisk), go to the Modules tab, select the downloaded file, and flash it. |

---

## 📦 Step 8: Environment Setup

FluxLinux Pro will now extract and configure the core Linux file system (rootfs) on your device.

* This process may take a few minutes depending on your device's storage speed.
* Please **do not close the app** or turn off the screen during this process.

| Action / State | Screenshot | Description |
| :--- | :---: | :--- |
| **Environment Setup Screen** | <img src="img/step-seven-step-up-environment-click-on-initialize-environemnt-open-in.png" width="500" /> | Tap **1. Initialize Environment (Required)** to trigger the extraction of the Linux rootfs. |
| **Termux Script Execution** | <img src="img/step-seven-termux-will-open-and-start-the-setup.png" width="500" /> | The app will launch Termux to run the automated setup script. Wait for all packages to install. |
| **Environment Initialized** | <img src="img/step-seven-termux-initialized.png" width="500" /> | When Termux setup completes, return to FluxLinux Pro. You will see **Environment Initialized ✔**. Click **Next**. |

---

## 💻 Step 9: System Requirements Check

Before launching the environment, FluxLinux Pro will run a quick hardware check to ensure your device meets the minimum requirements for a smooth experience.

* It will verify your **RAM (Memory)** and **Storage Space**.
* We highly recommend creating a **SWAP file** (Virtual RAM) using a third-party app if your device has less than 8GB of RAM. This prevents out-of-memory crashes when running heavy Linux desktop applications.

Click **Continue to Final Step** once you have reviewed your system status.

| Action / State | Screenshot | Description |
| :--- | :---: | :--- |
| **System Check Results** | <img src="img/step-eight-enable-good-amount-of-swap.png" width="500" /> | The app checks system memory. If you have less than 8GB of RAM, we recommend configuring a SWAP file to avoid out-of-memory crashes. |

---

## 🎉 Step 10: You're Ready!

Congratulations! You have successfully configured FluxLinux Pro.

Click **Launch FluxLinux Pro** to start your environment. The app will initialize the Termux backend and automatically open the Termux:X11 display, presenting you with a full Linux desktop on your Android device.

Enjoy your new portable workstation!

> [!IMPORTANT]
> Always make sure that Termux remains running in the background or in a split-screen layout when using FluxLinux Pro to prevent Android from killing the environment processes.

| Action / State | Screenshot | Description |
| :--- | :---: | :--- |
| **Background Execution Note** | <img src="img/step-last-always-have-termux-in-background-or-in-split.png" width="500" /> | An important notice reminding you that Termux must be kept running in the background. Tap **Complete Setup** to launch your desktop! |

---

## ⏭️ Next Steps

Now that you have configured the core FluxLinux Pro application environment, proceed to our step-by-step guide to install your first Linux distribution:

👉 [**Debian PRoot Setup & Configuration Guide**](setup_debian_proot.md)

