
<div align="center">
  <img src="https://raw.githubusercontent.com/abhay-byte/fluxlinux/main/assets/logo/logo.webp" width="150" />
  <h1>FluxLinux Pro v1.2 - BusyBox & Policy Update</h1>
  <p><strong>Run full Linux desktop environments on your Android device</strong></p>
</div>

---

This release addresses **Google Play Policy Compliance** regarding Accessibility Services and introduces a major feature for **Rooted Users** to simplify Chroot environment setup. We have also refined the UI permissions flow for a smoother onboarding experience.

## 🚀 What's New in v1.2

| Category | Features |
|:---:|---|
| **Compliance** | • **Accessibility Disclosure**: Added prominent, compliant disclosure for Accessibility Service usage (Step 8)<br>• **Privacy**: Explicitly declared no data collection for navigation services |
| **Root Support** | • **New BusyBox Step**: Automatically detects root and guides users to install the required BusyBox NDK module<br>• **Direct Download**: One-click download for `osm0sis` BusyBox module<br>• **Smart Skip**: Non-rooted users can safely skip this step |
| **UI / UX** | • **Visual Refinements**: Updated permission cards to use `secondary` theme colors for better visibility<br>• **Bug Fixes**: Corrected duplicate step numbering in the setup wizard (Steps 6-11) |

## 📸 Visual Updates

<div align="center">
  <table>
    <tr>
      <td align="center"><img src="https://raw.githubusercontent.com/abhay-byte/fluxlinux/main/docs/releases/v1.2/accessibility.png" width="250" /><br/><b>Accessibility Disclosure</b></td>
      <td align="center"><img src="https://raw.githubusercontent.com/abhay-byte/fluxlinux/main/docs/releases/v1.2/busybox.png" width="250" /><br/><b>BusyBox Installation</b></td>
    </tr>
  </table>
</div>

## 🛠️ Key Changes
- **Version**: Bumped to v1.2 (Code: 3)
- **New Feature**: BusyBox Installation Step (Root Only logic)
- **Policy**: Added `AccessibilityService` usage disclosure
- **Refactor**: Renumbered `PrerequisitesScreen` steps for consistency

## 📦 Installation
1. Uninstall any previous version if you have signature conflicts (otherwise update).
2. Download and install `app-release.apk`.
3. Follow the setup wizard; rooted users should see the new BusyBox step.

## 📝 Notes
- **BusyBox**: If you are rooted, installing the NDK module is highly recommended for full Linux compatibility.
- **Accessibility**: The service is used *only* for global Back/Home/Recents actions from the floating menu.
