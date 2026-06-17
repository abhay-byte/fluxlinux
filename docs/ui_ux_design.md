# FluxLinux Pro UI/UX Design Specification

## Overview
FluxLinux Pro is a modern Android app for running Linux distributions via Termux. This document outlines the complete UI/UX design for all screens and user flows.

## Design Principles
- **Glassmorphism**: Translucent cards with blur effects
- **Dark Theme**: Deep purple gradient background
- **Vibrant Accents**: Cyan (#00E5FF) and Magenta (#FF00E6)
- **Progressive Disclosure**: Show only relevant options based on state
- **Clear Feedback**: Toast messages, progress indicators, status badges

---

## 1. Onboarding Flow

### 1.1 Welcome Screen (First Launch)
**Purpose**: Introduce app and set expectations

**Layout**:
- FluxLinux Pro logo with gradient animation
- Tagline: "Run Full Linux Distributions on Android"
- Feature highlights (3 cards):
  - 🐧 Multiple Distros (Debian, Ubuntu, Arch)
  - 🖥️ Full Desktop Environment (XFCE4)
  - ⚡ No Root Required (PRoot mode)
- "Get Started" button (cyan accent)
- "Skip" text button (bottom)

**Flow**: Welcome → Prerequisites Check → Home

---

### 1.2 Prerequisites Check Screen
**Purpose**: Verify Termux and Termux:X11 installation

**Layout**:
- Title: "Prerequisites"
- Subtitle: "FluxLinux Pro requires these apps to function"

**Checklist**:
1. **Termux** 
   - ✅ Installed (v0.118.3) - Green checkmark + version
   - ⬇️ Not Installed - "Install Termux" button
   
2. **Termux:X11**
   - ✅ Installed - Green checkmark + version
   - ⬇️ Not Installed - "Install X11" button

**Actions**:
- Auto-download and install APKs when buttons clicked
- Progress bars during download
- "Continue" button (enabled only when both installed)

**Flow**: Prerequisites → Permission Request → Home

---

### 1.3 Permission Request Screen
**Purpose**: Request RUN_COMMAND permission

**Layout**:
- Icon: 🔐
- Title: "Grant Termux Permission"
- Explanation: "FluxLinux Pro needs permission to communicate with Termux"
- "Grant Permission" button
- "Why is this needed?" expandable info

**Flow**: Permission → Home

---

## 2. Home Screen (Main Interface)

### 2.1 Header Section
- App logo (top-left)
- Settings icon (top-right)

### 2.2 Setup Section (Collapsible)
**Initial State** (Not initialized):
- "Initialize Environment (Setup)" button (cyan, full-width)
- "🎨 Apply Termux Tweaks" button (magenta, full-width)

**Completed State**:
- Hidden by default
- "▼ Show Setup" toggle button
- When expanded, shows grayed-out completed buttons

### 2.3 Prerequisites Status
**Layout**: Horizontal row with 2 status cards

**Termux Card**:
- ✅ Icon + "Termux ✓"
- Version number (small text)

**X11 Card**:
- ✅ Icon + "Termux:X11 ✓"
- Version number (small text)

### 2.4 Available Distros Section
**Title**: "Available Distros"

**Distro Card** (for each distro):

**Not Installed State**:
- Distro icon (gradient box)
- Name + ID
- Description
- "Install" button (full-width, glass border)

**Installed State**:
- Distro icon (gradient box)
- Name + ID
- Description
- Row with 2 buttons:
  - "CLI" (cyan background)
  - "GUI" (magenta background)
- "Uninstall" text button (red, right-aligned)

### 2.5 Footer
- "Fix Connection Issues" text button (magenta)

---

## 3. Settings Screen

### 3.1 General Settings
- **Theme** (future): Light/Dark/Auto
- **Default Launch Mode**: CLI/GUI
- **Auto-update Check**: Toggle

### 3.2 Advanced Settings
- **Installation Mode**:
  - 🔓 PRoot (No Root) - Default
  - 🔐 Chroot (Requires Root) - Badge: "Root Required"
- **GPU Acceleration**: Toggle (VirGL/Zink)
- **Audio**: PulseAudio settings

### 3.3 Storage Management
- **Installed Distros**: List with sizes
- "Clear Cache" button
- "Reset All" button (destructive, confirmation dialog)

### 3.4 About
- App version
- GitHub link
- License info
- "Check for Updates" button

---

## 4. Root Access Screen (Chroot Mode)

### 4.1 Root Detection
**Purpose**: Check if device is rooted

**Layout**:
- Icon: 🔐
- Title: "Root Access Required"
- Status indicator:
  - ✅ "Root Detected" (green)
  - ❌ "No Root Access" (red)

**If Rooted**:
- "Enable Chroot Mode" button
- Benefits list:
  - Better performance
  - Full hardware access
  - Native speed

**If Not Rooted**:
- Explanation of limitations
- "Continue with PRoot" button
- "Learn More" link

---

## 5. Installation Flow

### 5.1 Clipboard-Based Install
**Current Implementation**:
1. User clicks "Install"
2. Command copied to clipboard
3. Toast: "Command Copied! Paste in Termux to Install."
4. Termux app opens automatically
5. User pastes and executes command
6. Auto-return to FluxLinux Pro after completion

**Future Enhancement** (In-App Install):
- Progress dialog with steps:
  - ⏳ Downloading distro...
  - ⏳ Extracting files...
  - ⏳ Configuring system...
  - ✅ Installation complete!

### 5.2 Post-Install Configuration
**Automatic**:
- User creation (username: flux)
- Sudo privileges
- XFCE4 desktop setup
- Marker file creation

---

## 6. Launch Flows

### 6.1 CLI Launch
1. User clicks "CLI" button
2. Termux opens with proot-distro login
3. User sees bash prompt in distro

### 6.2 GUI Launch
1. User clicks "GUI" button
2. Termux:X11 app launches
3. XFCE4 desktop appears
4. PulseAudio connects for audio

---

## 7. Troubleshooting Screen

### 7.1 Connection Issues
**Trigger**: "Fix Connection Issues" button

**Layout**:
- Title: "Connection Issues?"
- Problem: "Can't communicate with Termux"

**Solutions**:
1. **Allow External Apps**
   - Command shown in code block
   - "Copy Command" button
   - Instructions to paste in Termux

2. **Reinstall Termux**
   - "Download Termux" button
   - Link to GitHub releases

3. **Check Permissions**
   - "Grant Permission" button
   - Permission status indicator

### 7.2 Common Issues
**Expandable sections**:
- "GUI not launching" → Check X11 installation
- "No sound" → PulseAudio troubleshooting
- "Slow performance" → PRoot vs Chroot comparison
- "Installation failed" → Log viewer

---

## 8. Distro Management

### 8.1 Distro Details Screen (Future)
**Purpose**: Show detailed info about installed distro

**Layout**:
- Distro name + icon
- Installation date
- Disk usage
- Package count
- Last updated

**Actions**:
- "Launch CLI"
- "Launch GUI"
- "Backup"
- "Restore"
- "Uninstall"

### 8.2 Uninstall Confirmation
**Dialog**:
- Title: "Uninstall Debian?"
- Warning: "This will delete all data"
- Checkbox: "Also remove user data"
- "Cancel" / "Uninstall" buttons

---

## 9. Notifications & Feedback

### 9.1 Toast Messages
- "Downloading Termux..." (with progress)
- "Command Copied! Paste in Termux."
- "Initializing Environment..."
- "Applying Termux Tweaks..."
- "Launching XFCE..."
- "Uninstalling..."

### 9.2 Progress Indicators
- Linear progress bars for downloads
- Circular progress for operations
- Determinate when possible

### 9.3 Status Badges
- ✅ Installed
- ⏳ Installing
- ❌ Failed
- 🔄 Updating

---

## 10. Navigation Structure

```
Home (Main)
├── Settings
│   ├── General
│   ├── Advanced (PRoot/Chroot)
│   ├── Storage
│   └── About
├── Troubleshooting
│   ├── Connection Issues
│   └── Common Problems
└── Distro Details (per distro)
    ├── Info
    ├── Backup/Restore
    └── Uninstall
```

---

## 11. Future Enhancements

### 11.1 Multi-Distro Support
- Install multiple distros simultaneously
- Switch between distros
- Distro comparison table

### 11.2 Backup & Restore
- Export distro as tarball
- Import from backup
- Cloud sync (optional)

### 11.3 Package Manager Integration
- Browse packages
- Install/remove packages from app
- Update all packages button

### 11.4 Desktop Environments
- Choose DE during install:
  - XFCE4 (default)
  - LXQt
  - MATE
  - i3wm

### 11.5 Performance Monitoring
- CPU usage graph
- RAM usage
- Disk I/O
- Network stats

---

## 12. Accessibility

- **High Contrast Mode**: For visually impaired
- **Large Text**: Scalable font sizes
- **Screen Reader**: TalkBack support
- **Keyboard Navigation**: Full keyboard support

---

## 13. Error States

### 13.1 No Internet
- Icon: 📡
- Message: "No internet connection"
- Action: "Retry" button

### 13.2 Termux Not Found
- Icon: ⚠️
- Message: "Termux not installed"
- Action: "Install Termux" button

### 13.3 Installation Failed
- Icon: ❌
- Message: "Installation failed"
- Details: Error log (expandable)
- Actions: "Retry" / "Report Issue"

---

## 14. Animations & Transitions

- **Screen Transitions**: Slide animations (300ms)
- **Button Press**: Scale down (0.95) with haptic feedback
- **Card Expand**: Smooth height animation
- **Progress**: Indeterminate shimmer effect
- **Success**: Checkmark animation with bounce

---

## Color Palette Reference

### Primary Colors
- Background Start: `#0F0C29`
- Background Mid: `#302B63`
- Background End: `#24243E`

### Glass Effects
- Glass High: `#26FFFFFF` (15% white)
- Glass Medium: `#1AFFFFFF` (10% white)
- Glass Low: `#0DFFFFFF` (5% white)
- Glass Border: `#4DFFFFFF` (30% white)

### Accents
- Cyan: `#00E5FF`
- Magenta: `#FF00E6`
- Success Green: `#50fa7b`
- Error Red: `#FF6B6B`

### Text
- Primary: `#FFFFFF`
- Secondary: `#DDDDDD`
- Disabled: `#888888`
