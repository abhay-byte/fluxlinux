# FluxLinux Pro v1.3 Release Notes

## 🚨 Google Play Compliance Update

This release marks our debut on F-Droid and addresses Google Play Policy requirements regarding Accessibility Services. The project is now fully Open Source under the GPLv3 license.

### Key Changes
- **New Accessibility Disclosure Flow**: We have implemented a new, policy-compliant flow for enabling the Accessibility Service (used for the Floating Keyboard Button).
  - Users must now explicitly tap "Agree" in a prominent disclosure dialog before being taken to system settings.
  - The dialog clearly explains *why* the service is needed and declares that no personal data is collected.
  - This replaces the previous direct button to ensure users are fully informed.

### Other Improvements
- **GPLv3 License**: Project re-licensed to GPLv3 for full FOSS compliance.
- **Reproducible Builds**: Build configuration updated to support reproducible builds (F-Droid compliance).
- Updated build configuration for better stability.
- Minor UI refinements in the Prerequisites screen.

### ⚠️ Important for Users
If you are updating from a previous version, you may need to re-enable the Accessibility Service permission if prompted.
