
<div align="center">
  <img src="https://raw.githubusercontent.com/abhay-byte/fluxlinux/main/assets/logo/logo.webp" width="150" />
  <h1>FluxLinux Pro v1.1 - Policy Compliance Update</h1>
  <p><strong>Run full Linux desktop environments on your Android device</strong></p>
</div>

---

This release focuses on **Google Play Policy Compliance** and stability improvements. We have refactored the application to better handle package installations and permissions, ensuring a safer and more transparent experience for all users.

## 🚀 What's New in v1.1

| Category | Features |
|:---:|---|
| **Compliance** | • **Removed restricted permissions** (`REQUEST_INSTALL_PACKAGES`, `QUERY_ALL_PACKAGES`, `PACKAGE_USAGE_STATS`)<br>• Deleted automatic APK installer mechanism<br>• Dependencies (Termux) are now downloaded via browser for transparency |
| **Privacy** | • Added comprehensive **Privacy Policy**<br>• Removed all analytics and tracking code<br>• Confirmed zero personal data collection |
| **UI / UX** | • **Step 5 & 6 Layout Fixes**: Resolved button overlap issues on smaller screens (Process Killer Fix & System Check)<br>• **Visual Updates**: Updated primary/secondary button colors for better readability<br>• **Version Info**: Updated settings screen with accurate version tracking |
| **Stability** | • Fixed syntax errors in state management<br>• Improved Termux connection troubleshooting<br>• Updated dependency links to latest stable releases |

## 🛠️ Key Changes
- **Target SDK**: Updated to Android 16 (API 36)
- **Dependencies**: Bumps `androidx.core:core-ktx` and Compose BOM
- **Removed Features**: In-app APK installer (replaced with deep links)

## 📦 Installation
1. Uninstall any previous version of FluxLinux Pro (due to signature change).
2. Download and install `app-release.apk`.
3. Follow the expanded **Prerequisites** wizard to setup Termux.

## 📝 Notes
- Ensure you have **Termux** and **Termux:X11** installed. The app will guide you to their download pages.
- For Android 12+ users, follow the "Process Killer Fix" steps carefully.
