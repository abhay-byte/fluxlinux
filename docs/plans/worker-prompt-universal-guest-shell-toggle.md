# Worker agent prompt — Universal bash/zsh guest login toggle

**Status: IMPLEMENTED (PR1–PR3, 2026-08-15).** Do **not** re-run the implement fence below. Residual-only follow-up is at the bottom of this file.

Plan SSOT: [`docs/plans/universal-guest-shell-toggle.md`](./universal-guest-shell-toggle.md) (implementation status + residuals).  
Code review: 0 bugs, 3 suggestions, 1 nit (comment noise). Device smoke: openSUSE proot bash/zsh + live tab; passwd still `/usr/bin/zsh`.

**Copy everything below the fence only if re-implementing from a tree that does not yet have `GuestLoginShell`.** Locked contracts in the plan win if this prompt and the tree ever disagree.

```
You are the FluxLinux worker for the universal guest login-shell toggle.

Repo:     /home/abhaybyte/repos/fluxlinux
Plan:     docs/plans/universal-guest-shell-toggle.md   ← SSOT, read it whole first
Package:  com.ivarna.fluxlinux
Flavor:   :app:assembleIvarnaRelease --no-daemon  (only if you device-smoke)
Signing:  keystore.properties → ~/repos/keys/fluxlinux.jks
Do not git push. Stop Gradle daemons when done. Do not implement extra features.

==============================================================================
MISSION
==============================================================================

Implement a SINGLE global Settings → Terminal bash/zsh Switch that every
interactive proot/chroot session opened from the Terminal page (and
MainActivity.onOpenTerminal) honors.

Today those sessions always land in zsh because:
  1. Callers omit shellCmd → default "exec zsh" (a SENTINEL, not a shell choice).
  2. ProotCommandBuilder.GUEST_LOGIN_SHELL always prefers /bin/zsh if present
     (true on every customized distro). Changing only the UI is a no-op.
  3. ChrootCommandBuilder.build hardcodes loginShellFlux = "zsh" and
     loginShellRoot = bash (Alpine path sh). The helper already supports
     login --shell bash and su -s.

Default stays zsh (opt-in bash). Launch-time exec / --shell / su -s.
Do NOT mutate /etc/passwd. Do NOT uninstall OMZ. Do NOT touch host/install/XFCE.

Implement PR1 → PR2 → PR3 in that order. Do not start UI before builders accept
loginShell. Run unit tests after each PR.

  ./gradlew :app:testIvarnaDebugUnitTest --no-daemon

==============================================================================
LOCKED CONTRACTS (do not invent alternatives)
==============================================================================

K6  Inject prefs ONLY in GuestSessionFactory.openSession. Never read
    TerminalPreferences inside LinuxCommandBuilder / Proot / Chroot builders
    (FakeContext has no SharedPreferences).

K13 Proot interactive check = isLoginSentinel ONLY.
    Chroot interactive check = isInteractiveLogin (sentinels OR workdir).
    Workdir-form "mkdir -p DIR && cd DIR && exec zsh|bash" stays a Proot
    /bin/sh -c PAYLOAD (mkdir/cd must not be dropped).

K14 buildRootInner String defaults stay loginShellFlux="zsh",
    loginShellRoot="bash". Keep interactiveRoot_loginBash as-is.

Product ChrootCommandBuilder.build(..., loginShell: GuestLoginShell? = null):
    loginShell == null  → today's defaults (PR2-safe, ZERO flip)
      flux → zsh
      root → bash, except chrootPath.contains("Alpine") → sh
    loginShell != null  → BOTH users get loginShell.chrootFlag

PR3 factory ALWAYS passes getGuestLoginShell() (ZSH|BASH, never null).
That is when chroot root flips to zsh if the pref is default zsh — and the
Settings switch is the escape hatch. Do not ship that flip in PR2.

GuestZshrcRepair.repairIfNeeded still runs when bash is selected.

==============================================================================
OUT OF SCOPE — do not touch
==============================================================================

- GuestSessionFactory.openHostShell / openHostShellAfterReady (libbash -l)
- openComponentSession, InstallSessionFactory, UninstallSessionFactory
- HomeScreen Qwen payload (echo '…' | base64 -d | bash) — not a sentinel
- TerminalScreen.openCard / MainActivity.onOpenTerminal signatures
- setup_customization_*.sh chsh, GuestZshrcRepair.ensureFluxLoginShellZsh
- fluxlinux_chroot.sh ensure_flux_zsh_profile, flux su -s cases,
  resolve_login_shell fallback order
- start_gui.sh / start_guest_gui.sh
- Per-distro prefs, live-PTY restart, tab-title suffix, Robolectric

==============================================================================
PHASE 1 — GuestLoginShell SSOT + prefs (inert)
==============================================================================

New file:
  app/src/main/kotlin/com/ivarna/fluxlinux/core/terminal/GuestLoginShell.kt

No Android imports. Exact API:

  enum class GuestLoginShell(val id: String) {
      ZSH("zsh"), BASH("bash");
      val chrootFlag: String get() = id
      companion object {
          val DEFAULT = ZSH
          const val INTERACTIVE_SENTINEL = "exec zsh"
          fun fromId(raw: String?): GuestLoginShell
              // "bash"/"BASH" → BASH; everything else (null, "", "zsh", "fish") → ZSH
          fun isLoginSentinel(shellCmd: String): Boolean
              // trim() first (intentional). true for "", "exec zsh", "exec bash",
              // "/bin/bash --login". false for workdir form and payloads.
          fun isInteractiveLogin(shellCmd: String): Boolean
              // isLoginSentinel || parseInteractiveWorkdir != null
          fun parseInteractiveWorkdir(shellCmd: String): String?
              // ^mkdir -p (.+) && cd \1 && exec (?:zsh|bash)$
              // reject empty dir or dir containing '
          fun prootLoginCascade(preferred: GuestLoginShell): String
              // /bin/sh -lc 'if [ -x /bin/$first ]; then exec /bin/$first -l; \
              //  elif [ -x /bin/$second ]; then exec /bin/$second -l; \
              //  else exec /bin/sh -l; fi'
              // first = preferred.id; second = the other of {bash,zsh}
              // ZSH cascade MUST be byte-equivalent to today's GUEST_LOGIN_SHELL
      }
  }

Extend:
  app/src/main/kotlin/com/ivarna/fluxlinux/core/utils/TerminalPreferences.kt

  private const val KEY_GUEST_LOGIN_SHELL = "guest_login_shell"
  fun getGuestLoginShell(context: Context): GuestLoginShell
  fun setGuestLoginShell(context: Context, shell: GuestLoginShell)
  fun preferZsh(context: Context): Boolean
  // SharedPreferences file stays flux_terminal_prefs.
  // Update file KDoc: font zoom + ExtraKeys + guest login shell.

Tests (new):
  app/src/test/java/com/ivarna/fluxlinux/core/terminal/GuestLoginShellTest.kt
  app/src/test/java/com/ivarna/fluxlinux/core/utils/TerminalPreferencesTest.kt

There is NO Robolectric. FakeContext does not implement getSharedPreferences.
For TerminalPreferencesTest, add a tiny in-memory SharedPreferences + a
FakePrefsContext (or extend FakeContext) that implements getApplicationContext()
and getSharedPreferences(name, mode). Do not add Robolectric.

GuestLoginShellTest matrix:
  fromId(null/""/"zsh"/"ZSH") → ZSH
  fromId("bash"/"BASH") → BASH
  fromId("fish") → ZSH
  isLoginSentinel("") / "exec zsh" / "exec bash" / "/bin/bash --login"
      / " exec zsh " (trim) → true
  isLoginSentinel("echo hi") → false
  isLoginSentinel("mkdir -p /home/flux/p && cd /home/flux/p && exec zsh") → FALSE
  isInteractiveLogin(that workdir form) → TRUE
  isInteractiveLogin("echo hi") → false
  Qwen-shaped "echo 'x' | base64 -d | bash" → both predicates FALSE
  parseInteractiveWorkdir(... exec zsh) → /home/flux/p
  same with exec bash → /home/flux/p
  quote in path / non-matching → null
  prootLoginCascade(ZSH) contains "if [ -x /bin/zsh ]" BEFORE "/bin/bash"
  prootLoginCascade(BASH) contains "if [ -x /bin/bash ]" BEFORE "/bin/zsh"
      and still ends with exec /bin/sh -l

TerminalPreferencesTest:
  unset → ZSH
  set BASH → get == BASH
  set ZSH → ZSH
  garbage stored string → ZSH

No builder or UI changes in this phase.

==============================================================================
PHASE 2 — Builders + helper fail-open (ZERO product default change)
==============================================================================

ProotCommandBuilder.kt
  - buildArgs(..., loginShell: GuestLoginShell = DEFAULT)
  - Interactive check = GuestLoginShell.isLoginSentinel(shellCmd)
    NEVER isInteractiveLogin.
  - Interactive argv uses prootLoginCascade(loginShell)
  - Change `const val GUEST_LOGIN_SHELL` to a `val` alias of
    prootLoginCascade(DEFAULT). Grep leftover refs first.
  - build() threads loginShell. Does NOT read prefs.
  - Non-interactive /bin/sh -c UNCHANGED (workdir form stays here).
  - "exec bash" is NOW a sentinel (today it is a payload) — add a unit test.

ChrootCommandBuilder.kt
  - build(..., loginShell: GuestLoginShell? = null) with the locked mapping above.
  - Keep isAlpine ONLY for the loginShell == null root mapping.
  - Rewrite the stale "Always request zsh for flux" / File.exists() comments to:
    "shell comes from loginShell; helper resolves the real binary as root
     (app uid cannot stat /data/local/tmp)."
  - Do NOT change buildRootInner String defaults.
  - buildRootInner: isInteractiveLogin + GuestLoginShell.parseInteractiveWorkdir.
    Delete the private parseInteractiveWorkdir.

LinuxCommandBuilder.build(..., loginShell: GuestLoginShell? = null)
  Forward to both builders. Null = legacy product defaults.
  Proot may coerce loginShell ?: GuestLoginShell.DEFAULT (zsh-first is already
  today's proot default).

fluxlinux_chroot.sh + ChrootPaths.kt  (SAME change set)
  Replace the ENTIRE root) case "$LOGIN_SHELL" in guest_login with this drop-in.
  Do not change resolve_login_shell order or flux su -s cases.

    root)
      case "$LOGIN_SHELL" in
        zsh)
          if [ -n "$_cd" ]; then
            guest_chroot_env /bin/zsh -c "${_cd}exec /bin/zsh -l"
          else
            guest_chroot_env /bin/zsh -l
          fi
          ;;
        sh|ash)
          # Alpine/Chimera minirootfs — resolver may return sh when bash/zsh missing
          if [ -n "$_cd" ]; then
            guest_chroot_env /bin/sh -c "${_cd}exec /bin/sh -l"
          else
            guest_chroot_env /bin/sh -l
          fi
          ;;
        bash|*)
          if [ -n "$_cd" ]; then
            guest_chroot_env /bin/bash --login -c "${_cd}exec /bin/bash --login"
          else
            guest_chroot_env /bin/bash --login
          fi
          ;;
      esac
      ;;

  Bump header + VERSION_STR + ChrootPaths.CHROOT_HELPER_VERSION to
  "fluxlinux-chroot v2.5" together.

CHANGELOG.md (PR2 only): helper v2.5 fail-open. Do NOT claim a chroot-root
default flip here.

Tests — keep:
  ChrootCommandBuilderTest.interactiveFlux_loginZsh
  ChrootCommandBuilderTest.interactiveRoot_loginBash   ← still --shell bash
  ChrootCommandBuilderTest.interactiveAlpine_loginSh
  existing workdir exec-zsh, simple/b64/winch/env

Tests — add:
  Proot: loginShell=BASH + shellCmd="exec zsh" → bash-first cascade
  Proot: shellCmd="exec bash" → interactive + default zsh cascade
  Proot: Qwen-shaped payload → /bin/sh -c, no cascade
  Proot: workdir-form → /bin/sh -c still contains mkdir and cd
  Proot: blank shellCmd → interactive + zsh-first cascade
  Proot: existing debian/alpine exec zsh still login + env -i + zsh-first
  Chroot: interactiveRoot_explicitZshPref
      buildRootInner("", root, loginShellRoot = DEFAULT.chrootFlag) → --shell zsh
  Chroot: interactiveFlux_loginBash → --shell bash
  Chroot: workdir exec bash → --workdir + --shell from params
  LinuxCommandBuilder (withSeededSu):
      build(..., user=root, method=chroot) NO loginShell → --shell bash
      build(..., user=root, method=chroot, loginShell=ZSH) → --shell zsh
      build(..., user=root, method=chroot, loginShell=BASH) → --shell bash
      build(..., user=flux, method=chroot) NO loginShell → --shell zsh
      build(..., user=flux, method=chroot, loginShell=BASH) → --shell bash
  ChrootPathsTest ALWAYS:
      assertEquals("fluxlinux-chroot v2.5", ChrootPaths.CHROOT_HELPER_VERSION)

After Phase 2, product UI still unused. Opening a Terminal card must behave
exactly as today (flux zsh, chroot root bash, Alpine root sh).

==============================================================================
PHASE 3 — Factory injection + Settings toggle
==============================================================================

GuestSessionFactory.openSession
  After GuestZshrcRepair + GuestApkDbRepair (KEEP both, even if bash):
    val loginShell = TerminalPreferences.getGuestLoginShell(ctx)  // ZSH|BASH
    LinuxCommandBuilder.build(..., loginShell = loginShell)
  Log.d("GuestSessionFactory", "loginShell=$id method=$method distroId=…")
  KDoc: default shellCmd "exec zsh" means interactive login; binary from prefs.
  openHostShell / openComponentSession UNCHANGED.

FluxTerminalSessionManager: KDoc only. No new parameters.

TerminalSettingsScreen — third GlassSettingCard AFTER ExtraKeys.
Same Row + Column(weight=1) + Switch as ExtraKeys. ONE title + ONE subtitle.
Live word sits NEXT TO the Switch, not under the title.

  Title:    "Guest login shell"          // 16sp Bold, ExtraKeys title color
  Subtitle: "On = zsh, off = bash. New proot/chroot Terminal sessions only; live tabs keep their shell."
            // 12sp, onSurface 65% alpha
  Live word next to Switch: "zsh" (checked) / "bash" (unchecked)
  Switch checked = (shell == GuestLoginShell.ZSH)
  onCheckedChange true  → setGuestLoginShell(ZSH)
  onCheckedChange false → setGuestLoginShell(BASH)
  contentDescription: "Guest login shell: zsh" or "Guest login shell: bash"
  state: remember { TerminalPreferences.preferZsh(context) }

No live-session restart. No snackbar.

SettingsScreen.kt Terminal nav card subtitle:
  "Font zoom, extra keys, and guest shell"

CHANGELOG.md Features: guest login shell toggle; note that chroot root now
follows the pref (default zsh) with the Settings escape hatch.

Do not unit-test the Compose Switch or GuestSessionFactory (needs TerminalSession).
Factory wiring is covered by prefs + builders + the one-line pass-through.

==============================================================================
ACCEPTANCE
==============================================================================

Unit: :app:testIvarnaDebugUnitTest green, including every case above.

If a device is attached (optional, do not block on missing adb):
  1. Fresh: Settings → Terminal switch ON (zsh). Debian Shell still zsh
     (OMZ prompt if customized).
  2. Switch OFF: NEW Debian proot flux tab → echo $0 is bash.
     Existing zsh tab unchanged.
  3. Switch ON: new tab is zsh again.
  4. Same pref on a chroot flux card AND a chroot root card (if root granted).
  5. Host card still libbash. Qwen / component / install unchanged.
  6. Guest /etc/passwd flux line still /bin/zsh after toggling to bash.
  7. Alpine minirootfs (no zsh, no bash): session still opens on sh.

==============================================================================
STOP CONDITIONS
==============================================================================

- Do not start Phase 3 UI before Phase 2 tests are green.
- Do not merge a silent chroot-root bash→zsh flip without the Settings switch.
- Do not read prefs in builders.
- Do not collapse isLoginSentinel into isInteractiveLogin.
- Stop after tests are green and CHANGELOG is updated. Do not implement
  per-distro prefs, live-tab restart, or host-shell toggle.
```

---

## Residual follow-up (optional — do not re-implement the toggle)

PR1–PR3 already landed. Local review found **0 bugs**. If picking this up again, only:

**R1 — comment cleanup (suggestions / nit)**

- `ChrootCommandBuilder.build`: drop the “Product mapping (locked) / PR2-safe” table. Keep only: *shell comes from `loginShell`; helper resolves the real binary as root (app uid cannot stat `/data/local/tmp`)*.
- `GuestLoginShell.isLoginSentinel`: replace “today’s `==` did not” with *trim so padded sentinels still count*.
- `ProotCommandBuilder.GUEST_LOGIN_SHELL`: unused alias. Delete it, or keep a one-line “zsh-first cascade (default pref)” KDoc with no back-compat coaching.
- `ProotCommandBuilderTest.execBashSentinel_isInteractiveWithDefaultZshCascade`: delete the “NOW a sentinel” comment.

**R2 — remaining device smoke (optional)**

- Debian proot flux + one chroot flux/root pair (acceptance 4).
- Alpine minirootfs still opens on `sh` (acceptance 7).
- Worker already passed openSUSE proot bash/zsh + live-tab + passwd `/usr/bin/zsh`.

**R3 — Fedora chroot flux (fixed in helper v2.6)**

Do not re-hardcode `/bin/su`. Flux login is `guest_login_user`: runuser → su → busybox `chroot --userspec`. Fedora family installs `util-linux`. Existing Fedora chroots work after the helper restages (open any chroot tab).

Do not change builders, prefs, or Settings copy unless R1/R2 require it.