# Plan: Home display-session card, Distros A–Z, X11 chrome, contrast, keyboard leak, Distros FPS

**Date:** 2026-08-16 (updated same day after local implementation review + user follow-ups)  
**Status:** IMPLEMENTATION IN PROGRESS — first eight items landed in the working tree; local code review **REVISE** (8 bugs, 2 suggestions, 3 nits). Four new user follow-ups (items 9–12) are now in contract. Working tree is **not** done.  
**Scope:** Original eight UI / behavior fixes **plus** Home installed A–Z, X11 settings CTA contrast, X11 chrome Back/Keyboard reliability, and system Back → Home then exit. Still no new distros, no rootfs, no script changes, no KDE rewrite, no onboarding flow rewrite except the shared theme-picker contrast tokens.  
**Reviews:**  
1. Plan review (I-1–I-14) — incorporated before implementation.  
2. Local implementation review (`/tmp/grok-1000/grok-review-a6ec1bfc.md`) — 8 bugs. Incorporated as **§22–§27** and **I-15–I-27**.

**How to use this file:** this is the implementation contract. PRs 1–7 are already in the working tree. **Remaining work is PR 8** (§18 / §27). Device-verify on Ivarna release (`:app:assembleIvarnaRelease` + `adb install -r`). Do not uninstall the existing APK.

**References:**  
[`docs/ui_design.md`](../ui_design.md), [`docs/ui_ux_design.md`](../ui_ux_design.md), [`app/src/main/kotlin/com/ivarna/fluxlinux/ui/theme/Theme.kt`](../../app/src/main/kotlin/com/ivarna/fluxlinux/ui/theme/Theme.kt), [`DesktopLauncher.kt`](../../app/src/main/kotlin/com/ivarna/fluxlinux/core/desktop/DesktopLauncher.kt), [`HomeScreen.kt`](../../app/src/main/kotlin/com/ivarna/fluxlinux/ui/screens/HomeScreen.kt), [`DistroScreen.kt`](../../app/src/main/kotlin/com/ivarna/fluxlinux/ui/screens/DistroScreen.kt), [`TerminalScreen.kt`](../../app/src/main/kotlin/com/ivarna/fluxlinux/ui/screens/TerminalScreen.kt), [`termux-x11/src/main/res/layout/main_activity.xml`](../../termux-x11/src/main/res/layout/main_activity.xml).

---

## 0. Product decisions

| Decision | Choice | Why |
|----------|--------|-----|
| Exclusive display | **One** X11 / desktop session at a time (XFCE **or** KDE, any distro) | Embedded X is `:0`. A second `start_gui` would clobber the socket / FGS. User: “without closing it another server cannot be started.” |
| Home banner | New **session card at the top of Home**, above “Installed Distros” | Per-card `RUNNING` chip is easy to miss; user asked for a dedicated top card. |
| Banner vs per-card | Keep existing DistroCard Open/Stop/Logs **and** add the banner | Banner is the “someone is using :0” warning; cards stay the per-distro controls. |
| Start gate | Block in **DesktopLauncher.start**, KDE launch paths, **and** Home launch dialog | UI-only disable is not enough — FGS / onboarding `onStartDesktop` can still start a second session. |
| Top-bar X11 | New **monitor** icon on `TopBar` (Home + Distros tabs). Hidden on Terminal tab (top bar already hidden). | User: “add a button at top bar to visit the x11 display.” |
| Top-bar X11 when idle | Button **always visible**. Idle tap opens the X11 stub (new mascot art). Running tap calls `DesktopLauncher.reopenDisplay`. | Visiting the display includes the disconnected stub. A green/cream badge shows when a session is live. |
| Distros sort | Case-insensitive **name** A–Z. Coming-soon **after** installable. Within each group, A–Z. | `openSUSE` starts with lowercase `o` and currently sorts after `Void`. User asked alphabetical. |
| Coming-soon placement | Still last | A “SOON” Adélie card must not sit above installable Alpine. |
| Home installed order | **IN SCOPE (follow-up).** Same `sortForDistroPage` after the FS filter / method split. | User follow-up: “installed distro page, not sorted alphabetically.” Original “Distros page only” decision is reversed. |
| X11 stub art | New **square** AI image: Flux penguin mascot + **dark idle monitor** (not a running desktop). Replace stub `x11_image` only. Keep `ic_x11_icon.xml` as notification. | User: “fluxlinux icon mascot + x11 display only a monitor not running … square image.” |
| X11 chrome | Dark restyle **and** reliable Back / Keyboard (Java **is** allowed for tap-vs-drag, IME show, and return-to-Home). Restore Preferences / Help / Exit on the idle stub — those were deleted by mistake. | Original “UI only” is superseded: user reports Back/Keyboard do not work consistently. |
| X11 settings CTAs | Redo “Open full X11 preferences” + “Re-apply to running display”. Cream fill must use **dark** text (`onSecondary`). Do not use `primary` as a filled chip. | User follow-up Image #1: cream-on-cream Re-apply; near-black Open-preferences. |
| App Back | First system/gesture Back from any in-app screen or from X11 lands on **Home**. Only a subsequent Back **from Home** exits. X11 must not stay under Home on the back stack. | User: “when back button is clicked should come to home page only then exit.” |
| Keyboard leak | Hide **IME** when leaving Terminal. ExtraKeys already leave composition. | `SHOW_FORCED` + no `hideSoftInput` on dispose. |
| Distros FPS | `LazyColumn` + drop `IntrinsicSize.Min` + stop using the full scroll list as a Haze source | Root cause is composition + measure + blur, not “too many distros.” |
| Contrast | Shared **Flux switch / setting-card / theme-pick** tokens. Do not change `primary = FluxDarkGrey`. | Primary-as-dark is a known TextButton trap (`Theme.kt` comment). Switches/sliders must not use `primary`. |
| Theme picker reuse | One `InstallThemePickRow` restyle covers Install wizard **and** onboarding Options page | Same composable, two call sites. |
| KDE | Same exclusive-session rules as XFCE. Banner copy uses `StateManager.getGuiRunningType`. | User said XFCE but “a display is already running” includes KDE. |
| Tests | JVM unit tests for sort, exclusive-start, contrast tokens. No UI screenshot CI. | Match existing `app/src/test` style. |

---

## 1. Current state (inspected 2026-08-16)

### 1.1 Home — no exclusive-session banner

`HomeScreen` (`app/src/main/kotlin/com/ivarna/fluxlinux/ui/screens/HomeScreen.kt`):

- Collects `DesktopLauncher.uiState`.
- Installed list from `TerminalLauncher.isDistroInstalledOnFs`.
- Per card: `isGuiRunning = StateManager.isGuiRunning(ctx, id) \|\| (desktopForThis && phase == Running)`.
- Per card already has Open / Stop / Logs when that card’s GUI is up (`GlassCard.kt` ~225–257).
- **No** top-of-list card.
- Launch dialog still offers “Launch XFCE4” / “Launch KDE” even when another distro already owns `:0`.
- `DesktopLauncher.start()` **does not** refuse a second start. `continueStart` does `guiShellJob?.cancel()` and starts a new stream (line ~141). That is the exclusive-session hole.

KDE is a second path: `TermuxIntentFactory.buildLaunchKdeGui*` + `StateManager.setGuiRunning` + `EmbeddedX11.launchDisplay`. It does not go through `DesktopLauncher.start`, so a KDE session and an XFCE session can overlap.

`StateManager.getDistrosWithGuiRunning(context)` already exists (prefs scan) and is unused by Home.

### 1.2 Distros page sort — almost A–Z, case-sensitive

`DistroScreen.kt` ~88–90:

```kotlin
val availableDistros = DistroRepository.supportedDistros.filter {
    !installedDistroIds.contains(it.id)
}.sortedWith(compareBy<Distro> { it.comingSoon }.thenBy { it.name })
```

Problems:

1. `thenBy { it.name }` is **case-sensitive**. Catalog name `"openSUSE"` sorts after `"Void"` (`'o' > 'V'`).
2. Catalog order in `DistroRepository.supportedDistros` is Debian-first, not A–Z. The screen sort is the only order the user sees — it must be locale-insensitive and case-insensitive.
3. Chroot tab filters `chrootSupported`; PRoot tab filters `prootSupported`. Sort must run **after** the method filter (or before — same result) so each tab is independently A–Z.
4. No unit test asserts Distros-page order.

Installable cards today (24): Alpine, Alpine (Rooted), Arch, Arch (Rooted), Chimera, Chimera (Rooted), Debian, Debian (Rooted), Deepin, Deepin (Rooted), Fedora, Fedora (Rooted), Kali, Kali (Rooted), Manjaro, Manjaro (Rooted), openSUSE, openSUSE (Rooted), Parrot, Parrot (Rooted), Ubuntu, Ubuntu (Rooted), Void, Void (Rooted).

Coming soon (7): Adélie Linux, Artix Linux, BackBox, CentOS Stream, Gentoo, OpenKylin, Rocky Linux.

### 1.3 Top bar — no X11 entry

`MainActivity.kt` `TopBar` (~633–700): logo + “FluxLinux” | optional “Host: embedded” | **Terminal** | **Settings**.

Shown when `currentScreen == HOME && currentTab != TERMINAL`. Distros tab uses the same top bar. There is no monitor / display action.

`DesktopLauncher.reopenDisplay(ctx)` already opens `com.termux.x11.MainActivity` with `SINGLE_TOP | CLEAR_TOP` (Activity context, so BACK returns to FluxLinux).

### 1.4 X11 idle stub — old X11 “X” logo

`termux-x11/src/main/res/layout/main_activity.xml` `@+id/x11_image` → `@drawable/ic_x11_icon`.

`ic_x11_icon.xml` is a **500×410** white X.org mark (the classic X with a bar). Not square, not Flux branding, not a monitor.

Stub stack: icon + “not connected” + Preferences / Help / Exit (`bg_nc_stub_btn`, NativeCode green `#3DDC84`).

Mascot source of truth (do **not** invent a new character):

- `app/src/main/res/drawable/ic_logo.webp`
- `assets/logo/logo.png`

Clay-style **blue penguin** with white belly, orange beak, sitting in an open cardboard box (orange left / grey-blue right), wearing a blue backpack with a **white gear**. 3D toy, soft lighting.

### 1.5 X11 chrome — NativeCode green squares

Right-edge cluster `@+id/chrome_cluster` (`main_activity.xml` 29–67):

- `alpha="0.5"`
- Sharp `bg_nc_chrome_btn.xml`: fill `@color/nc_surface_container` (`#1E1E1E`), stroke `@color/nc_primary` (`#3DDC84`), `radius="0dp"`.
- Icons `ic_nc_back.xml` / `ic_nc_keyboard.xml` (`#FAFAFA`).
- Behavior in `termux-x11/.../MainActivity.java`: back → `goToNativeCodeHome()`, keyboard → toggle IME, both have a Y-drag touch listener. **Do not change Java click/drag.**

### 1.6 Terminal IME leak

`TerminalScreen.kt`:

- `onSingleTapUp` → `requestFocus()` + `SHOW_IMPLICIT`, fallback `SHOW_FORCED` (~362–368).
- `AndroidView.update` also `requestFocus()` every recompose (~403–406).
- IME padding: `WindowInsets.ime.union(navigationBars)` on the Column.
- `DisposableEffect(Unit)` on the view only `detachView()` / nulls the ref — **never** `hideSoftInputFromWindow`.
- ExtraKeys are Compose children; they unmount with the screen. The leak is the **system IME window**.

Navigation that leaves Terminal without hiding IME:

1. Terminal top-bar Back → `currentTab = HOME` (`MainActivity` ~624). `when (tab)` **disposes** `TerminalScreen`.
2. System back is **not** wired (no `BackHandler` / `onBackPressed` in the app). It **finishes the Activity**; IME dies with the process. Hide-on-dispose still runs. §8.3 covers a later tab-leave BackHandler.
3. FGS / `currentTabRef` only **enters** Terminal (`MainActivity` ~65–70).
4. Any future path that flips `currentTab` away from `TERMINAL`.

`SHOW_FORCED` on several OEMs keeps the IME after the focused view is gone. Home / Distros then render **under** the keyboard; `GlassScaffold` uses `contentWindowInsets = WindowInsets(0)` so those tabs do not pad for IME.

### 1.7 Distros FPS

Measured architecture (not a guess):

| Factor | Where | Cost |
|--------|--------|------|
| Eager `Column` + `verticalScroll` | `DistroScreen.kt` 61–64, 128–154 | All ~12–19 visible cards compose and measure every frame. |
| `height(IntrinsicSize.Min)` | `DistroCard` row (`GlassCard.kt` 95) | Double measure pass per card. |
| `Modifier.haze(state)` on **all** tab content | `GlassScaffold.kt` 41–45 | Entire Distros list is a Haze **source**. Scroll invalidates blur sampling. |
| `hazeChild` + `blurRadius = 100.dp` | `GlassBottomNavigation.kt` 56–59 | Full-screen-width 72dp pill samples the scrolling source every frame. |
| `hazeChild` on `TopBar` | `MainActivity.kt` 641–648 | Second blur consumer. |
| `hazeState` unused in DistroScreen | parameter only | Distros pays for blur it does not use locally. |
| Main-thread FS walk | `isDistroInstalledOnFs` inside `remember` | Jank on first paint / resume, not scroll FPS. |
| No `key(distro.id)` | `forEach` | Identity loss on tab switch. |
| WebP logos | 36dp `Image` / `painterResource` | Fine if lazy; expensive if all eager. |

Catalog size: 31 cards; method tab shows ~12 installable + ~5–7 coming-soon.

Home uses the same `Column` + scroll + haze source, but fewer cards (installed only). **Do not** rewrite Home to `LazyColumn` in this plan unless leftover jank is proven after Distros is fixed.

### 1.8 Contrast (the three screenshots)

Dark scheme (`Theme.kt`):

```
primary        = FluxDarkGrey  (#1A1C1E)   // filled surfaces, NOT accents
onPrimary      = white
secondary      = BrandCream    (#F5E6CA)   // the readable accent
onSecondary    = FluxDarkGrey
background     = FluxDarkSurface (#121212)
surface        = FluxDarkGrey
onSurface      = white
onSurfaceVariant = TextGrey (#DDDDDD)
```

`Theme.kt` already warns: TextButtons that use default `primary` content vanish on dark backgrounds. **Switches and sliders have the same trap.**

| Screenshot | Widget | Failure |
|------------|--------|---------|
| **#1 Terminal settings** | `Switch` default colors | Unchecked track ≈ surface. Checked track/thumb uses **primary = near-black**. Thumb-on looks like a dark smudge. |
| #1 | `Icon` + / − | No tint → low-contrast on the card. |
| #1 | Body / range | `onSurface` @ 0.65 / 0.50. Range “10–48” is the worst. |
| #1 | “zsh” label | `onSurface` @ 0.50, cramped against the switch. |
| #1 | `GlassSettingCard` | `surface` @ 0.60 + outline @ 0.15 → card edge almost gone. Title “Global terminal zoom” in cream is OK; the controls are not. |
| **#2 X11 Input** | Same `Switch` | Off = dark-on-dark. On (“Prefer scancodes”) = muted purple/grey blob (dynamic scheme / primary). Subtitles @ 0.60. |
| **#3 Appearance** | `InstallThemePickRow` | Selected = magenta 1dp ring (good). Unselected = `outlineVariant` (nearly invisible). Radio selected = magenta. Body @ 0.75. |
| #3 | Includes box | `surfaceVariant` @ 0.40 + `onSurfaceVariant` bullets — grey-on-grey. |

Shared widgets:

- `GlassSettingCard` — `GlassCard.kt` 434–460. Used by Terminal settings + X11 settings.
- `PrefSwitch` — local to `X11SettingsScreen.kt` 288–308. Default `Switch`.
- `InstallThemePickRow` — `InstallProgressPanel.kt` 185–223. Used by `InstallConfigScreen` **and** `OnboardingFlowScreen.OptionsPage`.

`Slider` in X11 Display uses `primary` for thumb/track (`X11SettingsScreen.kt` 146–149) — same near-black-on-black bug. Fix it in the same contrast PR even though it is not in the three screenshots.

---

## 2. Goal / non-goals

### 2.1 Goals (acceptance, user-facing)

1. **Home session card.** If any XFCE or KDE session is Starting/Running, Home shows a card **above** “Installed Distros” naming the distro and desktop type, with Open / Stop / Logs, and copy that another desktop cannot start until this one stops.
2. **Exclusive start.** A second `DesktopLauncher.start`, KDE launch, or launch-dialog desktop action is refused with a Toast. The first session stays up.
3. **Distros A–Z.** PRoot tab and Chroot tab each list uninstalled cards A–Z by `name` (case-insensitive). Coming-soon after installable, still A–Z inside that group.
4. **Home installed A–Z.** Home PRoot / Chroot installed cards use the same sorter. Catalog order (Debian-first) must not appear.
5. **Top-bar Visit display.** Monitor icon on the global top bar opens the embedded X11 activity. Live-session badge when Running/Starting.
6. **X11 idle art.** Stub uses a new **1:1** mascot+idle-monitor image. No X.org “X”. Idle stub still has Preferences / Help / Exit.
7. **X11 chrome restyle + reliability.** Back + keyboard match Flux dark (rounded, cream/white hairline, no `#3DDC84`). A tap always fires Back or Keyboard. Back returns to FluxLinux **Home**. Keyboard shows the IME. Vertical drag still moves the cluster.
8. **IME cleanup.** Leaving Terminal hides the soft keyboard. Home / Distros are not covered by a leftover IME.
9. **Distros scroll.** Distros tab stays ≥ 50 fps on a mid-range device while flinging the full PRoot list. No haze-on-scroll hitch.
10. **Contrast.** The three marked sections (Terminal settings, X11 Input switches, Appearance theme + Includes) meet WCAG AA for body (≥ 4.5:1) and have a visible switch track in both on and off states. X11 settings footer CTAs are also readable (cream + dark text; no white-on-cream, no near-black filled chip).
11. **Back → Home → exit.** System / gesture Back from Distros, Terminal, Settings, nested settings, install, or the X11 activity lands on Home. Only Back **from Home** finishes the app. X11 must not reappear under Home.

### 2.2 Non-goals

- Do not change `primary = FluxDarkGrey` (would regress every filled button).
- Do not restyle Chroot settings or Distro Settings danger zone. **Accepted side effect:** `GlassSettingCard` fill/outline tokens also apply to `SettingsScreen` and `TroubleshootingScreen` (same composable). That is a contrast win, not a layout rewrite.
- Do not rewrite KDE start scripts or `TermuxIntentFactory` beyond the exclusive-session guard.
- Do not change ExtraKeys layout, terminal font pipeline, or `SHOW_IMPLICIT` tap-to-focus (only add hide-on-leave).
- Do not LazyColumn Home or Settings.
- Do not change X11 pointer / scancode **preference semantics**. Chrome Back/Keyboard reliability **is** in scope (see §24).
- Do not generate a new mascot character. Use the existing penguin.
- Do not ship a running XFCE screenshot as the stub. Monitor must look **off / disconnected**.
- Do not delete idle-stub Preferences / Help / Exit. Restore them if the working tree still has them removed.
- Do not `finishAffinity()` / kill the process from X11 Back. Session stays running; only the X11 **activity** may finish so Home is top of the FluxLinux task.

---

## 3. Item 1 — Home “display already running” card + exclusive start

### 3.1 Single source of truth

Add a small read model so Home, TopBar, launch dialog, and `DesktopLauncher` agree.

**New file:** `app/src/main/kotlin/com/ivarna/fluxlinux/core/desktop/DesktopSession.kt`

```kotlin
data class DesktopSession(
    val distroId: String,
    val distroName: String,      // "Debian", not "debian13_chroot"
    val type: Type,              // XFCE4 | KDE
    val phase: Phase             // Starting | Running
) {
    enum class Type { XFCE4, KDE }
    enum class Phase { Starting, Running }
}

object DesktopSessionQuery {
    fun current(context: Context, ui: DesktopLauncher.UiState): DesktopSession?
}
```

Resolution order (pick **one** id, never `first()` of a mixed set):

1. If `ui.phase == Starting || Running` and `ui.distroId != null` → XFCE session from `DesktopLauncher` (authoritative for XFCE, including in-flight `prepareHost` once `start()` has flipped phase — see §3.2).
2. Else `val kdeId = StateManager.getDistrosWithGuiRunning(context).firstOrNull { StateManager.getGuiRunningType(context, it) == "kde" }` → KDE session for **that** id.
3. Else `val staleId = StateManager.getDistrosWithGuiRunning(context).firstOrNull()` (stale XFCE pref after process death, type `""` or `"xfce4"`) → treat as Running XFCE so the user can still Stop.
4. Else `null`.

Call `Query.current` every composition. Depend on `desktopUi` **and** `StateManager.refreshTrigger`. Do **not** `remember` the session forever. `setGuiRunningType` does not call `triggerRefresh()`; `setGuiRunning` does. Home KDE writes running then type — step 2’s `type == "kde"` filter avoids attaching a mid-write XFCE id.

Name: `DistroRepository.supportedDistros.find { it.id == id }?.name?.removeSuffix(" (Rooted)") ?: id`.

**Do not** invent a new SharedPreferences key. Reuse existing `distro_*_gui_running` / `distro_*_gui_type` + `DesktopLauncher.uiState`.

### 3.2 Exclusive start in `DesktopLauncher`

**Contract (do not implement the old snippet that called `finishStart`).**

`finishStart(..., ok=false)` **overwrites** `_ui` to `Idle` / requested `distroId`. If Debian is `Starting` (prefs not written until `onHealthyLine`) and Alpine is refused, the banner vanishes while `start_gui` is still streaming. Even after Running, refuse would flip phase to Idle and swap `distroId`. `finishStart` is only for *this* start when nothing else is live.

`prepareHost` is async (background extract, then `onDone` on main). Two taps or a KDE tap during that window see `phase == Idle` unless `start()` claims the session **first**.

**Required sequence:**

1. **First line of `start(ctx, distroId)`** (keep the caller `Context` for display-open; use `applicationContext` only for files / prefs / FGS):
   - If `DesktopSessionQuery.current` is a **different** distro, or `isSessionActive()` / in-flight is true for another owner: toast `"Stop {name} {type} first"`; invoke **this attempt’s** `onResult?.invoke(false)` directly — **do not** call `deliverStartResult` (that atomic belongs to the in-flight start; I-21). **Return. Do not touch `_ui`. Do not call `finishStart` or `revertToIdle`.**
   - If current session is the **same** `distroId`: toast `"Desktop already running"`; `reopenDisplay(ctx)` — the **Activity** `ctx`, never `app`; `deliverStartResult(onResult, true)`; return. (`DesktopSession.Phase` is only `Starting | Running`; `existing != null && existing.distroId == distroId` is the whole condition. There is no idle phase on `DesktopSession`.)
   - Else claim the slot **before** `prepareHost`: `_ui.phase = Starting`, `_ui.distroId = distroId`, and/or `startInFlight = true`. This is what KDE callbacks must read.
2. `prepareHost` failure: revert the claim (`phase = Idle`, clear in-flight), then `finishStart(..., false, "Host not ready", …)` is OK — nothing else was live.
3. **First line of `continueStart`**, **before** `guiShellJob?.cancel()`: authoritative re-check. If another owner appeared (should not, if step 1 holds), refuse the same way as step 1 — toast + this attempt’s `onResult(false)` (not `deliverStartResult`) + **leave `_ui` / existing job alone**.

Same-distro re-tap = reopen, not restart. User must use **Stop** to tear down.

Add `DesktopLauncher.isSessionActive(): Boolean` = `_ui.phase != Idle || startInFlight`. KDE callbacks (§3.3) must call this, not only prefs.

JVM: Running XFCE debian + `start(alpine)` → `_ui.phase` still Running, `distroId` still debian. Spec row: “XFCE `start()` in `prepareHost` + KDE tap → KDE refused, XFCE continues.”

### 3.3 KDE path

Live KDE starts today are **Turnip + Software** only. `onSelectVirGL` is still a parameter (`HomeScreen.kt` ~593, 871) but the dialog UI does not render a VirGL card. Guard Turnip + Software; keep the same guard on VirGL if the param stays.

Also the launch-dialog “Launch KDE” row (opens the picker). Distro Settings `onLaunchXfce` / `onLaunchKde` are wired in `MainActivity.kt` ~890–927 but **never invoked** from `DistroSettingsScreen` (dead parameters). If revived they need the same gate; do not treat them as a live hole today.

- If `DesktopSessionQuery.current != null` **or** `DesktopLauncher.isSessionActive()` (covers XFCE still inside `prepareHost`) → Toast + do not fire `buildLaunchKdeGui*`.
- If a KDE is already running for this distro → do not start a second intent; `EmbeddedX11.launchDisplay` only.

### 3.4 Home banner UI

New composable `ActiveDesktopCard` in `ui/components/` (keep `HomeScreen.kt` from growing further).

Placement in `HomeScreen` Column, **after** the 8dp spacer, **before** the “Installed Distros” title (~144–154):

```
if (session != null) {
    ActiveDesktopCard(
        session = session,
        onOpen = { DesktopLauncher.reopenDisplay(context) },
        onStop = { /* existing stop branch: KDE intent vs DesktopLauncher.stop */ },
        onLogs = { showDesktopLogs = true }
    )
}
```

Visual spec (Flux dark, match DistroCard radius 14–16dp):

| Element | Spec |
|---------|------|
| Container | `surface` @ 0.88, 1dp cream/white hairline @ 0.20, 16dp corners |
| Leading | 8dp vertical bar `StatusRunning` `#4CAF50` (or `#FFA000` if Starting) + 36dp distro icon |
| Title | “{Name} desktop is running” / “Starting {Name} desktop…” — `onSurface`, 16sp SemiBold |
| Subtitle | “XFCE4 on :0 — stop this session before starting another desktop.” — `onSurfaceVariant`, 12sp, **alpha ≥ 0.85** (no 0.5 greys) |
| Actions | Open (cyan `#00E5FF` / black text), Stop (`#FF5252` / white), Logs (secondaryContainer) — reuse `CompactAction` from `GlassCard.kt` or extract it |
| A11y | `contentDescription` = full title + subtitle. Stop is a button, not an icon-only. |

Empty Home (“No distros installed”) **still shows the banner** if a session is somehow live (edge: user uninstalled while GUI ran). Stop remains available.

### 3.5 Launch dialog + DistroCard Start

When `session != null` and `session.distroId != thisDistro.id`:

- DistroCard `startEnabled = false` **or** `onNavigateToStart` Toasts “Stop {session.distroName} first”. Prefer **keep Start tappable** and Toast — a disabled cream button with no explanation is how users think the app is broken.
- Launch dialog XFCE / KDE rows: if another session owns `:0`, tapping Toasts and does not start. If **this** session is already running, XFCE row becomes “Open XFCE4” → `reopenDisplay` instead of `start`.

When `session.distroId == thisDistro.id`, keep the existing Open/Stop/Logs row on the card (already implemented).

### 3.6 Onboarding / FGS

`MainActivity` `onStartDesktop` calls `DesktopLauncher.start` — the launcher guard is enough.

`DesktopSessionService` notification: no change. Optional later: “Open display” already exists via the activity.

### 3.7 Tests

New `app/src/test/.../core/desktop/DesktopSessionQueryTest.kt` with `FakePrefsContext` (already used by `TerminalPreferencesTest`):

| Case | Expect |
|------|--------|
| Idle ui + empty prefs | `null` |
| `phase=Running`, `distroId=debian` | XFCE / Debian / Running |
| `phase=Starting`, `alpine_chroot` | XFCE / Alpine / Starting |
| Idle ui + prefs `debian` running type `kde` | KDE / Debian / Running |
| Running XFCE debian **and** prefs kde on alpine | **XFCE wins** (launcher is live) |
| Stale prefs `isGuiRunning=true`, type `""`, ui Idle | XFCE / Running (so Stop is offered) |
| Stale XFCE pref + live KDE pref, ui Idle | KDE id from `firstOrNull { type == "kde" }`, **not** `first()` of the set |
| Running XFCE debian + start(alpine) refuse | `_ui.phase` still Running, `distroId` still debian (`finishStart` not used) |

New `DesktopLauncherExclusiveStartTest` **or** extract `fun shouldRefuseStart(existing, requestedId): Refuse?` as a pure function and test that. Do not spin real shells in JVM tests.

Do **not** mock Android `Toast`.

---

## 4. Item 2 — Distros page alphabetical order

### 4.1 Shared sorter

Add on `DistroRepository` (SSOT — UI must not invent a second comparator):

```kotlin
fun sortForDistroPage(distros: List<Distro>): List<Distro> =
    distros.sortedWith(
        compareBy<Distro> { it.comingSoon }
            .thenBy { it.name.lowercase(Locale.ROOT) }
            .thenBy { it.id }
    )
```

`id` tie-break keeps `debian` before `debian13_chroot` if names ever collide after stripping. They do not today (`Debian` vs `Debian (Rooted)`).

`DistroScreen`:

```kotlin
val visibleDistros = DistroRepository.sortForDistroPage(
    availableDistros.filter { ... method tab ... }
)
```

Remove the existing `sortedWith` on `availableDistros` so we do not sort twice.

### 4.2 Expected order (uninstalled, comingSoon last)

**PRoot installable A–Z:** Alpine, Arch, Chimera, Debian, Deepin, Fedora, Kali, Manjaro, **openSUSE**, Parrot, Ubuntu, Void.

**PRoot coming-soon** (only `prootSupported == true`): **Artix Linux, Rocky Linux**. Adélie / BackBox / CentOS Stream / Gentoo / OpenKylin are **chroot-only** (`prootSupported = false`) and must **not** appear on the PRoot tab.

**Chroot installable A–Z:** Alpine (Rooted), Arch (Rooted), Chimera (Rooted), Debian (Rooted), Deepin (Rooted), Fedora (Rooted), Kali (Rooted), Manjaro (Rooted), openSUSE (Rooted), Parrot (Rooted), Ubuntu (Rooted), Void (Rooted).

**Chroot coming-soon** (all seven, A–Z): Adélie Linux, Artix Linux, BackBox, CentOS Stream, Gentoo, OpenKylin, Rocky Linux.

### 4.3 Tests

Extend `DistroRepositoryTest.kt`:

```kotlin
fun distroPageSort_isCaseInsensitiveAndComingSoonLast() {
    val sorted = DistroRepository.sortForDistroPage(DistroRepository.supportedDistros)
    val available = sorted.filter { !it.comingSoon }.map { it.name }
    assertEquals("Alpine", available.first())
    assertTrue(available.indexOf("openSUSE") < available.indexOf("Parrot"))
    assertTrue(available.indexOf("openSUSE") < available.indexOf("Void"))
    assertTrue(sorted.indexOfFirst { it.comingSoon } > sorted.indexOfLast { !it.comingSoon })
    val prootSorted = DistroRepository.sortForDistroPage(
        DistroRepository.supportedDistros.filter { it.prootSupported }
    )
    assertTrue(prootSorted.none { it.id == "adelie" })
    assertTrue(prootSorted.any { it.id == "artix" && it.comingSoon })
    assertTrue(prootSorted.any { it.id == "rocky" && it.comingSoon })
}
```

Do **not** assert the full 31-name list in one blob — it breaks every new distro. Assert invariants + the `openSUSE` case.

### 4.4 Home installed list (now in scope — item 9)

Home `installedDistros` is still catalog order (`supportedDistros` is Debian-first). After the filesystem filter, run the **same** `sortForDistroPage` so each Home method tab is A–Z. Do not invent a second comparator. Install wizard and `TerminalToolSelector` stay unsorted.

See **§22**.

---

## 5. Item 3 — Top-bar “Visit X11 display”

### 5.1 `TopBar` API

```kotlin
fun TopBar(
    hazeState: HazeState,
    onSettingsClick: () -> Unit,
    onTerminalClick: () -> Unit,
    onOpenDisplay: () -> Unit,
    displayLive: Boolean,
)
```

Call site (`MainActivity` ~734–738):

```kotlin
val desktopUi by DesktopLauncher.uiState.collectAsState()
val session = DesktopSessionQuery.current(LocalContext.current, desktopUi)
TopBar(
    ...
    onOpenDisplay = { DesktopLauncher.reopenDisplay(this@MainActivity) },
    displayLive = session != null,
)
```

Use **Activity** context (`this@MainActivity`), not `LocalContext.current` if that is an application context — `reopenDisplay` skips `NEW_TASK` only for `Activity`, which makes BACK return to Home.

### 5.2 Icon + badge

- Icon: `Icons.Filled.DesktopWindows` (already used on Home).
- `contentDescription`: `"Open X11 display"` / `"Open X11 display — desktop running"`.
- When `displayLive`: 8dp green dot at the icon’s top-end (`#69F0AE`), or tint the icon `FluxAccentCyan`. Prefer a **dot** so Terminal / Settings icons stay white.
- Hit target: 48dp `IconButton` (current Settings/Terminal size). Do not shrink to fit “Host: embedded”.
- If the label “Host: embedded” crowds the row on small widths, hide that label when `displayLive` **or** wrap the right cluster in `horizontalScroll`. Prefer hiding the host label — it is debug-ish.

Idle tap: still `reopenDisplay` / `EmbeddedX11.launchDisplay`. User sees the new stub. That is intended.

### 5.3 Terminal tab

Top bar is already hidden (`showTopBar = currentTab != TERMINAL`). No display button on Terminal. Users return via Terminal Back, then use the top bar.

---

## 6. Item 4 — X11 idle stub image (AI, square)

### 6.1 Asset contract

| Field | Value |
|-------|-------|
| Purpose | Disconnected X11 stub only (`@id/x11_image`) |
| Aspect | **1:1** |
| Pixel size | 1024×1024 master; ship `xxhdpi` 384×384 webp (or 512×512 if quality drops) |
| Background | Solid `#121212` (Flux `FluxDarkSurface`) so it sits on `nc_bg` `#131313` without a halo |
| Subject | Existing Flux penguin mascot **next to / slightly in front of** a single dark computer monitor. Monitor **off**: black/near-black panel, faint grey bezel, no XFCE wallpaper, no windows, no “X” logo, no desktop icons. Optional tiny power LED **off** or dim red. |
| Style | Same clay / toy 3D as `ic_logo.webp`. Soft studio light. No text, no watermark, no X.org mark. |
| Safe area | Subject inside the center 80% so `fitCenter` at 130dp does not clip the box flaps. |

**Generation (implementation turn, not this planning turn):** `image_gen` with `aspect_ratio="1:1"`, then `image_edit` using `app/src/main/res/drawable/ic_logo.webp` as the identity reference if the first pass drifts. Reject any result that shows a lit desktop.

**Check-in path:**

```
termux-x11/src/main/res/drawable/x11_idle_mascot.webp
```

**Keep `ic_x11_icon.xml` as the notification small icon.** It is used at `termux-x11/.../MainActivity.java:776` (`setSmallIcon(R.drawable.ic_x11_icon)`). Deleting it without a Java one-liner is a compile break. PR 4 is **zero `.java` edits**: stub `srcCompat` + chrome XML/drawables only. Do not add `ic_x11_notification.xml`. The old X.org vector stays as a 24dp-ish monochrome status-bar icon (it is already a white mark on transparent).

Grep (verified 2026-08-16):

| Symbol | Hits |
|--------|------|
| `ic_x11_icon` | `main_activity.xml:83` (`x11_image` — **replace this one**); `MainActivity.java:776` (notification — **leave**) |
| `bg_nc_chrome_btn` | `main_activity.xml:45` and `:61` only (back + keyboard). ExtraKeys / stub buttons use `bg_nc_stub_btn`. **Edit in place; no fork.** |
| `x11_image` | only stub icon |

`main_activity.xml`:

```xml
<ImageView
    android:id="@+id/x11_image"
    android:layout_width="160dp"
    android:layout_height="160dp"
    android:scaleType="fitCenter"
    app:srcCompat="@drawable/x11_idle_mascot"
    android:contentDescription="@string/x11_idle_mascot_cd" />
```

Add `x11_idle_mascot_cd` = “FluxLinux mascot beside an idle X11 display”.

**Keep** Preferences / Help / Exit on the idle stub (not requested to remove). The working tree deleted them plus their `onCreate` listeners — that is a regression; restore both XML and the three Java listeners (see §24.4). Optional restyle of those stub buttons to cream is still out of scope.

Notification small icon stays `R.drawable.ic_x11_icon`. Do **not** point it at the full-color mascot webp. Do **not** edit `MainActivity.java`.

### 6.2 What “not running” means

Wrong: wallpaper, XFCE panel, terminal windows, glowing screen, X.org logo.  
Right: dark glass, no image on the panel, penguin idle / sitting.

---

## 7. Item 5 — X11 chrome buttons (UI only)

### 7.1 Drawables

Replace `bg_nc_chrome_btn.xml` (used **only** by the two chrome ImageButtons — grep before editing):

```xml
<shape android:shape="rectangle">
    <solid android:color="#CC121212"/>          <!-- 80% FluxDarkSurface -->
    <stroke android:width="1dp" android:color="#33F5E6CA"/> <!-- 20% BrandCream -->
    <corners android:radius="12dp"/>
</shape>
```

Cluster in `main_activity.xml`:

- `android:alpha="0.92"` (0.5 is why they look dirty / low contrast).
- Optional 4dp gap stays.
- Size stays 44dp (meets 44dp touch). Do not go below 40dp.

Icons stay `#FAFAFA`. If they look dull on the new fill, switch fillColor to `#F5E6CA` (cream) — still monochrome vectors.

**Do not** change `nc_primary` `#3DDC84` globally — ExtraKeys and stub buttons still use NativeCode green (`bg_nc_stub_btn`). `bg_nc_chrome_btn` is chrome-only (two ImageButtons). **Edit in place; no fork.**

### 7.2 Java (superseded for reliability)

Original contract: chrome wiring **no logic edits**. That is **void** for the follow-up. User reports Back / Keyboard do not work consistently. Targeted Java in `setupChromeCluster`, `goToNativeCodeHome`, IME show, and predictive Back **is required** — see **§24**. Still forbidden: ExtraKeys behavior, notification `setSmallIcon`, pointer/scancode handling, deleting stub Preferences/Help/Exit.

### 7.3 Light theme

X11 activity is always dark (`nc_bg`). No light chrome variant.

---

## 8. Item 6 — Terminal IME must not persist

### 8.1 Hide helper

New `app/src/main/kotlin/com/ivarna/fluxlinux/ui/terminal/ImeController.kt`:

```kotlin
fun hideIme(view: View) {
    val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.hideSoftInputFromWindow(view.windowToken, 0)
    view.clearFocus()
}
```

Keep it a function, not an object, so tests can call it with a mocked `View` if needed. Production call sites always pass a real view.

### 8.2 `TerminalScreen`

1. Extend the existing view `DisposableEffect` (the one that `detachView`s) **or** add a sibling:

```kotlin
DisposableEffect(Unit) {
    onDispose {
        terminalViewRef?.let { hideIme(it) }
        FluxTerminalSessionManager.detachView()
        terminalViewRef = null
    }
}
```

There are currently **two** effects — merge hide into the view-owned one so we cannot forget one.

2. Terminal Back: hide **before** `onBack()` so the next frame of Home is not under the IME:

```kotlin
onClick = {
    terminalViewRef?.let { hideIme(it) }
    onBack?.invoke()
}
```

3. Leave `SHOW_IMPLICIT` + `SHOW_FORCED` fallback as-is (OEM keyboards). Hiding on leave is the fix, not removing `SHOW_FORCED`.

### 8.3 Belt at `MainActivity`

When `currentTab` leaves `TERMINAL`:

```kotlin
LaunchedEffect(currentTab) {
    if (currentTab != BottomTab.TERMINAL) {
        hideIme(window.decorView)
    }
}
```

This covers any future path that flips the tab without going through Terminal Back. Item 12 adds a root `BackHandler`; that handler must also call `hideIme` when leaving Terminal.

### 8.4 ExtraKeys

No change. They unmount with the composable. Do not persist ExtraKeys visibility in a static; it is already a `remember` + prefs for the **next** Terminal visit, which is correct.

### 8.5 Tests

Hard to JVM-test `InputMethodManager`. Add a tiny `ImeController` unit test only if we wrap IMM behind an interface — **not worth it**. Device check: open Terminal, tap view (IME up), tap Back → Home fully visible, IME gone; Distros tab same.

---

## 9. Item 7 — Distros page FPS

### 9.1 Root-cause order (fix in this order)

1. **Stop using the Distros list as a Haze source.** Largest frame-time spike.
2. **`LazyColumn` + `key(distro.id)`.** Stop composing off-screen cards.
3. **Remove `IntrinsicSize.Min`** from `DistroCard`.
4. Prefetch / remember install-status set (already `remember`ed — keep; do not move FS I/O into item compose).

### 9.2 Haze

`GlassScaffold` applies `.haze(state)` to **every** tab’s content. Distros does not need backdrop blur on the list.

Options (pick A):

| Option | What | Tradeoff |
|--------|------|----------|
| **A (chosen)** | `GlassScaffold(enableHazeSource: Boolean = true)`. Distros / Terminal pass `false`. Home keeps `true` so the floating nav still frosts the cards. | Distros nav becomes a translucent surface **without** live blur. Acceptable — nav already has `surface` @ 0.7. |
| B | Always haze, but lower Distros `blurRadius` to 8dp | Still samples a scrolling source. |
| C | Snapshot a static gradient as haze source | More code, little gain. |

Implementation:

```kotlin
fun GlassScaffold(..., blurContent: Boolean = true, ...) {
    Box(Modifier.fillMaxSize().then(if (blurContent) Modifier.haze(state) else Modifier)) {
        content()
    }
}
```

`MainScreenContent` must pass `blurContent = tab == BottomTab.HOME` into the scaffold — but the scaffold is **outside** the tab `when`. So the flag lives on `GlassScaffold` at `Screen.HOME`:

```kotlin
GlassScaffold(
    hazeState = hazeState,
    blurContent = currentTab == BottomTab.HOME,
    ...
)
```

When the user is on Distros, top + bottom `hazeChild` have nothing to sample and fall back to `backgroundColor` already set on those modifiers (`GlassBottomNavigation` `surface.copy(0.7f)`, TopBar `background.copy(0.7f)`). Haze 1.1.0 should not go fully transparent; still verify. If they do, set `containerColor = surface.copy(0.92f)` on the nav when `!blurContent`.

**`hazeState` parameter:** `DistroScreen(hazeState:)` and `HomeScreen(hazeState:)` are unused in the bodies (Haze source is `GlassScaffold`). **Keep both unused params** in PR 5 — do not churn `MainScreenContent` signatures. Do not leave this implicit.

### 9.3 `LazyColumn`

Replace DistroScreen’s `Column + verticalScroll` with:

```kotlin
LazyColumn(
    modifier = Modifier.fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally,
    contentPadding = PaddingValues(bottom = 128.dp),
) {
    item(key = "title") { /* Available Distros + spacer */ }
    item(key = "tabs") { MethodTabs(...) }
    if (visibleDistros.isEmpty()) {
        item(key = "empty") { /* existing empty copy */ }
    } else {
        items(visibleDistros, key = { it.id }) { distro ->
            if (distro.comingSoon) CompactDistroCard(distro)
            else DistroCard(...)
        }
    }
}
```

Remove the trailing `Spacer(128.dp)` — it becomes `contentPadding`.

Keep `MethodTabs` as a header `item`, not a sticky header (sticky would reintroduce measure cost; not requested).

### 9.4 `DistroCard` measure

`GlassCard.kt` Row uses `height(IntrinsicSize.Min)` so the 3dp method bar matches card height. Replace with:

```kotlin
Box(Modifier.fillMaxWidth().padding(...).clip(CardShape).background(...).border(...)) {
    Box(Modifier.align(Alignment.CenterStart).width(3.dp).fillMaxHeight().background(methodColor))
    Column(Modifier.padding(start = 12.dp + 3.dp, ...)) { ... }
}
```

**Do not use `Modifier.fillMaxHeight()` for the stripe.** In an unbounded `Column` / `LazyColumn` the first measure pass gives the bar max-height `Infinity` → it collapses to 0. Size the card from the `Column`, then overlay the stripe with `Modifier.align(Alignment.CenterStart).matchParentSize()` and clip/draw only a 3dp leading strip. Same fix on `ActiveDesktopCard` (8dp / 4dp status bar). Verify Home + Distros. See **§26 I-19**.

**Do not** split `DistroCard` into two files in this PR.

### 9.5 Other cheap wins (same PR)

- `contentType = { if (it.comingSoon) "soon" else "full" }` on `items` for reuse.
- Do not read `installState` inside every card if only `isCurrentlyInstalling` needs the id — already passed as params; keep it.
- Do not add `animateItem()` — extra cost.

### 9.6 What not to do

- Do not remove Haze from Home (visual regression of the floating nav).
- Do not `LazyColumn` a 2–4 card Home list.
- Do not decode logos with Coil unless profiler says so. `painterResource` + lazy items is enough.
- Do not run `isDistroInstalledOnFs` per item.

### 9.7 Verification

On a physical device, Distros → PRoot, fling through coming-soon:

- GPU rendering profile / `adb shell dumpsys gfxinfo <pkg> framestats` — 90th percentile frame < 20ms after warm-up.
- Qualitative: no blur smear, no missed frames while the green/cyan method bar stays aligned.

---

## 10. Item 8 — Contrast / section UI rehaul

Redo the **sections**, not just hex values. Shared tokens first, then the three UIs.

### 10.1 New tokens (`ui/theme/Color.kt` + small component file)

```kotlin
// Contrast-safe on #121212 / #1A1C1E
val FluxBodyMuted = Color(0xFFC8C8C8)          // ~10:1 on #121212
val FluxHairline = Color(0x33FFFFFF)
val FluxCardFill = Color(0xE61A1C1E)           // 90% surface
val FluxSwitchCheckedTrack = BrandCream        // #F5E6CA
val FluxSwitchCheckedThumb = FluxDarkGrey
val FluxSwitchUncheckedTrack = Color(0xFF3A3A3A)
val FluxSwitchUncheckedThumb = Color(0xFFE8E8E8)
val FluxSwitchUncheckedBorder = Color(0xFF6B6B6B)
```

New `ui/components/FluxSwitch.kt`:

```kotlin
@Composable
fun FluxSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = FluxSwitchCheckedThumb,
            checkedTrackColor = FluxSwitchCheckedTrack,
            checkedBorderColor = FluxSwitchCheckedTrack,
            uncheckedThumbColor = FluxSwitchUncheckedThumb,
            uncheckedTrackColor = FluxSwitchUncheckedTrack,
            uncheckedBorderColor = FluxSwitchUncheckedBorder,
        ),
        modifier = modifier
    )
}
```

**Never** use default `Switch()` in Terminal settings or X11 settings after this PR.

`GlassSettingCard`: raise fill to `FluxCardFill` (or `surface.copy(0.90f)`), outline to `FluxHairline` (1dp). Keep 24dp corners.

This composable is also used by `SettingsScreen` (five cards) and `TroubleshootingScreen`. **Accepted:** those screens pick up the same fill/outline. Do not add a `legacy` parameter. Do not restyle their **contents** (rows, icons, copy).

### 10.2 Terminal settings (`TerminalSettingsScreen.kt`) — Image #1

Rehaul, same three settings, clearer hierarchy:

**Card A — Global terminal zoom**

- Section label: cream `secondary`, 13sp Medium, “DISPLAY” (or keep “Global terminal zoom” as the card title in **onSurface** 16sp Bold — cream is for **section headers**, not control titles). **Decision:** titles = `onSurface`; cream only for a small “Terminal” context line if needed. The current cream title on a near-black card is readable but inconsistent with Extra-keys / Guest titles (those are already `onSurface`). Make all three titles `onSurface`.
- Description: `FluxBodyMuted`, 13sp, 20sp line height. No 0.65 alpha.
- Stepper: 48dp circular `-` / `+` on `surfaceVariant` with **explicit** `onSurface` icon tint. Value “27 pt” 22sp Bold `onSurface`. Range “Range 10–48” `FluxBodyMuted` 12sp (not 0.50).
- Disable `−` at `FONT_MIN`, `+` at `FONT_MAX` (already coerced; also disable the button).

**Card B — Extra keyboard rows**

- Title `onSurface` 16sp Bold.
- Description `FluxBodyMuted` 13sp. Keep the existing sentence.
- `FluxSwitch` aligned center-end. Minimum 48dp height row.

**Card C — Guest login shell**

- Same row pattern.
- Replace the dim “zsh” text with a **12sp cream chip** (“zsh” / “bash”) to the left of the switch so the state is readable at a glance. Chip: `secondary.copy(0.18)` fill, `secondary` text.
- Keep the semantics `contentDescription`.

Scaffold background stays `colorScheme.background`. Top app bar unchanged.

### 10.3 X11 Input (`X11SettingsScreen.kt`) — Image #2

`PrefSwitch`:

- Title `onSurface` Medium 15sp.
- Subtitle `FluxBodyMuted` 13sp (replace `onSurface.copy(0.6f)`).
- `FluxSwitch` instead of `Switch`.
- Row min height 56dp, 12dp vertical padding. Title + subtitle must not wrap under the thumb.
- `PrefDivider`: `FluxHairline`, 8dp vertical padding (12dp is sparse once rows are taller).

Apply `FluxSwitch` to **all** switches on this screen (Display + Input), not only the three in the screenshot — otherwise Fullscreen / Keep screen on stay broken.

Slider (Display scale): thumb + active track = `secondary` (cream), inactive = `FluxSwitchUncheckedTrack`. **Stop using `primary`.**

### 10.4 Appearance theme (`InstallThemePickRow` + Includes) — Image #3

Restyle `InstallThemePickRow` (covers Install wizard **and** onboarding Options):

| State | Fill | Stroke | Radio | Title | Desc |
|-------|------|--------|-------|-------|------|
| Selected | `secondary.copy(0.10)` | 1.5dp `FluxAccentMagenta` | magenta | `onSurface` | `FluxBodyMuted` |
| Unselected | `FluxCardFill` | 1dp `FluxHairline` | outline `FluxSwitchUncheckedBorder` | `onSurface` | `FluxBodyMuted` |

Radio `selectedColor = FluxAccentMagenta` stays (already). Unselected radio must use a **visible** `unselectedColor` (`#9A9A9A`), not default.

Includes box (`InstallConfigScreen` ~227–268 and onboarding `OptionsPage` ~560–594):

- Fill `FluxCardFill`, 1dp `FluxHairline`, 16dp corners.
- Heading “Includes” `onSurface` 14sp Bold.
- Bullets `FluxBodyMuted` 13sp — **not** `onSurfaceVariant` on a 40% surfaceVariant wash.
- Optional 4dp leading cream dash instead of `•` for a bit more structure. Do not add icons (no assets).

Do **not** change the Install CTA, progress panel, or onboarding copy.

`DistroSettingsScreen.SettingsThemeOption` is a different widget (dialog). Out of scope unless it is visually identical and one-line to point at the same row. **Skip** to avoid a settings-dialog regression.

### 10.5 Light theme

`FluxSwitch` / `FluxBodyMuted` are tuned for dark. If `ThemeMode.LIGHT` is active:

- `FluxBodyMuted` `#C8C8C8` fails on white.
- Gate: `if (MaterialTheme.colorScheme.background.luminance() < 0.5)` use dark tokens; else `onSurfaceVariant` and default M3 switch with `checkedTrackColor = secondary` (dark grey on cream).

Implement the gate in `FluxSwitch` and a `fluxMutedText(): Color` helper so Install / Terminal / X11 stay correct in light mode.

**App theme is forced dark.** `MainActivity.kt:446–447` sets `ThemeMode.DARK` (“Force Permanent Dark Mode”). Settings theme hooks (`onThemeChanged`) are unused. `InstallThemePickRow` is the **guest XFCE** dark/light picker, not the app theme. The luminance gate is defensive only; users will not see light tokens until theme is un-hardcoded.

### 10.6 Tests

No screenshot tests. Add `FluxContrastTokensTest` that asserts:

- `FluxBodyMuted` contrast vs `#121212` ≥ 7:1 (or ≥ 4.5:1).
- `FluxSwitchUncheckedTrack` ≠ `FluxDarkSurface` (off track must not equal background).

Contrast math can live in the test as relative luminance (WCAG). Keep it dependency-free.

---

## 11. Shared implementation notes

### 11.1 File touch list (expected)

| File | Items |
|------|-------|
| `core/desktop/DesktopSession.kt` (**new**) | 1, 3 |
| `core/desktop/DesktopLauncher.kt` | 1 |
| `ui/screens/HomeScreen.kt` | 1 |
| `ui/components/ActiveDesktopCard.kt` (**new**) | 1 |
| `ui/components/GlassCard.kt` | 1 (optional CompactAction extract), 7 (intrinsic) |
| `core/data/DistroRepository.kt` | 2 |
| `ui/screens/DistroScreen.kt` | 2, 7 |
| `MainActivity.kt` | 3, 6, 7 (`blurContent`) |
| `ui/components/GlassScaffold.kt` | 7 |
| `ui/components/GlassBottomNavigation.kt` | 7 (fallback fill) |
| `ui/screens/TerminalScreen.kt` | 6 |
| `ui/terminal/ImeController.kt` (**new**) | 6 |
| `termux-x11/.../main_activity.xml` | 4, 5 |
| `termux-x11/.../drawable/x11_idle_mascot.webp` (**new**) | 4 |
| `termux-x11/.../drawable/bg_nc_chrome_btn.xml` (edit in place) | 5 |
| `termux-x11/.../values/strings.xml` | 4 |
| `ui/theme/Color.kt` | 8 |
| `ui/components/FluxSwitch.kt` (**new**) | 8 |
| `ui/components/GlassCard.kt` `GlassSettingCard` | 8 |
| `ui/screens/TerminalSettingsScreen.kt` | 8 |
| `ui/screens/X11SettingsScreen.kt` | 8 |
| `ui/install/InstallProgressPanel.kt` `InstallThemePickRow` | 8 |
| `ui/screens/InstallConfigScreen.kt` Includes box | 8 |
| `ui/onboarding/OnboardingFlowScreen.kt` Includes box | 8 |
| `app/src/test/.../DistroRepositoryTest.kt` | 2 |
| `app/src/test/.../DesktopSessionQueryTest.kt` (**new**) | 1 |
| `app/src/test/.../FluxContrastTokensTest.kt` (**new**) | 8 |

**Must stay empty:** start/stop GUI shell scripts, `DistroInstallProfile`, rootfs, ExtraKeys, `Theme.kt` colorScheme.primary.  
`termux-x11/.../MainActivity.java` may change **only** for §24 (chrome tap/IME/Back) and to **restore** the three stub-button listeners. Notification `setSmallIcon(R.drawable.ic_x11_icon)` stays.

### 11.2 Extract `CompactAction`

`CompactAction` is `private` in `GlassCard.kt`. `ActiveDesktopCard` needs the same Open/Stop/Logs buttons. **Lift** `CompactAction` to internal in the same file (or `FluxActions.kt`) in PR1 so Home banner and DistroCard share one button.

### 11.3 `DesktopLauncher.start` concurrency

See §3.2 — this is the contract, not an appendix. First line of `start()` claims the slot (`phase = Starting` and/or `startInFlight`). Authoritative refuse at first line of `continueStart` **before** `guiShellJob?.cancel()`. Refuse never calls `finishStart`. Same-distro reopen uses the caller Activity `ctx`.

### 11.4 Stale `isGuiRunning` after crash

If XFCE dies and prefs stay `true`, the banner still shows. Stop must remain safe: `DesktopLauncher.stop` already clears prefs. Banner Stop on a dead session should still run `stop` / clear flags. Do not add a liveness probe in this plan (no `pidof X` on the UI thread).

---

## 12. Risks and regressions

| Risk | Mitigation |
|------|------------|
| Exclusive start blocks same-distro reopen | Same-id → `reopenDisplay(ctx)` (Activity), not refuse |
| Refuse second start idles live XFCE | **Never** `finishStart` / `revertToIdle` on refuse |
| KDE tap during XFCE `prepareHost` | `start()` claims `phase=Starting` before `prepareHost`; KDE reads `isSessionActive()` |
| KDE + XFCE both look “running” | Query prefers live `DesktopLauncher` phase; KDE via `firstOrNull { type == "kde" }` |
| Banner Stop on KDE calls `DesktopLauncher.stop` | Reuse Home’s existing branch (`runningType == "kde"` → stop intent) |
| `sortForDistroPage` changes Terminal grid | Do **not** call it from `TerminalToolSelector` |
| Deleting `ic_x11_icon` breaks notification | **Do not delete.** Leave Java `setSmallIcon` on the vector. |
| `bg_nc_chrome_btn` shared | Verified chrome-only; edit in place |
| Haze off on Distros makes nav invisible | Explicit `surface` fill fallback |
| `fillMaxHeight` bar without intrinsic collapses | Implement as overlay Box; verify Home + Distros |
| Light theme muted text vanishes | `fluxMutedText()` luminance gate |
| `SHOW_FORCED` hide fails on OEM | Also `hideIme(window.decorView)` from `MainActivity` |
| Onboarding `onStartDesktop` during an existing session | Launcher refuse + Toast |
| `openSUSE` locale | `Locale.ROOT` lowercase, not default locale |
| New webp bloat | 384–512 px webp, not 1024 in APK |
| Reviewer “also sort Home” | **Now in scope** — user asked (item 9) |

---

## 13. Implementation sequence (inside each PR)

Do not implement “all 8 files at once.” Per PR:

1. Write / extend JVM tests first (sort, session query, contrast tokens).
2. Land logic (exclusive start, sort helper, IME hide).
3. Land UI (banner, top bar, settings, chrome, asset).
4. `./gradlew :app:testIvarnaDebugUnitTest` (or the module’s existing test task).
5. For UI PRs: Ivarna release + visual pass on Home, Distros fling, Terminal IME, X11 stub, three settings screens.

---

## 14. Device / visual QA checklist

### Home

- [ ] No session → no banner; Start on a card still opens the launch dialog.
- [ ] Start XFCE on Debian → banner “Debian desktop is running”; Alpine Start Toasts; Debian card shows Open/Stop/Logs.
- [ ] Banner Open → X11 activity; BACK → Home, session still running.
- [ ] Banner Stop → banner gone; cards back to Start.
- [ ] Start KDE (if installed) → banner says KDE; XFCE start Toasts.
- [ ] Empty installed list + stale running flag → banner still offers Stop.

### Distros

- [ ] PRoot: Alpine … Void A–Z; `openSUSE` between Manjaro and Parrot, **not** after Void.
- [ ] Home installed PRoot / Chroot lists are the same A–Z (not Debian-first catalog order).
- [ ] Chroot: `Alpine (Rooted)` first among rooted.
- [ ] Coming soon after installable. PRoot soon = Artix + Rocky only (no Adélie). Chroot soon = all seven, Adélie first.
- [ ] Fling is smooth; method bar on cards still full height.
- [ ] Bottom nav still readable without live blur.

### Top bar

- [ ] Monitor icon on Home and Distros.
- [ ] Hidden on Terminal.
- [ ] Idle: opens stub with new art.
- [ ] Live: green dot; opens the running display.

### X11

- [ ] Stub: square penguin + **off** monitor. No X.org mark.
- [ ] Chrome: rounded dark pills, cream hairline, no green, still 50%+ visible.
- [ ] A normal press (well over 100ms) on Back returns to FluxLinux **Home**. A second Back from Home exits. X11 does not reappear.
- [ ] A normal press on Keyboard shows the IME on the X11 surface. Hide still works on a second tap.
- [ ] Drag Y still moves the cluster. Small vertical jitter must not swallow the tap.
- [ ] Idle stub still has Preferences / Help / Exit.
- [ ] Notification icon still monochrome / not a 512px photo.

### Terminal IME

- [ ] Tap view → IME. Back → Home, IME gone, no hole at the bottom.
- [ ] ExtraKeys hide with the screen (already).
- [ ] Re-enter Terminal → IME not auto-shown until tap (current behavior).

### Contrast

- [ ] Terminal settings: titles white, body `#C8C8C8`, −/ + visible, off switch track visible, on switch cream track / dark thumb, zsh chip readable.
- [ ] X11 Input: same switches; long titles do not sit under the thumb.
- [ ] X11 footer: “Open full X11 preferences” is a visible outlined / cream-hairline chip with readable label. “Re-apply to running display” is cream fill + **dark** text (not white-on-cream).
- [ ] Appearance: selected Dark has magenta ring + wash; unselected Light has a visible border; Includes bullets readable.
- [ ] Light theme (if toggled): body text still visible.

### System Back

- [ ] Distros + Back → Home (app still running).
- [ ] Terminal + system Back → Home, IME gone.
- [ ] Settings / X11 settings / Terminal settings + Back → parent, then Home. Does **not** exit.
- [ ] X11 chrome Back or X11 system Back → FluxLinux Home. Session still running.
- [ ] Home + Back → app exits. X11 activity is not revealed.

---

## 15. Alternatives considered

| Topic | Rejected | Why |
|-------|----------|-----|
| Only disable other Start buttons (no banner) | User asked for a **card at the top** |
| Kill the previous session when starting a new one | User: must **close** first; silent swap is data-lossy |
| Hide top-bar X11 when idle | User said “visit the x11 display”; stub is a valid visit |
| Pure A–Z including coming-soon mixed in | Buries Alpine under Adélie |
| Sort Home installed list | **Accepted** after user follow-up (item 9) |
| Recolor `colorScheme.primary` to cream | Breaks every filled dark button (`Theme.kt` history) |
| Keep `Column` + only kill Haze | 19 composed `DistroCard`s + intrinsic measure still jank on low-end |
| Keep Haze + only `LazyColumn` | Blur source still invalidates every scroll frame |
| Vector mascot instead of AI raster | User asked to generate an image; penguin is already raster |
| Running-desktop screenshot as stub | User: monitor **not running** |
| Change chrome Java to Compose | Out of scope. Keep View chrome; fix tap/IME/Back in Java. |

---

## 16. Open questions

None that block implementation. Defaults above are product decisions. If a later implementer disagrees:

1. **Top-bar icon when idle** — we chose always visible. Alternative: hide until `session != null`. Only change if the user says the idle icon is noise.
2. **Home installed A–Z** — **decided in.** `sortForDistroPage` on Home installed lists.
3. **Stub Preferences / Help / Exit** — keep NativeCode green styling; **must remain present**. Restore if deleted.

Do **not** escalate these to the user unless implementation hits a conflict.

---

## 17. Key decisions

1. **Exclusive display is enforced in `DesktopLauncher` + KDE callbacks**, not only in Compose. Rationale: onboarding and double-taps bypass UI disables. **Refuse must not call `finishStart`** — that helper idles `_ui` and would hide a live XFCE session.
2. **`DesktopSessionQuery` is the read model** for banner, top-bar badge, and refuse-start. Rationale: XFCE live state lives in a StateFlow; KDE lives in prefs; Home must not fork that merge twice.
3. **Same-distro Start while running reopens X11** instead of restarting `start_gui`. Rationale: restart would kill the session the banner is advertising.
4. **Distros sort is case-insensitive `name` with coming-soon last**, SSOT on `DistroRepository`. Rationale: `openSUSE` is the known failure of the current `thenBy { it.name }`.
5. **Haze source is Home-only.** Rationale: Distros FPS is dominated by live blur of a scrolling list; nav already has an opaque-ish fill.
6. **`LazyColumn` + drop `IntrinsicSize.Min`.** Rationale: eager Column + double measure is the remaining CPU cost after Haze.
7. **Do not change `primary`.** Contrast is fixed with `FluxSwitch` / `FluxBodyMuted` / cream slider. Rationale: primary-as-dark is load-bearing.
8. **X11 stub is a new square webp; `ic_x11_icon.xml` stays as the notification small icon.** Rationale: status-bar cannot host a 512px photo. Java may change for chrome reliability (§24), never to point `setSmallIcon` at the mascot webp.
9. **Chrome restyle is drawables + alpha; chrome reliability is targeted Java.** Rationale: original “UI only” is superseded by the user report that Back/Keyboard do not work. Tap must not require a <100ms press. Back must land on Home. Keyboard must show IME.
10. **IME hide on Terminal dispose + tab change + Back.** Rationale: `SHOW_FORCED` survives a single hide site on some OEMs.
11. **System Back is Home-then-exit.** Rationale: user follow-up. Predictive-back-safe `BackHandler` at the Compose root. X11 Back finishes the X11 **activity** (session stays) so Home is not sitting on top of X11.
12. **X11 settings CTAs set `contentColor` explicitly.** Rationale: M3 `buttonColors(containerColor = secondary)` keeps `onPrimary` (white) → cream-on-cream.

---

## 18. PR Plan

### PR 1 — Exclusive desktop session + Home banner

- **Title:** Home: running-desktop card and single-session gate
- **Files:** `DesktopSession.kt`, `DesktopLauncher.kt`, `ActiveDesktopCard.kt`, `HomeScreen.kt`, `GlassCard.kt` (lift `CompactAction`), `DesktopSessionQueryTest.kt`
- **Depends on:** none
- **Changes:** Query + refuse-start + KDE guard + Home banner + launch-dialog Toasts. No sort, no X11 art.

### PR 2 — Distros alphabetical sort

- **Title:** Distros tab: case-insensitive A–Z
- **Files:** `DistroRepository.kt`, `DistroScreen.kt`, `DistroRepositoryTest.kt`
- **Depends on:** none (can parallel PR 1)
- **Changes:** `sortForDistroPage` only. No LazyColumn yet (that is PR 5) so review stays small.

### PR 3 — Top-bar Visit display

- **Title:** Top bar: open X11 display
- **Files:** `MainActivity.kt` (`TopBar`)
- **Depends on:** PR 1 (`DesktopSessionQuery`). **Must not land without Query.** `displayLive = desktopUi.phase != Idle` is **wrong** — KDE never writes `DesktopLauncher.uiState`. `displayLive = session != null`.
- **Changes:** Icon + badge + `reopenDisplay`.

### PR 4 — X11 stub art + chrome restyle

- **Title:** X11: Flux idle-monitor stub and dark chrome
- **Files:** `x11_idle_mascot.webp`, `main_activity.xml` (`x11_image` src + chrome alpha), `bg_nc_chrome_btn.xml`, `strings.xml`
- **Depends on:** none (can parallel PR 1–2; **not** parallel with 3/5/6)
- **Changes:** Asset + XML only. **Zero** `.java` edits. Keep `ic_x11_icon.xml` for the notification.

### PR 5 — Distros FPS

- **Title:** Distros tab: LazyColumn and Home-only Haze
- **Files:** `DistroScreen.kt`, `GlassScaffold.kt`, `GlassBottomNavigation.kt`, `MainActivity.kt`, `GlassCard.kt`
- **Depends on:** PR 2 (sort stays correct inside `items`)
- **Changes:** Performance only. Visual of cards unchanged aside from method-bar layout.

### PR 6 — Terminal IME leak

- **Title:** Hide IME when leaving Terminal
- **Files:** `ImeController.kt`, `TerminalScreen.kt`, `MainActivity.kt`
- **Depends on:** none
- **Changes:** Hide on dispose / Back / tab leave.

### PR 7 — Contrast rehaul (the three screenshots)

- **Title:** Settings contrast: FluxSwitch, theme picker, Includes
- **Files:** `Color.kt`, `FluxSwitch.kt`, `GlassSettingCard`, `TerminalSettingsScreen.kt`, `X11SettingsScreen.kt`, `InstallThemePickRow`, `InstallConfigScreen.kt`, `OnboardingFlowScreen.kt`, `FluxContrastTokensTest.kt`
- **Depends on:** none
- **Changes:** Shared tokens + the three UIs + slider. No `Theme.kt` primary change.

### PR 8 — Follow-ups from device QA + implementation review (do this next)

- **Title:** Home A–Z, X11 chrome reliability, Back-to-Home, CTA contrast, exclusive-start holes
- **Files:** `HomeScreen.kt`, `X11SettingsScreen.kt`, `MainActivity.kt` (app), `GlassCard.kt`, `ActiveDesktopCard.kt`, `DesktopLauncher.kt`, `DesktopSession.kt` (comments), `DesktopSessionQueryTest.kt` + exclusive-start test, `termux-x11/.../MainActivity.java`, `termux-x11/.../main_activity.xml`
- **Depends on:** current working tree (PRs 1–7 already applied locally)
- **Changes:** Items 9–12 plus review bugs I-15–I-22. Restore stub Preferences/Help/Exit. Do **not** broaden into ExtraKeys, rootfs, or `Theme.kt` primary.

**Suggested merge order (historical):** 2 + 4 → 1 → 6 → 3 → 5 → 7.  
**Remaining work is PR 8 only**, serial on `MainActivity.kt`, `HomeScreen.kt`, `GlassCard.kt`, and `termux-x11/MainActivity.java`.

**Do not branch PR 3 / 5 / 6 in parallel.** All three edit `MainActivity.kt` around the `Screen.HOME` / `GlassScaffold` / `TopBar` block (~633–755). PR 3 **must** follow PR 1 (Query). PR 5 after PR 2 is required. PR 6 may follow 1 or 2 but must be serial with 3 and 5 on MainActivity.

`GlassCard.kt` is touched by PR 1 (lift `CompactAction`) then PR 5 (intrinsic) then PR 7 (`GlassSettingCard`) then PR 8 (`matchParentSize` stripe) — serial.

---

## 19. Worker notes (when implementing)

- Flavor: Ivarna unless the user says otherwise. Do not uninstall the existing APK.
- Do not regenerate rootfs or touch `assets/rootfs/`.
- Mascot: pass `ic_logo.webp` as the identity reference. Square. Dark monitor. Reject lit desktops.
- After PR 5, if Distros still janks, profile **before** adding more caches. Next suspect is `hazeChild` on the top bar while Distros scrolls under a non-source — should be cheap.
- After PR 7, walk Settings → Terminal, Settings → X11, Install wizard, onboarding Options. All four share tokens; do not fix only the screenshot files.

---

## 20. Done when

All §2.1 acceptance boxes are true (including items 9–12), §14 checklist is ticked on a device, new JVM tests pass, and `git diff` shows **no** script / rootfs / `Theme.kt` primary / ExtraKeys behavior changes. Targeted X11 Java for §24 plus restored stub listeners is expected and must be reviewed, not empty.

---

## 21. Review revisions (2026-08-16)

First-pass review: **14 open** (1 critical, 6 major, 5 minor, 2 nits). Verdict was REVISE. All incorporated:

| ID | Severity | Plan change |
|----|----------|-------------|
| I-1 | critical | §3.2 refuse = toast + invoke **this attempt’s** `onResult(false)` only. Never `finishStart` / `revertToIdle`. Never touch the in-flight `startResultDelivered` atomic (I-21). Test: debian stays Running. |
| I-2 | major | `start()` claims `phase=Starting` / `startInFlight` **before** `prepareHost`. `continueStart` re-checks before `cancel()`. KDE reads `isSessionActive()`. |
| I-3 | major | Same-distro `reopenDisplay(ctx)` (Activity), never `app`. |
| I-4 | major | Keep `ic_x11_icon.xml` for notification. `bg_nc_chrome_btn` is chrome-only — edit in place. “Zero Java” is **void** for §24 reliability + stub-listener restore. |
| I-5 | major | PRoot soon = Artix + Rocky only. Chroot soon = all seven. Test: no `adelie` on proot filter. |
| I-6 | major | KDE id = `firstOrNull { type == "kde" }`. Recompose on `refreshTrigger`. |
| I-7 | major | PR 3 must use Query. Deleted `phase != Idle` fallback. |
| I-8 | minor | `GlassSettingCard` side effect on Settings / Troubleshooting accepted. |
| I-9 | minor | Keep unused `hazeState` params; no signature churn. |
| I-10 | minor | **Superseded by item 12.** Original note (“system back finishes Activity”) is no longer accepted. |
| I-11 | minor | App theme forced dark; luminance gate is defensive. |
| I-12 | nit | Same-distro = `existing != null && existing.distroId == distroId`. |
| I-13 | nit | Live KDE = Turnip + Software. Distro Settings launch callbacks dead. |
| I-14 | minor | PR 3/5/6 serial on `MainActivity` HOME block. |
| I-15 | bug | Home installed A–Z via `sortForDistroPage` (§22). |
| I-16 | bug | X11 CTA `contentColor` + no `primary` fill (§23). |
| I-17 | bug | Chrome tap/IME/`goToNativeCodeHome`+`finish()` (§24). |
| I-18 | bug | App `BackHandler` Home-then-exit (§25). |
| I-19 | bug | Stripe `matchParentSize`, not `fillMaxHeight`. |
| I-20 | bug | KDE gate: any live session blocks a new KDE start. |
| I-21 | bug | Refuse must not consume `startResultDelivered`. |
| I-22 | bug | Restore idle stub Preferences / Help / Exit. |
| I-23 | suggestion | Exclusive-start JVM row. |
| I-24 | suggestion | Drop restatement comments in `DesktopSession.kt`. |
| I-25 | nit | Unused `IntrinsicSize` import. |
| I-26 | nit | Idle mascot 160dp. |
| I-27 | nit | Chrome javadoc still says 50% alpha. |

**Do not implement from any pre-review snippet that called `finishStart` on refuse.**  
**Do not implement from any snippet that called `deliverStartResult` on a refuse of a different attempt** (I-21).

---

## 22. Item 9 — Home installed list A–Z

**User follow-up:** “installed distro page, not sorted alphabetically.”

### 22.1 Current hole

`HomeScreen.kt` ~146–205:

```kotlin
val installedDistros = remember(refreshKey.value) {
    DistroRepository.supportedDistros.filter {
        TerminalLauncher.isDistroInstalledOnFs(context, it.id)
    }
}
val prootInstalled = installedDistros.filter { !it.isChrootCard() }
val chrootInstalled = installedDistros.filter { it.isChrootCard() }
val visibleDistros = if (methodTab == MethodTab.CHROOT) chrootInstalled else prootInstalled
```

`supportedDistros` is Debian-first catalog order. Distros page already uses `sortForDistroPage`. Home does not.

### 22.2 Required change

After the filesystem filter, sort with the **same** SSOT:

```kotlin
val installedDistros = DistroRepository.sortForDistroPage(
    DistroRepository.supportedDistros.filter {
        TerminalLauncher.isDistroInstalledOnFs(context, it.id)
    }
)
```

Sorting before the PRoot/Chroot split is enough (same comparator). Do not write a second comparator. Coming-soon never appears on Home (installed-only), so the `comingSoon` key is a no-op here and that is fine.

### 22.3 Tests

Extend `DistroRepositoryTest` is already enough for the sorter. Add a Home-facing assertion only if a Home helper is extracted. Do not snapshot the full installed set — users have different guests.

Device: install Alpine + Ubuntu + Debian on PRoot. Home PRoot order must be Alpine → Debian → Ubuntu, **not** Debian → Ubuntu → Alpine.

---

## 23. Item 10 — X11 settings CTA contrast (user Image #1 this turn)

The attached screenshot is the two footer buttons on `X11SettingsScreen`, not the original Terminal-settings Image #1.

### 23.1 Failure

```kotlin
Button(
    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
) { Text("Open full X11 preferences") }   // primary = #1A1C1E on #121212 → chip vanishes

Button(
    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
) { Text("Re-apply to running display") } // secondary = #F5E6CA, contentColor stays onPrimary = white
```

M3 `buttonColors(containerColor = …)` does **not** flip `contentColor`. Cream fill + white label is the cream-on-cream pill in the screenshot.

### 23.2 Required restyle

Redo the pair as a stacked Flux action column (48dp min height, 16dp gap, full width):

| Button | Fill | Stroke | Label / icon |
|--------|------|--------|----------------|
| Open full X11 preferences | `FluxCardFill` / `surface` @ 0.90 | 1dp `FluxHairline` | `onSurface` + cream `OpenInNew` icon |
| Re-apply to running display | `secondary` (`#F5E6CA`) | none | **`onSecondary`** (`FluxDarkGrey`) |

Explicitly set `contentColor`. Never use `colorScheme.primary` as a filled container.

Keep the existing click semantics (`applyToTermux` + `openTermuxX11Preferences` / toast). Do not change Lorie pref keys.

Optional: use `OutlinedButton` for Open-preferences and `Button` for Re-apply. Either is fine if the table above holds.

---

## 24. Item 11 — X11 chrome Back + Keyboard must work

**User follow-up:** “the button back and keyboard not working consistently in x11 display page … should come back to app and keyboard show up.”

Visual restyle (dark rounded + cream hairline + 0.92 alpha) stays. Behavior is now **in scope**.

### 24.1 Why taps are flaky today

`setupChromeCluster` `dragY` (`MainActivity.java` ~699–747):

1. `ACTION_DOWN` returns `true` (consumes the stream; the real `OnClickListener` never sees the original click).
2. Click is synthesized only on `UP` when `!dragging` **and** `uptime - downTime <= ViewConfiguration.getTapTimeout()` (~**100ms**).
3. A normal press longer than 100ms, or a move past touch slop, is treated as a drag and **swallows** Back / Keyboard.

That is the inconsistency. It is not a theme bug.

### 24.2 Required chrome touch contract

Keep Y-drag of the cluster. Change the click rule:

- `ACTION_DOWN`: record raw Y, start translation, `dragging = false`. Still consume (return true) so the cluster can drag.
- `ACTION_MOVE`: if `abs(dy) > touchSlop`, `dragging = true` and translate Y as today (clamp, `translationX = 0`).
- `ACTION_UP` / `CANCEL`: if **`!dragging`**, `v.performClick()`. **Do not gate on tapTimeout.** Duration of a still finger is a tap.
- Attach the listener to back, keyboard, and cluster padding as today.

Do not require a `GestureDetector` unless the simple rule still fails on device. Do not change icon assets.

### 24.3 Keyboard must show the IME

`toggleKeyboardVisibility` today:

- no-ops IME when `externalKeyboardConnected && !showIMEWhileExternalConnected` (many devices report a fake external keyboard);
- uses deprecated `toggleSoftInput(SHOW_FORCED, 0)`, which is unreliable in fullscreen / immersive X11.

Required for the **chrome keyboard button only** (ExtraKeys path may keep calling the existing helper):

```
lorieView.requestFocus()
imm.showSoftInput(lorieView, InputMethodManager.SHOW_FORCED)
```

If IME is already visible, hide with `hideSoftInputFromWindow`. Do not refuse just because `externalKeyboardConnected` is true unless the user turned **off** “Show IME with external keyboard” **and** a hardware keyboard is actually connected. Default chrome tap must show the keyboard.

Do not change ExtraKeys layout or `TermuxX11ExtraKeys` behavior beyond sharing a reliable show/hide if you extract a helper.

### 24.4 Back must return to FluxLinux Home

`goToNativeCodeHome()` today starts `com.ivarna.fluxlinux.MainActivity` with only `REORDER_TO_FRONT | SINGLE_TOP`. Two holes:

1. Auto-open uses application context (`DesktopLauncher.openX11(app)` → `NEW_TASK`). KDE uses `EmbeddedX11.launchDisplay` which **always** sets `NEW_TASK`. X11 is then often another task; reorder-to-front does not raise FluxLinux.
2. When X11 **is** in the same task, reorder leaves X11 **under** FluxLinux. The next system Back from Home reveals X11 instead of exiting — this violates item 12.

**Required `goToNativeCodeHome()`:**

```
Intent to {package}.MainActivity
  flags: SINGLE_TOP | CLEAR_TOP | REORDER_TO_FRONT
         + NEW_TASK if FluxLinux is not in this task
  extra: EXTRA_TARGET_PAGE = "home"   // force Home tab, not Terminal
startActivity(intent)
finish()   // pop the X11 activity; X server / session stays running
```

`onBackPressed` / a predictive-back `OnBackPressedCallback` on the X11 activity must call the same method. Do **not** `finishAffinity()`. Do not stop the desktop session.

App `MainActivity.onNewIntent` already handles `EXTRA_TARGET_PAGE == "terminal"`. Add `"home"` → `currentScreen = HOME`, `currentTab = HOME`.

KDE / same-distro reopen must use `DesktopLauncher.reopenDisplay(activity)` (Activity context, no `NEW_TASK`) instead of `EmbeddedX11.launchDisplay` so BACK returns to Home in the same task.

### 24.5 Restore idle stub Preferences / Help / Exit

The working tree deleted the three stub buttons from `main_activity.xml` and their `onCreate` listeners from `MainActivity.java`. That was **not** requested. Restore:

- `preferences_button` → `LoriePreferences`
- `help_button` → existing help URI
- `exit_button` → `finish()` of the X11 activity (session may still be running; this only closes the surface)

Keep NativeCode green `bg_nc_stub_btn` on those three. Do not restyle them in this item.

### 24.6 What Java may and may not change

**Allowed:** `setupChromeCluster` tap rule, chrome keyboard show/hide, `goToNativeCodeHome` flags + `finish()`, X11 `OnBackPressedCallback`, restore the three stub listeners, javadoc (“50% alpha” is stale).

**Forbidden:** ExtraKeys behavior, notification `setSmallIcon`, pointer/scancode, `CmdEntryPoint`, deleting `ic_x11_icon.xml`.

### 24.7 Device checks

- Idle and running display: Back tap (press and hold ~200ms) → FluxLinux Home.
- Keyboard tap → IME visible on the X11 surface; second tap hides.
- Drag the cluster vertically; release without a tap → no navigation / no IME flip.
- Notification small icon still monochrome.
- Preferences / Help / Exit visible on the disconnected stub.

---

## 25. Item 12 — System Back: Home first, then exit

**User follow-up:** “when back button is clicked should come to home page only then exit.”

### 25.1 Current hole

There is **no** `BackHandler` / `OnBackPressedCallback` in the app module (`MainActivity.kt` ~729 `when (currentScreen)`). In-app arrow buttons already pop Settings → Home and Terminal → Home tab. **System / gesture Back** finishes `MainActivity` from Distros, Terminal, Settings, X11 settings, install, etc.

X11 Back is item 11 (`goToNativeCodeHome` + `finish()`). After that, the user is on FluxLinux Home; the next Back must **exit**, not reopen X11.

### 25.2 Required handler

One predictive-back-safe handler at the `setContent` root (Compose `BackHandler` is enough; it registers an `OnBackPressedCallback`):

```
if (currentScreen == SETTINGS_TERMINAL
    || currentScreen == SETTINGS_X11
    || currentScreen == SETTINGS_CHROOT
    || currentScreen == TROUBLESHOOTING
    || currentScreen == ROOT_ACCESS) {
    currentScreen = SETTINGS
} else if (currentScreen == SETTINGS
    || currentScreen == DISTRO_SETTINGS
    || currentScreen == INSTALL_WIZARD) {
    currentScreen = HOME
    currentTab = HOME
} else if (currentScreen == ONBOARDING || currentScreen == PREREQUISITES) {
    // leave default (do not trap the user in onboarding with a dead Back)
    finish()
} else if (currentScreen == HOME && currentTab != HOME) {
    if (currentTab == TERMINAL) hideIme(window.decorView)
    currentTab = HOME
} else {
    // already Home tab
    finish()
}
```

Do not finish the process from Settings / X11-settings / Terminal / Distros. Hide IME when leaving Terminal via this path (same helper as item 6).

### 25.3 X11 vs app

| Where | Back does |
|-------|-----------|
| X11 chrome or X11 system Back | `goToNativeCodeHome()` then `finish()` X11 activity. Session stays. Lands on Home tab. |
| Distros / Terminal / Settings / nested | Pop toward Home as above. |
| Home tab | `finish()` FluxLinux. X11 activity is already gone, so the launcher (not X11) is next. |

### 25.4 Do not

- `finishAffinity()` from X11 (would kill FluxLinux too).
- Stop the desktop session on Back.
- Add a confirmation dialog.

---

## 26. Implementation review (2026-08-16, local)

Reviewer: `/tmp/grok-1000/grok-review-a6ec1bfc.md`. Verdict **REVISE**. 8 bugs, 2 suggestions, 3 nits.

What already matches the original eight-item contract (keep; do not rewrite):

- `DesktopSession` + `DesktopSessionQuery` precedence (live XFCE → KDE `type=="kde"` → stale XFCE → null).
- `start()` claims `Starting` / `startInFlight` before `prepareHost`.
- Different-distro refuse does not call `finishStart` / `revertToIdle` / cancel the live job.
- Distros page `sortForDistroPage` after the method filter; tests cover `openSUSE`, no Adélie on PRoot, Artix/Rocky on PRoot.
- Top-bar monitor; `displayLive = session != null`; `reopenDisplay(this@MainActivity)`.
- Idle mascot webp; `ic_x11_icon.xml` still the notification icon.
- Distros `LazyColumn` + `key(id)` + Home-only Haze source.
- `hideIme` on Terminal dispose, Terminal Back, tab leave.
- `FluxSwitch` on all X11 settings switches; slider uses `secondary`; theme picker / Includes tokens; `Theme.kt` primary untouched.

### 26.1 Bugs that remain (must fix in PR 8)

| ID | Sev | File | Fix |
|----|-----|------|-----|
| I-15 | bug | `HomeScreen.kt:146` | Item 9 — `sortForDistroPage` on installed list. |
| I-16 | bug | `X11SettingsScreen.kt:252` | Item 10 — explicit `contentColor`; no `primary` fill. |
| I-17 | bug | `termux-x11/MainActivity.java:697` | Item 11 — tapTimeout, IME show, `goToNativeCodeHome` + `finish()`. |
| I-18 | bug | `MainActivity.kt:729` | Item 12 — root `BackHandler`. |
| I-19 | bug | `GlassCard.kt:92`, `ActiveDesktopCard.kt:67` | Stripe via `matchParentSize`, not `fillMaxHeight()`. |
| I-20 | bug | `HomeScreen.kt:561` | KDE row / all three GPU callbacks: if `Query.current != null` **or** `isSessionActive()`, toast and return, unless this distro already has **KDE** running (then reopen only). Same-distro XFCE must not open the KDE picker. |
| I-21 | bug | `DesktopLauncher.kt:76` | Refuse of a *different* attempt must **not** call `deliverStartResult`. That atomic is per in-flight start (reset only in `continueStart`). Invoke the refused `onResult(false)` directly, or use a per-start token. Otherwise a refuse during `prepareHost` steals the live start’s success callback. |
| I-22 | bug | `main_activity.xml:91` + Java listeners | Restore Preferences / Help / Exit (§24.5). |

### 26.2 Suggestions / nits

| ID | Sev | Fix |
|----|-----|-----|
| I-23 | suggestion | Add the exclusive-start JVM row: Running XFCE debian + `start(alpine)` leaves `_ui` Running / debian. Extract `shouldRefuseStart` if `DesktopLauncher` is too Android-heavy to construct. Do not mock `Toast`. |
| I-24 | suggestion | Delete numbered restatement comments in `DesktopSession.kt`. One line on why live launcher beats prefs is enough. |
| I-25 | nit | Drop unused `IntrinsicSize` import after the stripe fix. |
| I-26 | nit | Idle mascot `ImageView` 160dp as specified (working tree is 180dp). |
| I-27 | nit | `setupChromeCluster` javadoc still says “50% alpha”. |

### 26.3 Exclusive-start claim still required

I-21 is the only remaining refuse-path correctness bug. Do **not** “fix” it by calling `finishStart` on refuse.

KDE I-20 is a live `:0` clobber: XFCE Starting/Running for Debian + “Launch KDE” on Debian still opens the GPU picker because the guard is `session.distroId != thisDistro.id`.

---

## 27. PR 8 implementation sequence

Do not land items 9–12 as one untested blob. Order:

1. **Restore** stub Preferences / Help / Exit (XML + three Java listeners). Confirm `git diff` on Java is only that restore plus later §24 hunks.
2. **Chrome tap rule** (drop tapTimeout). Device-tap Back and Keyboard.
3. **`goToNativeCodeHome` + X11 BackCallback + `EXTRA_TARGET_PAGE=home` + `finish()`**. Device: Back → Home; Home Back → exit; session still running.
4. **Chrome IME show** on `LorieView`. Device: keyboard appears.
5. **App `BackHandler`** (item 12). Device: Distros / Settings / Terminal system Back → Home.
6. **Home `sortForDistroPage`**. Visual A–Z on installed PRoot / Chroot.
7. **X11 CTA colors** (item 10).
8. **KDE exclusive gate** (I-20).
9. **`deliverStartResult` isolation** (I-21) + exclusive-start JVM test (I-23).
10. **`matchParentSize` stripes** (I-19) + nits I-24–I-27.

After each step: compile. After 9: `./gradlew :app:testIvarnaDebugUnitTest`. After visual steps: Ivarna release + `adb install -r` (do not uninstall).

**Done when** §2.1 items 1–11, §14 including the new Back / CTA / Home-sort rows, I-15–I-27 closed, JVM tests green, and forbidden surfaces (scripts, rootfs, `Theme.kt` primary, ExtraKeys) still untouched.
