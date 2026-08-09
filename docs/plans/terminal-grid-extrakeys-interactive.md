# Plan: Terminal page grid + ExtraKeys + interactive shell (nativecode-ai parity)

**Date:** 2026-08-09  
**Branch:** `feat/embedded-terminal-bootstrap-proot-chroot` (or follow-up)  
**Status:** **PLAN ONLY** — do not implement until approved  
**Refs:** `~/repos/nativecode-ai` (SSOT for UX), Flux `TerminalScreen.kt`, `SessionRegistry.kt`, `GuestSessionFactory.kt`

---

## 1. Problem (device)

| Symptom | Observed / likely cause |
|---------|-------------------------|
| Terminal page is **not a grid with original icons** | Empty state is a vertical list of Material buttons + generic `Icons.Default.Terminal` |
| Terminal is **not interactive** | Soft keyboard / ExtraKeys / focus path incomplete vs nativecode; key injection uses `session.write` + `dispatchKeyEvent` instead of `TerminalView.onKeyDown` / `inputCodePoint` |
| ExtraKeys feel incomplete | Single thin Compose row; no ENT/BKSP, no lock-on-long-press mods, no F-keys, no icon arrows; wrong inject path |
| Session open UX | No toast on host prepare failure; no host shell card; chroot gated only by installed rootfs |

User priority: **do these first** (before further customization-script polish).

---

## 2. nativecode-ai reference (what to port)

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

Nativecode also filters AI tools by method; **Flux v1** should ship **system shells only** (+ optional Host shell), not AI CLI marketplace.

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
| Row 1 | CTRL · ALT · SHFT · ESC · TAB · ENT · BKSP · (attach optional) — equal weight, **44dp** height, edge-to-edge |
| Row 2 | Horizontal scroll: arrows (icon drawables), DEL/Ins, symbols `/\|~-_\`, Fn |
| Row 3 | F1–F12 (toggle via Fn) |
| Key codes | `SPECIAL_KEY_CODES` map ~L3250 |
| Injection rule | Special → `tv.onKeyDown`/`onKeyUp` with meta; printable → `tv.inputCodePoint(cp, ctrl, alt)` |

Docs (nativecode):  
`docs/plan-special-keys-toolbar.md`, `docs/terminal-row2-button-resize.md`, `docs/plan/terminal-debian-shell-rooted.md`, `docs/plan/terminal-workspace-tool-sections.md`

### 2.4 Drawables / assets to reuse

| Asset | Path in nativecode |
|-------|--------------------|
| Arrow / BKSP icons | `res/drawable/ic_arrow_{left,up,down,right}.xml`, `ic_backspace.xml`, `ic_attach_image.xml`, `ic_terminal*.xml` |
| CLI icons | `assets/images/cli/*.png` (repo currently only has `qwen-code.webp` checked in — recover `shell.png` from release/history or design Debian-branded substitutes) |
| Font | `assets/fonts/font.ttf` (Flux already has this) |

---

## 3. FluxLinux current state

| Area | Status |
|------|--------|
| `TerminalScreen.kt` | Tabs + `AndroidView(TerminalView)` + thin `ExtraKeysBar` |
| Empty state | 3 `Button`s (proot flux / proot root / chroot) — **no grid, no icons** |
| ExtraKeys inject | `session.write("\u001b…")` / `dispatchKeyEvent` — **not nativecode path** |
| Modifiers | `AtomicBoolean` one-shot latch (no lock, no long-press) |
| Soft keyboard | `onSingleTapUp` shows IME; focus flags partial |
| Clipboard | Session client: copy/paste **no-ops** |
| Host prepare | `openSessionAfterHost` silent on failure |
| FGS | `AppTerminalService` already on session count |
| Session factories | `GuestSessionFactory` + `LinuxCommandBuilder.sessionUserForType` already support `shell` / `shell-root` + method |

---

## 4. Goals (acceptance)

1. **Terminal tab empty state** = scrollable **2-column card grid** with section headers, **original/full-color icons**, labels, short descriptions.  
2. Tap card → prepare host (if needed) → open session → **hide grid, show interactive terminal** + ExtraKeys.  
3. Soft keyboard **types into the PTY**; cursor visible; paste works.  
4. ExtraKeys: nativecode-style **2 rows (+ Fn row)**; inject via **`TerminalView.onKeyDown` / `inputCodePoint`**; Ctrl/Alt/Shift toggle + long-press lock.  
5. Resize: `updateSize` + SIGWINCH only on real size/pid change (already partially done).  
6. Cards for **proot + chroot** when installed; disabled/gray with reason when not.  
7. Device smoke: open shell, type `uname -a`, Ctrl+C via toolbar, arrows work in readline/zsh.

Non-goals for this plan:

- AI CLI tool marketplace / opencode cards (nativecode Free/Paid)  
- Image attach / base64 inject (optional follow-up)  
- Customization theme extraction rework (separate, deferred)

---

## 5. Target UX

### 5.1 Empty / selector (no active session)

```
Terminal                              [+]
────────────────────────────────────────
DEBIAN SHELL                    // PROOT
┌──────────────┐  ┌──────────────┐
│  [shell.png] │  │  [shell.png] │
│ Debian Shell │  │ Shell Rooted │
│ User: flux   │  │ User: root   │
└──────────────┘  └──────────────┘

DEBIAN SHELL                   // CHROOT
┌──────────────┐  ┌──────────────┐
│  [debian]    │  │  [debian]    │
│ Chroot Shell │  │ Chroot Root  │  (enabled only if chroot installed + root OK)
└──────────────┘  └──────────────┘

HOST (optional)
┌──────────────┐
│  [host icon] │
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

Bottom nav already pads terminal (`padding(bottom = 120.dp)`); keep ExtraKeys above nav, not under it.

---

## 6. Implementation plan

### PR-T1 — Shell catalog + icon assets

**Goal:** SSOT for cards + icons.

| Task | Detail |
|------|--------|
| Add `TerminalShellCatalog` (or reuse small data class) | `type`, `label`, `desc`, `method` (`proot`/`chroot`/`host`), `iconAsset` / `iconRes` |
| Cards | proot flux, proot root, chroot flux, chroot root; optional host |
| Assets | Place full-color icons under `app/src/main/assets/images/cli/` — recover `shell.png` from nativecode release/history if missing; else use `distro_debian.webp` + monochrome host icon fallback |
| Copy useful drawables from nativecode | `ic_arrow_*`, `ic_backspace`, `ic_terminal_thick` (if not present) |
| Availability | `isProotInstalled`, `isChrootInstalled`, `RootShell` probe for chroot-root |

**Files:** new `TerminalShellCatalog.kt`, assets, drawables.

### PR-T2 — Grid UI (Compose)

**Goal:** Replace button column with nativecode-style sections.

| Task | Detail |
|------|--------|
| `TerminalToolSelector` composable | Section header + 2-col `LazyVerticalGrid` or chunked `Row`s |
| Card | 64dp icon (Bitmap from assets, **no tint** on color PNGs), title, desc, disabled alpha |
| On click | `FluxTerminalSessionManager.openSessionAfterHost(...)` with explicit `method` + type; Toast on failure |
| `+` button | Opens same selector as bottom sheet **or** defaults to proot flux if grid is empty-state only |

**Files:** `TerminalScreen.kt` (or `ui/terminal/TerminalToolSelector.kt`).

### PR-T3 — Interactive TerminalView (fix non-interactive)

**Goal:** Keyboard + focus parity with nativecode `initTerminalView`.

| Task | Detail |
|------|--------|
| Focus | After attach: `isFocusable = true`, `isFocusableInTouchMode = true`, `requestFocus()` |
| IME | `onSingleTapUp` → `SHOW_IMPLICIT` (fallback `SHOW_FORCED` on OEM fails) |
| Client returns | `onKeyDown`/`onKeyUp`/`onCodePoint` → **false** (do not swallow) |
| Clipboard | Wire `SessionRegistry.sessionClient` copy/paste like nativecode (`ClipboardManager` + `session.emulator.paste` / `write`) |
| Re-attach on revision | When `revision` / `activeIndex` changes, re-`attachSession` + focus (avoid “blank / dead” view after open) |
| Failure UX | Toast “Host bootstrap failed” / “Root not available” / “Chroot not installed” |
| Nested scroll | Ensure ExtraKeys / bottom nav do not steal `TerminalView` touches; test with haze scaffold |

**Files:** `TerminalScreen.kt`, `SessionRegistry.kt`.

### PR-T4 — ExtraKeys toolbar (nativecode inject path)

**Goal:** Full toolbar; correct injection.

| Task | Detail |
|------|--------|
| Extract `ModifierState` | Toggle + lock + `onStateChanged` for UI refresh |
| Extract `injectKey` | Port `SPECIAL_KEY_CODES` + `onKeyDown`/`inputCodePoint` |
| UI | Compose 2-row bar (Row1 equal weight 44dp; Row2 scroll + icon arrows; Fn→F-keys) |
| Replace `writeKey` | Delete ESC/arrow `session.write` path for toolbar |
| Optional | Image attach **out of scope** for v1 (leave slot if layout needs 8 cells) |

**Files:** new `ui/terminal/TerminalExtraKeys.kt`, `TerminalScreen.kt`.

### PR-T5 — Resize / WINCH harden

| Task | Detail |
|------|--------|
| Port `forceTerminalResize` pattern | Post to view when size > 0; kill SIGWINCH on session pid |
| Keep spam guard | Only when cols/rows/pid change (existing `lastWinchKey`) |
| Chroot | Confirm chroot session exec still forwards WINCH (existing host script trap) |

**Files:** `TerminalScreen.kt`.

### PR-T6 — Device smoke + polish

| Check | Pass criteria |
|-------|----------------|
| Grid icons | Color icons visible; no solid-fill tint bug |
| Open proot flux | Prompt, type works |
| Open proot root | `whoami` → root |
| Chroot cards | Disabled if missing; work when installed |
| ExtraKeys | Esc, Tab, arrows, Enter, Bksp, Ctrl+C |
| Background | AppTerminalService FGS while session open |
| Rotate / font pinch | Resize still usable |

---

## 7. Suggested file map

| New / change | Role |
|--------------|------|
| `docs/plans/terminal-grid-extrakeys-interactive.md` | This plan |
| `ui/screens/TerminalScreen.kt` | Wire selector ↔ session ↔ ExtraKeys |
| `ui/terminal/TerminalToolSelector.kt` | Grid UI |
| `ui/terminal/TerminalExtraKeys.kt` | Toolbar + inject + ModifierState |
| `core/terminal/TerminalShellCatalog.kt` | Card SSOT |
| `core/terminal/SessionRegistry.kt` | Clipboard + focus-friendly attach |
| `assets/images/cli/*` | Original icons |
| `res/drawable/ic_arrow_*.xml`, `ic_backspace.xml` | Toolbar icons (from nativecode) |

---

## 8. Order of work

```
T1 assets + catalog
    → T2 grid UI
        → T3 interactive fix (critical path)
            → T4 ExtraKeys
                → T5 resize harden
                    → T6 device smoke
```

**Do not** start customization theme extraction improvements until T3–T4 pass on device.

---

## 9. Risks

| Risk | Mitigation |
|------|------------|
| Missing `shell.png` in nativecode tree | Design/export icons; interim `distro_debian.webp` |
| Soft keyboard still broken on some OEMs | `SHOW_FORCED`, ensure `TerminalView` not under clickable overlay |
| Compose `AndroidView` recompose drops focus | Stable keys; re-focus on session switch |
| Chroot root needs su grant | Probe + clear error card |
| ExtraKeys too tall with bottom nav | Cap toolbar height; scroll row2 only |

---

## 10. Done definition

- [ ] Plan approved  
- [ ] T1–T5 merged  
- [ ] Device smoke T6 signed off for proot (required) and chroot (if device has root)  
- [ ] No regression to onboarding install runner / BaseInstallService FGS  

---

## 11. Stop

**This document is planning only.** No code changes beyond this plan file and `docs/plans/README.md` index update until user asks to implement.
