# Privacy Policy for FluxLinux Pro

**Last Updated:** January 5, 2026

FluxLinux Pro ("we", "our", or "us") is committed to protecting your privacy. This Privacy Policy explains how our application handling your data.

## 1. Information Collection and Use

**We do not collect, store, or share any personal information about you.**

FluxLinux Pro operates entirely locally on your device. We do not have a backend server, and we do not use any third-party analytics or tracking services (such as Firebase Analytics, Google Analytics, or Crashlytics).

### Personal Data
We do not collect names, email addresses, phone numbers, location data, or any other personally identifiable information.

### Usage Data
We do not track or log how you use the application. All app logs are stored locally on your device for debugging purposes only and are never transmitted to us.

## 2. Permissions and Their Usage

FluxLinux Pro requires specific Android permissions to function correctly. Here is how we use them:

*   **INTERNET (`android.permission.INTERNET`)**: Used solely to download necessary assets (Linux distribution rootfs, installation scripts, graphics) from public repositories (e.g., GitHub) directly to your device *upon your explicit request*. We do not send data to any server.
*   **Run Command (`com.termux.permission.RUN_COMMAND`)**: Required to execute commands in the Termux app to install and launch Linux distributions. This is the core functionality of FluxLinux Pro.
*   **Foreground Service (`android.permission.FOREGROUND_SERVICE`)**: Used to keep the "Floating Keyboard" toggle button active while you are using a Linux environment.
*   **Display Over Apps (`android.permission.SYSTEM_ALERT_WINDOW`)**: Required to display the floating button that allows you to toggle the keyboard while using X11/GUI environments.

## 3. Third-Party Services

FluxLinux Pro interacts with the following third-party services/apps to function:

*   **GitHub**: When you choose to install a distribution or tool, the app downloads files directly from GitHub releases. Please refer to GitHub's privacy policy for data handling regarding downloads.
*   **Termux & Termux:X11**: FluxLinux Pro acts as an orchestrator for these applications. We rely on them to run the Linux environment. Please refer to their respective privacy policies.

## 4. Data Storage

All data created or downloaded by FluxLinux Pro (such as Linux file systems, scripts, and settings) is stored locally on your device. You have full control over this data and can delete it at any time by clearing the app data or uninstalling the application.

## 5. Children's Privacy

FluxLinux Pro is not intended for use by children under the age of 13. We do not knowingly collect personal information from children under 13.

## 6. Changes to This Privacy Policy

We may update our Privacy Policy from time to time. Thus, you are advised to review this page periodically for any changes. We will notify you of any changes by posting the new Privacy Policy on this page.

## 7. Contact Us

If you have any questions or suggestions about our Privacy Policy, do not hesitate to contact us at:

**Email**: abhay02delhi@gmail.com
**GitHub**: [https://github.com/abhay-byte](https://github.com/abhay-byte)
