# Plan: Terminal page grid + ExtraKeys + interactive shell (nativecode-ai parity)

**Date:** 2026-08-09  
**Updated:** 2026-08-09 (post-implement review)  
**Branch:** `feat/embedded-terminal-bootstrap-proot-chroot`  
**Commit:** `685eebb` — `feat(terminal): nativecode-parity shell grid, interactive TerminalView, ExtraKeys`  
**Status:** **IMPLEMENTED (T1–T5 + R1–R5)** — residual fixes landed; **T6 device smoke** open  
**Refs:** `~/repos/nativecode-ai` (SSOT for UX), Flux `TerminalScreen.kt`, `SessionRegistry.kt`, `GuestSessionFactory.kt`

---

## 1. Problem (device) — original symptoms

| Symptom | Observed / likely cause |
|---------|-------------------------|
| Terminal page is **not a grid with original icons** | Empty state was a vertical list of Material buttons + generic `Icons.Default.Terminal` |
| Terminal is **not interactive** | Soft keyboard / ExtraKeys / focus path incomplete vs nativecode; key injection used `session.write` + `dispatchKeyEvent` instead of `TerminalView.onKeyDown` / `inputCodePoint` |
| ExtraKeys feel incomplete | Single thin Compose row; no ENT/BKSP, no lock-on-long-press mods, no F-keys, no icon arrows; wrong inject path |
| Session open UX | No toast on host prepare failure; no host shell card; chroot gated only by installed rootfs |

User priority: **do these first** (before further customization-script polish).

---

## 2. nativecode-ai reference (what was ported)

Source tree: `/home/flux/repos/nativecode-ai`

### 2.1 Tool selector grid (Terminal page empty state)

| Piece | Location |
|-------|----------|
| Builder | `MainActivity.buildTerminalToolSelectorView()` ~L2184 |
| Card factory | `makeToolCard` — 2-column grid, 64dp icon, label + desc |
| Sections | Free CLI / Paid CLI / **DEBIAN SHELL** (Flux: shells only for v1) |
| Catalog SSOT | `cliauth/ToolLauncherCatalog.kt` → `shell`, `shell-root` |
| Icons | `assets/images/cli/{type}.png` via `cliToolAssetFilename()` (~L18722); full-color PNGs **must not** get `setColorFilter` |
| Launch | `createNewTerminalSession(type)` → `TerminalSession` + hide selector, show `TerminalView` |

Shell cards (product):

| type | label | guest user |
|------|-------|------------|
| `shell` | Debian Shell | `flux` |
| `shell-root` | Debian Shell Rooted | `root` |

Nativecode also filters AI tools by method; **Flux v1** ships **system shells only** (+ Host shell), not AI CLI marketplace.

### 2.2 Interactive `TerminalView`

| Piece | Location |
|-------|----------|
| Init | `initTerminalView()` ~L12074 |
| Client | `TerminalViewClient`: `onSingleTapUp` → focus + IME; `readControlKey`/`readAltKey`/`readShiftKey` from `ModifierState`; `onCodePoint`/`onKeyDown` return **false** (let view handle) |
| Session client | Clipboard copy/paste wired; `onTextChanged` → `onScreenUpdated` |
| Resize | `forceTerminalResize` ~L18691 — `updateSize` + `Os.kill(pid, SIGWINCH)` when size changes |

### 2.3 Special-keys toolbar (ExtraKeys)

| Piece | Location |
|-------|----------|
| Builder | `buildSpecialKeysToolbar` ~L3484 |
| Inject | `injectKey(tv, key, ctrl, alt, shift)` ~L3278 |
| Mod state | `ModifierState` ~L486 — toggle + **long-press lock**; `readCtrl(true)` clears unless locked |
| Row 1 | CTRL · ALT · SHFT · ESC · TAB · ENT · BKSP — equal weight, **44dp** height, edge-to-edge |
| Row 2 | Horizontal scroll: arrows (icon drawables), DEL/Ins, symbols `/\|~-_\`, Fn |
| Row 3 | F1–F12 (toggle via Fn) |
| Key codes | `SPECIAL_KEY_CODES` map ~L3250 |
| Injection rule | Special → `tv.onKeyDown`/`onKeyUp` with meta; printable → `tv.inputCodePoint(cp, ctrl, alt)` |

### 2.4 Drawables / assets

| Asset | Path in nativecode | Flux status |
|-------|--------------------|-------------|
| Arrow / BKSP icons | `res/drawable/ic_arrow_*.xml`, `ic_backspace.xml` | **Copied** |
| CLI icons | `assets/images/cli/*.png` | **Interim:** `distro_debian.webp` / `distro_termux.webp` (untinted) — no `assets/images/cli/` yet |
| Font | `assets/fonts/font.ttf` | Already present |

---

## 3. FluxLinux current state (post-`685eebb`)

| Area | Status |
|------|--------|
| `TerminalScreen.kt` | Tabs + interactive `AndroidView(TerminalView)` + `TerminalExtraKeys` + selector bottom sheet |
| Empty state | **`TerminalToolSelector`** — 2-col grid, section headers, full-color icons, disabled reasons |
| ExtraKeys inject | **`TerminalKeyInjector.injectKey`** via `onKeyDown`/`onKeyUp` + `inputCodePoint` (old `session.write` path gone) |
| Modifiers | **`TerminalModifierState`** — toggle + long-press lock; Compose-driven UI |
| Soft keyboard | Tap → focus + `SHOW_IMPLICIT` with `SHOW_FORCED` fallback |
| Clipboard | `SessionRegistry.sessionClient` copy/paste wired |
| Host prepare | Toast on `openSessionAfterHost` failure (generic messages — polish open) |
| Host shell | `GuestSessionFactory.openHostShell` + HOST card |
| FGS | `AppTerminalService` on session count (unchanged) |
| Catalog | `TerminalShellCatalog` SSOT for proot / chroot / host cards |

---

## 4. Goals (acceptance)

1. **Terminal tab empty state** = scrollable **2-column card grid** with section headers, **full-color icons**, labels, short descriptions.  
2. Tap card → prepare host (if needed) → open session → **hide grid, show interactive terminal** + ExtraKeys.  
3. Soft keyboard **types into the PTY**; cursor visible; paste works.  
4. ExtraKeys: nativecode-style **2 rows (+ Fn row)**; inject via **`TerminalView.onKeyDown` / `inputCodePoint`**; Ctrl/Alt/Shift toggle + long-press lock.  
5. Resize: `updateSize` + SIGWINCH only on real size/pid (or cols/rows) change.  
6. Cards for **proot + chroot** when installed; disabled/gray with reason when not.  
7. Device smoke: open shell, type `uname -a`, Ctrl+C via toolbar, arrows work in readline/zsh.

Non-goals for this plan:

- AI CLI tool marketplace / opencode cards (nativecode Free/Paid)  
- Image attach / base64 inject (optional follow-up)  
- Customization theme extraction rework (separate, deferred)

---

## 5. Target UX (shipped)

### 5.1 Empty / selector (no active session)

```
Terminal                              [+]
────────────────────────────────────────
DEBIAN SHELL                    // PROOT
┌──────────────┐  ┌──────────────┐
│  [debian]    │  │  [debian]    │
│ Debian Shell │  │ Shell Rooted │
│ User: flux   │  │ User: root   │
└──────────────┘  └──────────────┘

DEBIAN SHELL                   // CHROOT
┌──────────────┐  ┌──────────────┐
│  [debian]    │  │  [debian]    │
│ Chroot Shell │  │ Chroot Root  │  (enabled only if chroot installed + root OK)
└──────────────┘  └──────────────┘

HOST                       // OPTIONAL
┌──────────────┐
│  [termux]    │
│ Host Shell   │
│ libbash      │
└──────────────┘
```

### 5.2 Active session

```
[tabs…]  [+]
┌─────────────────────────────┐
│      TerminalView (focus)   │
│      soft keyboard OK       │
└─────────────────────────────┘
[CTRL][ALT][SHFT][ESC][TAB][ENT][BKSP]
[←][↑][↓][→][DEL][Ins][/][|][~][-][_][\][Fn]
[F1…F12 optional]
```

Bottom nav pads terminal (`padding(bottom = 120.dp)`); ExtraKeys sit above nav.

---

## 6. Implementation plan — status

### PR-T1 — Shell catalog + icon assets — **DONE**

| Task | Result |
|------|--------|
| `TerminalShellCatalog` | `TerminalShellDef` / `TerminalShellCardUi` / `TerminalShellSection` / `availability` + `sections` |
| Cards | proot flux/root, chroot flux/root, host |
| Icons | Interim `R.drawable.distro_debian` / `distro_termux` (no tint) |
| Drawables | `ic_arrow_*`, `ic_backspace`, `ic_terminal`, `ic_terminal_thick` |
| Availability | FS checks + async `RootShell.probeRootAvailable` in UI |

**Files:** `core/terminal/TerminalShellCatalog.kt`, drawables.

### PR-T2 — Grid UI (Compose) — **DONE**

| Task | Result |
|------|--------|
| `TerminalToolSelector` | Section headers + 2-col `LazyVerticalGrid` |
| Card | 64dp icon, title, desc, disabled alpha + reason |
| On click | `openSessionAfterHost` / `openHostShell` with explicit method; toast on fail |
| `+` button | Same selector in `ModalBottomSheet` |

**Files:** `ui/terminal/TerminalToolSelector.kt`, `ui/screens/TerminalScreen.kt`.

### PR-T3 — Interactive TerminalView — **DONE** (device verify in T6)

| Task | Result |
|------|--------|
| Focus | `isFocusable` / touch mode / `requestFocus` on attach + switch |
| IME | `SHOW_IMPLICIT` → `SHOW_FORCED` fallback |
| Client returns | `onKeyDown` / `onKeyUp` / `onCodePoint` → **false** |
| Clipboard | `SessionRegistry` copy/paste (executor paste) |
| Re-attach | `attachView` + focus on update / switch |
| Failure UX | Toasts present; messages still coarse (see residual) |

**Files:** `TerminalScreen.kt`, `SessionRegistry.kt`.

### PR-T4 — ExtraKeys toolbar — **DONE**

| Task | Result |
|------|--------|
| `TerminalModifierState` | Toggle + lock + `consumeModifiers` |
| `TerminalKeyInjector` | `SPECIAL_KEY_CODES` + nativecode `injectKey` path |
| UI | Row1 44dp equal weight; Row2 scroll + icons; Fn → F1–F12 |
| Old path | `session.write` toolbar inject removed |

**Files:** `ui/terminal/TerminalExtraKeys.kt`, `TerminalScreen.kt`.

### PR-T5 — Resize / WINCH harden — **PARTIAL**

| Task | Result |
|------|--------|
| `forceTerminalResize` | Post to view; `updateSize` + SIGWINCH when running |
| Spam guard | Present, but keys **pixel width/height + pid**, not cols/rows (see residual R2) |
| Chroot WINCH trap | Still in `ChrootCommandBuilder.winchWrap` |

**Files:** `TerminalScreen.kt`.

### PR-T6 — Device smoke + polish — **OPEN**

| Check | Pass criteria | Status |
|-------|----------------|--------|
| Grid icons | Color icons visible; no solid-fill tint bug | Pending device |
| Open proot flux | Prompt, type works | Pending device |
| Open proot root | `whoami` → root | Pending device |
| Chroot cards | Disabled if missing/no su; work when installed + root | Pending (also R1) |
| ExtraKeys | Esc, Tab, arrows, Enter, Bksp, Ctrl+C | Pending device |
| Background | AppTerminalService FGS while session open | Pending device |
| Rotate / font pinch | Resize usable; WINCH after pinch (R2) | Pending |

---

## 7. File map (landed)

| Path | Role |
|------|------|
| `docs/plans/terminal-grid-extrakeys-interactive.md` | This plan |
| `ui/screens/TerminalScreen.kt` | Selector ↔ session ↔ ExtraKeys + WINCH |
| `ui/terminal/TerminalToolSelector.kt` | Grid UI |
| `ui/terminal/TerminalExtraKeys.kt` | Toolbar + inject + ModifierState |
| `core/terminal/TerminalShellCatalog.kt` | Card SSOT |
| `core/terminal/SessionRegistry.kt` | Clipboard + focus-friendly attach |
| `core/terminal/GuestSessionFactory.kt` | `openHostShell` |
| `core/terminal/FluxTerminalSessionManager.kt` | Host shell facade |
| `res/drawable/ic_arrow_*.xml`, `ic_backspace.xml`, `ic_terminal*.xml` | Toolbar icons |

---

## 8. Residual issues (from code review)

Fix these before treating the plan as fully closed. Prefer before or during T6.

| ID | Sev | File | Issue | Fix | Status |
|----|-----|------|-------|-----|--------|
| **R1** | bug | `TerminalShellCatalog.kt` | Chroot **always** uses su (`RootShell.shellRootCommand`). Only `shell-root` is gated on `rootAvailable`; **Chroot Shell (flux)** can show enabled without root and fail on open. | Require `rootAvailable` for **all** chroot cards; reason `"Root required"` when chroot installed but no su. | **FIXED** |
| **R2** | bug | `TerminalScreen.kt` `forceTerminalResize` | Comment says cols/rows/pid; implementation stores `tv.width`/`tv.height`. Pinch zoom changes emulator cols/rows without view size change → SIGWINCH may not fire. | Key spam guard on emulator cols/rows (or fontSize + size + pid); WINCH when cols/rows actually change. | **FIXED** |
| **R3** | bug (UX) | `TerminalScreen.kt` `openCard` | Failures (max tabs, open fail) all toast as `"Host bootstrap failed"` / `"Chroot session failed"`. | Differentiate prepare fail vs open fail vs tab limit (`SessionOpenResult`). | **FIXED** |
| **R4** | suggestion | Host open path | Host shell skips `prepareHost`; weak failure if bootstrap missing. | Probe `libbash` / prepare host; toast `"Host not ready"`. | **FIXED** |
| **R5** | suggestion | `TerminalExtraKeys` | Locked mods look same as one-shot active. | Distinct locked style (outline / badge). | **FIXED** |
| **R6** | nit | Icons | Shared Debian webp for all guest cards; no `assets/images/cli/`. | Optional distinct art later; non-blocking. |
| **R7** | bug (device) | `.zshrc` / ExtraKeys | (1) Hard `source oh-my-zsh` + bare `pokemon-colorscripts` spam when network install skipped. (2) Soft keyboard covered ExtraKeys toolbar. | **Fixed:** defensive zshrc in customization scripts + `GuestZshrcRepair` on session open; `imePadding` + `adjustResize` + drop nav pad while IME open. | Deferred (user: reuse Flux distro icons) |

---

## 9. Order of remaining work

```
R1 chroot root gate (all chroot cards)               ✓ FIXED
    → R2 WINCH key on cols/rows                       ✓ FIXED
        → R3/R4 failure toasts + host ready           ✓ FIXED
            → T6 device smoke (proot required; chroot if su)   OPEN
                → optional R5/R6 polish (R5 ✓ FIXED; R6 deferred)
```

**Do not** start customization theme extraction improvements until T6 proot smoke passes.

---

## 10. Risks

| Risk | Mitigation |
|------|------------|
| Soft keyboard broken on some OEMs | `SHOW_FORCED`; ensure `TerminalView` not under clickable overlay |
| Compose `AndroidView` recompose drops focus | Re-focus on session switch / attach |
| ExtraKeys too tall with bottom nav + Fn row | Cap height; scroll row2 only |
| Chroot without su looks openable | **R1** |
| Pinch zoom without WINCH | **R2** |

---

## 11. Done definition

- [x] Plan written  
- [x] T1–T5 implemented (`685eebb`)  
- [x] Residual R1–R5 fixed (R6 deferred — user: reuse Flux distro icons)  
- [ ] Device smoke T6 signed off for proot (required) and chroot (if device has root)  
- [ ] No regression to onboarding install runner / BaseInstallService FGS (verify on device)  

---

## 12. History

| When | What |
|------|------|
| 2026-08-09 | Plan only (T1–T6 design). |
| 2026-08-09 | Implemented T1–T5 in `685eebb`. |
| 2026-08-09 | Code review: residual R1–R6; plan status → **IMPLEMENTED (T1–T5)**. |
| 2026-08-09 | Fixed R1–R5 (chroot root gate, WINCH cols/rows, result toasts, host-ready probe, locked style). R6 deferred. Status → **IMPLEMENTED (T1–T5 + R1–R5)**. |
