# Plan: Complete Onboarding UI/UX Redesign & Unification

**Date:** 2026-08-19  
**Status:** **IN DESIGN & IMPLEMENTATION**  
**Target:** `com.ivarna.fluxlinux.ui.onboarding.OnboardingFlowScreen.kt`, `InstallProgressPanel.kt`, and related UI components  
**Goal:** Redesign all onboarding pages with a unified Material 3 + Glassmorphism design system, responsive scaling, consistent button & typography hierarchy, step indicator, and zero changes to underlying business/install logic.

---

## 1. Problem Statement & Audit Findings

### 1.1 Welcome Page (`OnboardStep.Welcome`)
- **Scaling Issues**: Fixed Spacers and unconstrained image loading cause layout overflow and forced nested scrolling on smaller screens or landscape mode.
- **Visual Balance**: Feature cells use raw 2-column chunking leaving awkward single items hanging.
- **Hierarchy**: Lacks a modern hero section with the app logo and clear visual identity.

### 1.2 Consent Page (`OnboardStep.Consent`)
- **UI Structure**: Plain text with an unstyled standard checkbox and large loose spacers.
- **Notice Clarity**: Needs a structured F-Droid compliance info card explaining the GitHub download requirement.

### 1.3 Host Setup Page (`OnboardStep.HostSetup`)
- **Styling Inconsistency**: Relies on legacy `PrerequisitesScreen.kt` composables with mismatched dark-on-dark buttons, hardcoded "Step 1" headers, and redundant buttons.
- **UX Flow**: Dual action buttons (one inside the child component, one at the page bottom) cause confusion.

### 1.4 Distro Selection Page (`OnboardStep.DistroPick`)
- **Distro Cards**: Radio items lack modern glass styling, badges (architecture, root requirements, default tags), and visual polish.
- **Root Detection**: Plain red text warning when root is missing instead of a structured info/retry card.
- **Button Inconsistencies**: Non-standard button heights (48.dp vs 52/56.dp).

### 1.5 Options Page (`OnboardStep.Options`)
- **Theme Selection**: Basic radio rows instead of modern visual theme cards (Dark vs Light).
- **Manifest Card**: Plain text bullet list instead of structured component badges.
- **Duplicate Consent**: Plain checkbox lacking cohesive card framing.

### 1.6 Running / Progress Page (`InstallProgressPanel`)
- **Terminal Console**: Raw `#0A0A0C` background without consistent glass framing or smooth scroll animation.
- **Progress Visualization**: Needs polished progress bars, phase badges, and active pulse animations.

### 1.7 Done Page (`OnboardStep.Done`)
- **Action Button Hierarchy**: Three stacked buttons with three completely different color schemes (Cream, Magenta, Grey) causing visual noise.
- **Layout & Overflow**: Lacks vertical scroll safety on compact displays.

---

## 2. Design System & UI Specifications

### 2.1 Color Palette & Theme Tokens
- **Primary CTA**: `BrandCream` (`#F5E6CA`) container with `FluxDarkGrey` (`#1A1C1E`) bold text.
- **Secondary CTA / Badges**: `FluxAccentMagenta` (`#FF00E6`) / `FluxAccentCyan` (`#00E5FF`).
- **Glass Surfaces**: `MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)` with `FluxHairline` / `outlineVariant.copy(alpha = 0.40f)` border and `RoundedCornerShape(16.dp)`.
- **Typography**: Clear hierarchy using Material 3 `titleLarge` (22–26sp bold), `titleMedium` (16–18sp semi-bold), `bodyMedium` (14sp), `bodySmall` (12sp `fluxMutedText()`).

### 2.2 Reusable Components
1. **`OnboardingHeader` & `StepProgressBar`**:
   - Modern step indicator bar / dots showing progress across steps (Welcome → Consent → Host → Distro → Options → Progress → Done).
2. **`FluxPrimaryButton` & `FluxSecondaryButton`**:
   - Standard 52.dp height, `RoundedCornerShape(14.dp)`, unified ripple, font weight, and disabled states.
3. **`FluxGlassCard`**:
   - Standard container with rounded corners, subtle glass border, and elevation.
4. **`FluxConsentCheckboxCard`**:
   - Polished glass checkbox row with accent tint and clear label.
5. **`ThemeSelectionCard`**:
   - Visual card with theme swatch (Dark / Light) and checkmark indicator.

---

## 3. Detailed Page Redesigns

### Page 1: Welcome
- **Hero**: Glowing app logo icon badge + "FluxLinux" title + tagline.
- **Feature Cards**: 3 structured cards in a responsive vertical or 3-column layout with glass cards and magenta/cyan icon accents.
- **Hero Graphic**: Flexible height-constrained illustration with smooth gradient fade.
- **Bottom Bar**: Primary "Get Started" button.

### Page 2: Consent (F-Droid / GitHub Disclosure)
- **Header**: Icon badge (GitHub / Download) + "External Image Downloads".
- **Info Card**: Glass notice explaining that Linux host userland and distro rootfs images are downloaded from GitHub Releases.
- **Checkbox Card**: Interactive `FluxConsentCheckboxCard`.
- **Navigation Bar**: Back (Ghost) + Continue (Primary, gated on consent).

### Page 3: Host Environment Setup
- **Header**: "Host Environment".
- **Host Card**: In-page dedicated card displaying:
  - Host prefix status ("Ready" / "Needs Setup" / "Extracting…").
  - Animated linear progress bar during extraction.
  - Phase status details ("Extracting bootstrap.tar…", "Initializing PulseAudio…").
  - "Initialize Host" button if not done.
- **Navigation Bar**: Back (Ghost) + Continue (Primary, enabled when host is ready).

### Page 4: Distro Selection
- **Header & Tabs**: PRoot vs Chroot toggle tabs with root probe status.
- **Root Status Banner**: Glass warning banner if root is missing for Chroot with superuser request action.
- **Distro Cards**: Glass cards with distro logo, title, description, architecture chip, and selection indicator.
- **Coming Soon Section**: Compact subtle grid for upcoming distros.
- **Navigation Bar**: Back + Continue.

### Page 5: Installation Options
- **Header**: "Desktop Configuration".
- **Theme Picker**: Visual Dark / Light preview tiles.
- **Package Manifest**: Grid of included items (Rootfs, XFCE4, Hardware Acceleration, Flux Glass Theme, Terminal + X11 Display).
- **Download Checkbox Card**: Clean confirmation checkbox.
- **Navigation Bar**: Back + Install.

### Page 6: Installation Progress (`InstallProgressPanel`)
- **Status Hero**: Percentage circle / animated bar with glowing accent.
- **Phase Label & Detail**: Clear real-time status text.
- **Console Log**: Polished dark glass console with monospace text, syntax-colored highlights, and auto-scroll.
- **Error Handling**: Red warning card with "Retry" and "Back" actions if a step fails.

### Page 7: Completion & Launch
- **Celebration Hero**: Animated success check badge + distro icon.
- **Summary**: "Your [Distro] Linux desktop is ready."
- **Action Hierarchy**:
  1. **Start Desktop** (Primary button with Desktop icon → launches XFCE).
  2. **Open Terminal** (Secondary accent button with Terminal icon → opens shell).
  3. **Go to Dashboard** (Outlined button → navigates to Home).

---

## 4. Preservation of Core Logic & Safety Guarantees
- **Zero changes to**:
  - `OnboardingInstallRunner` and `HostBootstrap` execution.
  - `RootShell.probeRootAvailable` checks and caching.
  - F-Droid download consent gating.
  - StateManager flags (`setOnboardingComplete`, `setDistroInstalled`).
  - Navigation callbacks (`onFinished`, `onOpenTerminal`, `onStartDesktop`).
  - Process cancellation and lifecycle disposal.

---

## 5. Verification & Review Agent Plan
1. Launch a dedicated review subagent to inspect the Kotlin code before and after modifications.
2. Verify all parameter signatures, lifecycle events, coroutine scopes, and button callbacks are 100% matched.
3. Validate compilation via Gradle build task.
