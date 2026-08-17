# Plan: Host PulseAudio + guest audio on every PRoot and chroot distro

| Field | Value |
| --- | --- |
| **Author** | FluxLinux |
| **Date** | 2026-08-16 |
| **Status** | **COMPLETE** — see [results/pulseaudio-device-report.md](results/pulseaudio-device-report.md) |
| **Repo** | `/home/abhaybyte/repos/fluxlinux` |
| **Audience** | Implementers of host bootstrap + desktop launch + guest family scripts |
| **Flavor / package** | Ivarna (`com.ivarna.fluxlinux`) first; zenithblue bootstrap must get the same package-list / overlay fix |
| **APK policy** | `:app:assembleIvarnaRelease` + `adb install -r` only. Do **not** uninstall the existing APK. Do **not** wipe `usr/var/lib/proot-distro` or `/data/local/tmp/chroot*`. |
| **Device used for this plan** | `Y5WWBMJVOZSK4HU8` · Xiaomi 2311DRK48I (duchamp) · Android 16 · uid `10303` / `u0_a303` · SELinux `u:r:untrusted_app:s0:c47,c257,c512,c768` |
| **Scope lock** | Audio only. Do not reopen X11/VirGL, W^X loaders, guest-shell toggle, or storage settings. |

**How to use this file:** this is the implementation contract. PulseAudio is a **host** service that must run in the **embedded PREFIX as the app uid**. Guests are **clients** (`PULSE_SERVER=tcp:127.0.0.1`). The point of failure on the current Ivarna release is **not** “forgot to set `PULSE_SERVER` in one distro” — the host daemon cannot even `dlopen`. After the host is startable, most guests still have no Pulse client (`pactl` / `libpulse`) because family XFCE lists are desktop-only.

**Docs this plan implements:** [`docs/CHROOT_HARDWARE_ACCEL.md`](../CHROOT_HARDWARE_ACCEL.md), [`docs/roadmap.md`](../roadmap.md) (audio forwarding), [`docs/termux/native_gui_research.md`](../termux/native_gui_research.md), [`docs/scripts_reference.md`](../scripts_reference.md). Those docs still say “Termux”. The runtime is now the in-app PREFIX (`TermuxHostPaths`), not `com.termux`. The protocol (host Pulse + TCP + guest `PULSE_SERVER`) is unchanged.

**Do not implement in the planning session.** Device probes below are evidence only.

---

## 1. What “done” means

Audio works the same way on **every** installed distro, **PRoot and chroot**, from both the **native host shell** and a **guest login / XFCE session**:

1. Host `pulseaudio` is running as the FluxLinux app uid (never root, never inside the guest).
2. Default sink is a real Android output (`AAudio_sink` on this device; SLES if AAudio is missing).
3. TCP native protocol listens on **127.0.0.1:4713** only (`auth-anonymous=1`, `auth-ip-acl=127.0.0.1`).
4. Guest env always has `PULSE_SERVER=tcp:127.0.0.1` (builders, chroot helper, profile.d, GUI wrappers).
5. Guest has a Pulse **client** (`libpulse` + `pactl`/`paplay`) and does **not** autospawn a guest daemon / PipeWire-Pulse socket.
6. `pactl info` from native, PRoot, and chroot shows the host server and a non-null sink.
7. A short tone (`module-sine` or `paplay`) is audible from native and from at least one PRoot + one chroot guest.
8. Stop-desktop does **not** tear down the host Pulse daemon (CLI audio stays up). Host Pulse is independent of XFCE.
9. Existing PRoot containers and all 12 chroots survive the host-side fix (no bootstrap wipe).
10. Settings → **Audio** shows host Pulse running/stopped, Start or Restart, and supervisor logs (does not start Pulse as root or inside a guest).

---

## 2. Architecture (target)

```
┌─────────────────────────────────────────────────────────────────┐
│ FluxLinux app uid (u0_a303)  —  $filesDir/usr  = PREFIX         │
│                                                                 │
│  pulseaudio  (user daemon, --exit-idle-time=-1)                 │
│    ├─ module-aaudio-sink     → Android speaker  (primary)       │
│    ├─ module-sles-sink       → fallback if AAudio missing       │
│    ├─ module-native-protocol-unix  (host pactl only)            │
│    └─ module-native-protocol-tcp listen=127.0.0.1               │
│         auth-ip-acl=127.0.0.1 auth-anonymous=1                  │
│                                                                 │
│  start_pulse_host.sh   ← SSOT; called by prepareHost / GUI      │
│  default.pa            ← aaudio first; tcp; no .fail on sink    │
└───────────────────────────────┬─────────────────────────────────┘
                                │ TCP 127.0.0.1:4713
          ┌─────────────────────┴─────────────────────┐
          ▼                                           ▼
  PRoot guest (env -i)                         chroot guest (env -i)
  PULSE_SERVER=tcp:127.0.0.1                   PULSE_SERVER=tcp:127.0.0.1
  libpulse + pactl + xfce plugin               same
  autospawn=no                                 autospawn=no
  no pipewire-pulse socket                     no pipewire-pulse socket
```

This matches [`docs/CHROOT_HARDWARE_ACCEL.md`](../CHROOT_HARDWARE_ACCEL.md): the server **must** start in the app/Termux-class uid. Root cannot use the app `XDG_RUNTIME_DIR`, and a guest Pulse/PipeWire has no Android OpenSL/AAudio.

Unix sockets are the wrong guest transport. Chroot `XDG_RUNTIME_DIR=/tmp` is often root-owned (`pactl` then refuses the native socket). TCP loopback avoids that. Device probe: Debian 13 / Fedora / Arch / Manjaro chroots connected over `tcp:127.0.0.1` once the host daemon was up.

---

## 3. Device evidence (2026-08-16) — point of failure

All probes on `com.ivarna.fluxlinux` (release, not debuggable) via `adb` + KernelSU. Native commands used `nsenter -t <app-pid> -m` + `runcon u:r:untrusted_app:…` + `setuidgid 10303` so the linker namespace and uid match a real in-app shell.

### 3.1 Layer 0 — host daemon does not start (primary break, all distros)

| Check | Result |
| --- | --- |
| `usr/bin/pulseaudio` present | yes (72 304 bytes, RUNPATH = `$PREFIX/lib/pulseaudio:$PREFIX/lib`) |
| process before probe | **none** |
| `setup_termux.sh` gate | only `command -v pulseaudio` — **false green** |
| `start_gui.sh` / `start_gui_chroot.sh` | start Pulse, then `2>/dev/null` / `\|\| true` — **silent fail** |
| Native terminal / `HostCommandBuilder` | **never starts Pulse**, never sets `PULSE_SERVER` |
| `verify_bootstrap.sh` | only checks `usr/bin/pulseaudio` exists |

Exact start as app uid (same argv as `start_gui.sh`, stderr not swallowed):

```
CANNOT LINK EXECUTABLE "pulseaudio": library "libsoxr.so" not found
  needed by .../usr/lib/pulseaudio/libpulsecore-17.0.so
CANNOT LINK EXECUTABLE "pactl": library "libandroid-execinfo.so" not found
  needed by .../usr/lib/pulseaudio/libpulsecommon-17.0.so
```

After dropping in `libsoxr.so` the next missing lib was `libFLAC.so` (via `libsndfile.so`), then `libmp3lame.so`.

Recursive `readelf -d NEEDED` of the **local** extracted bootstrap root (`native/bootstrap/com.ivarna.fluxlinux/root/…/usr`) vs `bootstrap.tar`:

| Missing `.so` | Needed by | Deb already in `native/output/com.ivarna.fluxlinux/` | In SSOT / `bootstrap.tar`? |
| --- | --- | --- | --- |
| `libsoxr.so` | `libpulsecore-17.0.so` | `libsoxr_0.1.3-8_aarch64.deb` | **no** (never listed) |
| `libandroid-execinfo.so` | `libpulsecommon-17.0.so` | `libandroid-execinfo_0.1-3_aarch64.deb` | **no** (never listed) |
| `libFLAC.so` | `libsndfile.so` | `libflac_1.5.0-1_aarch64.deb` | **no** — list has `flac` (CLI `usr/bin/flac` only) |
| `libmp3lame.so` | `libsndfile.so` | `libmp3lame_3.100-7_aarch64.deb` | **no** |

So: Pulse **binary** was packaged; its **runtime graph** was not. The older SSOT audio chain (`libogg`, `flac`, `libvorbis`, `libopus`, `speexdsp`, `libsndfile`, …) copied termux-lib and assumed `flac` provided `libFLAC.so`. It does not.

This is why **every** distro is silent: guests talk to `tcp:127.0.0.1` and there is no server. Already reported as BUG-4 in [`docs/plans/results/deepin-proot-device-report.md`](./results/deepin-proot-device-report.md) (then mis-attributed to a missing `usr/bin/sh`).

### 3.2 Layer 1 — even after libs, `default.pa` picks a dummy sink

With the four `.so` files copied into PREFIX (diagnostic only; **not** a shippable fix):

| Step | Result |
| --- | --- |
| `pulseaudio --start --exit-idle-time=-1` | **starts** as uid 10303, `untrusted_app` |
| `pactl info` default sink | **`auto_null`** (module-always-sink) |
| `load-module module-sles-sink` | **FAIL** — `module.c: Failed to open module "module-sles-sink"` |
| `load-module module-aaudio-sink` | **OK** → default sink `AAudio_sink` (48 kHz) |
| `load-module module-native-protocol-tcp auth-ip-acl=127.0.0.1 auth-anonymous=1` | **OK**, module index 16 |
| listen | **`0.0.0.0:4713` and `[::]:4713`** (ACL is not a bind address) |
| `PULSE_SERVER=tcp:127.0.0.1 pactl info` (host) | **OK** |
| `module-sine frequency=440` for 2 s | module loaded (audible if the speaker is unmuted) |

`default.pa` currently ends with:

```
load-module module-sles-sink
#load-module module-aaudio-sink
```

On Android 16 / HyperOS, SLES does not open. `.fail` is cleared by `.nofail` before the include, so Pulse keeps running on **Dummy Output**. GUI scripts never load AAudio. Even a “successful” start after the linker fix would still be silent.

`pactl info` reports `User Name: root` even though the process is uid 10303 — `getpwuid(10303)` has no passwd entry. Harmless. Do not “fix” this by running `--system`.

### 3.3 Layer 2 — native / guest launch never guarantees a server

| Path | Starts host Pulse? | Sets guest `PULSE_SERVER`? |
| --- | --- | --- |
| Native Flux Terminal (`HostCommandBuilder.envMap`) | no | no |
| PRoot login (`ProotCommandBuilder.guestLoginEnv`) | no | **no** — `env -i` HOME/USER/TERM/TMPDIR/PATH only |
| Chroot login (`fluxlinux_chroot.sh` `build_guest_env_args`) | no | **no** — same `env -i` set |
| `ChrootCommandBuilder.buildEnv` (outer Android sh) | no | no |
| `start_gui.sh` / `start_gui_chroot.sh` | yes, errors swallowed | yes, inside the DE wrapper (`tcp:127.0.0.1`) |
| Family `~/.vnc/xstartup` | no | `127.0.0.1` (VNC only) |
| `stop_gui.sh` / `stop_gui_chroot.sh` | **kills** Pulse | — |
| `setup_termux.sh` | binary exists? only | — |
| `TermuxIntentFactory` (legacy Termux) | yes (old `com.termux` PREFIX) | n/a — not the product path |

openSUSE PRoot `etc/profile.d/termux-profile.sh` (injected by proot-distro) does `export PULSE_SERVER='127.0.0.1'`, but only if the guest sources `profile.d`. `env -i` logins used by the in-app terminal **do not**.

### 3.4 Layer 3 — most guests have no Pulse client

`FLUX_CHROOT=… sh fluxlinux_chroot.sh sh --user flux` and `proot-distro login opensuse --user flux -- env -i …` (2026-08-16). Host Pulse was running with AAudio + TCP for the connect column.

| Guest | `pactl` | `libpulse` | XFCE pulse plugin | Guest `pulseaudio` server | `pipewire` | `PULSE_SERVER=tcp:127.0.0.1 pactl info` |
| --- | --- | --- | --- | --- | --- | --- |
| **opensuse PRoot** | none | none | none | none | none | n/a (no client) |
| debian13 chroot | yes | yes | **yes** | **yes** (`/usr/bin/pulseaudio`) | none | **OK → AAudio_sink** |
| alpine chroot | none | yes (`.so` only) | none | none | none | n/a |
| ubuntu chroot | none | none | none | none | none | n/a |
| fedora chroot | yes (`/usr/sbin/pactl`) | yes | none | none | **yes** + `pipewire-pulse.{service,socket}` | **OK → AAudio_sink** |
| void chroot | none | yes | none | none | none | n/a |
| opensuse chroot | none | none | none | none | none | n/a |
| arch chroot | yes | yes | none | none | none | **OK → AAudio_sink** |
| kali chroot | none | none | none | none | none | n/a |
| deepin chroot | none | none | none | none | none | n/a |
| chimera chroot | none | none | none | none | none | n/a |
| manjaro chroot | yes | yes | none | none | none | **OK → AAudio_sink** |
| parrot chroot | none | none | none | none | none | n/a |

Debian 13 is the old `xfce4` + `xfce4-goodies` path (`setup_debian_family.sh`). Everyone else uses a **targeted XFCE list** (`xfce4-session`, panel, settings, terminal, xfdesktop, xfwm4, thunar) that never installs Pulse/PipeWire clients. That is also why xfce4-panel logs `Plugin "pulseaudio-8" was not found` (deepin PRoot report).

Debian 13 `etc/pulse/client.conf` still has `; autospawn = yes` (default **on**). Without `PULSE_SERVER`, a GUI app will spawn a **guest** Pulse with no Android sink.

Fedora already ships PipeWire. `pactl` there is a PipeWire-compatible client. With `PULSE_SERVER` set it talks to the host. Without it, a session bus can activate `pipewire-pulse.socket` and steal the default.

Without `PULSE_SERVER`, Debian 13 / Arch also hit:

```
XDG_RUNTIME_DIR (/tmp) is not owned by us (uid 1000), but by uid 0!
Connection failure: Connection refused
```

TCP is the only reliable guest transport.

### 3.5 Diagnostic mutation left on the device

Planning probes copied these four libraries into the live PREFIX and left a host Pulse running (AAudio + TCP):

`libsoxr.so`, `libsoxr-lsr.so`, `libandroid-execinfo.so`, `libFLAC.so`, `libmp3lame.so`

That is **not** the product fix. Implementers must treat the device PREFIX as dirty until a real overlay / re-extract lands. Do not ship “adb push .so” as the solution.

---

## 4. Failure chain (one sentence each)

1. **Linker:** host Pulse cannot start — bootstrap omitted `libsoxr`, `libandroid-execinfo`, `libflac`, `libmp3lame`.
2. **Sink:** `default.pa` loads SLES (fails on Android 16) and never AAudio → dummy sink even after (1).
3. **Lifecycle:** native terminal never starts Pulse; GUI start swallows errors; GUI stop kills Pulse.
4. **Env:** `env -i` guest logins omit `PULSE_SERVER`; only GUI wrappers set it.
5. **Client:** targeted family XFCE lists do not install `libpulse` / `pactl` / panel plugin on most distros.
6. **Guest servers:** Debian 13 autospawn + Fedora `pipewire-pulse.socket` can hide the host server if (4) is missed.

Fix in that order. (5) and (6) are useless while (1)–(3) are broken.

---

## 5. What must not break

| Do not | Why |
| --- | --- |
| Uninstall the APK / wipe PREFIX blindly | Destroys PRoot containers and host state. `BootstrapInstaller` already preserves `usr/var/lib/proot-distro` on force extract — still prefer an **overlay**, not `EXTRACT_VERSION` bump, just to add four `.so` files. |
| Touch `/data/local/tmp/chroot*` trees except guest config/packages | Storage / kill work is in flight. |
| Start Pulse as root or `pulseaudio --system` | Docs + `start_gui.sh` `IS_ROOT` branch are wrong. SLES/AAudio need the app uid. Delete the `--system` fallback. |
| Start Pulse **inside** the guest | No Android sink; fights the host on 4713 / cookies. |
| `pkill -f pulseaudio` from stop-desktop as a hard requirement | That is the current `stop_gui.sh` step 4. After this plan Pulse is a **host service**; stop-desktop kills X11 + VirGL only. |
| Bind TCP on `0.0.0.0` | Today’s `load-module module-native-protocol-tcp` without `listen=` did exactly that. Must pass `listen=127.0.0.1`. |
| `auth-anonymous=0` / cookie-only | Chroot uid 1000 cannot use the app-uid cookie. Keep `auth-anonymous=1` **and** `auth-ip-acl=127.0.0.1`. |
| Install the guest `pulseaudio` **daemon** (or enable `pipewire-pulse`) | Autospawn / socket activation steals clients. Client packages only. |
| Fall back to `apt install xfce4` / `apk add xfce4` to “get audio” | Pulls gvfs/udisks/ffmpeg; known proot hangs. Add **named** client packages. |
| Block XFCE start if Pulse fails | Desktop must still paint. Log `FluxLinux: [AUDIO] …` as a first-class line (DesktopLauncher already surfaces stdout). |
| Change VirGL / X11 / `libXlorie` / loader.apk / W^X | Out of scope. |
| Add `RECORD_AUDIO` | Playback (AAudio/SLES sink) does not need it. Mic is a later slice. |
| Route product GUI through `TermuxIntentFactory` / `com.termux` | Embedded host is SSOT. |
| Leak host `PATH` / `LD_LIBRARY_PATH` into guests | `ProotCommandBuilder` already uses `env -i` for this. Add `PULSE_SERVER` only. |
| `am force-stop` the app package | Existing start scripts already forbid this. |

---

## 6. Implementation (PR DAG)

Do not start PR2 until PR1’s host daemon **links** on a device PREFIX. Do not start PR4 until PR2’s `pactl info` on the host shows a non-null sink.

```
PR1 host libs + verify
   └─ PR2 host supervisor (aaudio + tcp + lifecycle)
         ├─ PR3 guest env (builders + helper + profile.d)
         └─ PR4 guest clients + disable guest servers
               └─ PR5 device matrix (all distros, both methods)
```

### PR1 — Bootstrap actually runs Pulse

**Goal:** `pulseaudio` and `pactl` `dlopen` on a fresh PREFIX for both app ids.

| ID | Work |
| --- | --- |
| H1 | Add to **all** assemble lists that include Pulse: `native/package-lists/termux-lib-ssot.txt`, `bootstrap-host.txt`, `scripts/assemble_bootstrap.py` `DEFAULT_PACKAGES`: `libsoxr`, `libandroid-execinfo`, `libflac`, `libmp3lame`. Keep `flac` (CLI) if already listed; it is **not** a substitute for `libflac`. |
| H2 | Re-assemble `bootstrap.tar` for `com.ivarna.fluxlinux` (and zenithblue in the same change or a follow-up with the same list). Debs already exist under `native/output/<appid>/`. |
| H3 | Extend `scripts/verify_bootstrap.sh`: require `usr/lib/libsoxr.so`, `usr/lib/libandroid-execinfo.so`, `usr/lib/libFLAC.so`, `usr/lib/libmp3lame.so`, `usr/lib/pulseaudio/modules/module-aaudio-sink.so`, `usr/lib/pulseaudio/modules/module-sles-sink.so`, `usr/lib/pulseaudio/modules/module-native-protocol-tcp.so`. Optionally walk `readelf NEEDED` from `usr/bin/pulseaudio` and fail on unresolved non-system libs. |
| H4 | **Existing devices:** do **not** bump `BootstrapInstaller.EXTRACT_VERSION` only for this. Ship a tiny overlay (`assets/pulse-runtime/<appid>/*.so` **or** copy from the new bootstrap tar members) applied by `HostScriptDeployer` / `start_pulse_host.sh` if a required `.so` is missing. Preserve proot containers. |
| H5 | `setup_termux.sh`: after `command -v pulseaudio`, run a link smoke (`pulseaudio --version` or `ldd`/`readelf` check). Fail host setup if Pulse cannot exec. |

### PR2 — Host Pulse supervisor

**Goal:** one SSOT script starts / heals Pulse as the app uid; GUI and native terminal both use it; desktop stop does not kill it.

New host script (deployed next to `start_gui.sh`): `$HOME/start_pulse_host.sh`.

Contract:

1. Source `fluxlinux-host.env`. Set `HOME=$TERMUX__HOME`, `TMPDIR=$PREFIX/tmp`, `PULSE_RUNTIME_PATH=$HOME/.pulse`, `XDG_RUNTIME_DIR=$TMPDIR` (or `$HOME/.pulse-runtime` at 0700 — do not use a root-owned `/tmp`).
2. If `pactl info` already talks to this PREFIX and default sink is not `auto_null` and TCP `127.0.0.1:4713` is up → print `FluxLinux: [AUDIO] already running` and exit 0.
3. Otherwise start (no `--system`, no `2>/dev/null`):

   ```bash
   pulseaudio --start --exit-idle-time=-1 \
     --dl-search-path="$PREFIX/lib/pulseaudio/modules"
   ```

4. Load, in order, ignoring “already loaded”:
   - `module-aaudio-sink`
   - if still no real sink: `module-sles-sink`
   - `module-native-protocol-tcp auth-ip-acl=127.0.0.1 auth-anonymous=1 listen=127.0.0.1`
5. Print one line the desktop log already captures: `FluxLinux: [AUDIO] sink=<name> tcp=127.0.0.1:4713` or `FluxLinux: [AUDIO] FAIL <reason>`.
6. Exit 0 even on audio fail (desktop must continue). Non-zero only if the script itself cannot run.

Wire-up:

| Caller | Change |
| --- | --- |
| `start_gui.sh` | Remove `pkill pulseaudio` and the `--system`/`--load` blob. Call `start_pulse_host.sh`. Keep `PULSE_SERVER=tcp:127.0.0.1` in the guest wrapper. |
| `start_gui_chroot.sh` | Same. Pulse stays on the **app-uid** side **before** `su`. |
| `start_gui_kde.sh` | Same. |
| `stop_gui.sh` / `stop_gui_chroot.sh` | **Do not** `pulseaudio --kill`. Still stop VirGL + X11. |
| `TerminalLauncher.prepareHost` or first host/proot/chroot session | Ensure Pulse once per process lifetime (cheap `pactl info`). Native terminal audio then works without opening XFCE. |
| `default.pa` in bootstrap | Uncomment `module-aaudio-sink`; keep SLES as `.nofail` fallback; add TCP with `listen=127.0.0.1`. Supervisor must still `pactl load-module` so **already-extracted** prefixes work without re-extract. |

Do **not** change `TermuxIntentFactory` except a comment that it is not the product audio path.

### PR3 — Guest `PULSE_SERVER` everywhere

Unify on **`tcp:127.0.0.1`** (not bare `127.0.0.1`) so clients never try a unix socket.

| File | Change |
| --- | --- |
| `ProotCommandBuilder.guestLoginEnv` | Add `PULSE_SERVER=tcp:127.0.0.1`. Unit-test the string. |
| `fluxlinux_chroot.sh` `build_guest_env_args` | Same. Bump helper version comment. |
| `ChrootCommandBuilder.buildEnv` | Same (outer env; inner `env -i` is the one that matters). |
| `flux_guest_common.sh` | `_flux_write_pulse_client`: write `/etc/profile.d/flux-pulse.sh`, `/etc/environment` line, and if `/etc/pulse/client.conf` exists set `default-server = tcp:127.0.0.1` and `autospawn = no`. Call from `_flux_ensure_home` / family tails. |
| Family VNC `xstartup` | Already exports Pulse; change to `tcp:127.0.0.1`. |
| GUI wrappers | Already `tcp:127.0.0.1` on the current start scripts — leave them as a belt. |

Do not add host `PATH` or `LD_LIBRARY_PATH`.

### PR4 — Guest clients, no guest servers

Add `_flux_install_pulse_client` in `flux_guest_common.sh` (or a tiny `setup_pulse_guest.sh` prepended like other common helpers). Best-effort, never `handle_error` the whole family if a plugin package is missing.

| Family | Install (try in order) | Explicitly do **not** enable |
| --- | --- | --- |
| Debian / Ubuntu / Kali / Parrot / Deepin | `libpulse0` `pulseaudio-utils` `xfce4-pulseaudio-plugin` | `pulseaudio` daemon; if already present (Debian 13) leave the package, force `autospawn = no` |
| Alpine | `libpulse` `pulseaudio-utils` `xfce4-pulseaudio` | `pulseaudio` service |
| Fedora | `pulseaudio-libs` `pulseaudio-utils` `xfce4-pulseaudio-plugin` | **mask** `pipewire-pulse.socket` + `pipewire-pulse.service` (user); do not `dnf remove pipewire` |
| openSUSE | `libpulse0` `pulseaudio-utils` `xfce4-pulseaudio-plugin` | pipewire-pulse socket |
| Void | `libpulseaudio` `pulseaudio` (only if that is how `pactl` is shipped) **or** `pulseaudio-utils` if it exists; `xfce4-pulseaudio-plugin` | guest daemon autostart |
| Arch / Manjaro | `libpulse` `xfce4-pulseaudio-plugin` | `pulseaudio` package (server) |
| Chimera | `libpulse` / `pulseaudio` client bits as named on apk v3; skip plugin if absent | guest daemon |

Also write `/etc/pulse/client.conf.d/99-fluxlinux.conf` when the directory exists (safer than rewriting vendor `client.conf`):

```
default-server = tcp:127.0.0.1
autospawn = no
```

Existing installs: run the same helper from a one-shot “repair audio” host script (`setup_pulse_guest.sh` via `proot-distro login` / `fluxlinux_chroot.sh`) so users are not told to reinstall XFCE.

Do **not** add Pulse to video-editing / KDE extra modules as a substitute for this helper.

### PR5 — Device matrix

Ivarna release, `adb install -r`. Host Pulse must be started from the **app** (`prepareHost` / desktop), not from a leftover planning `nsenter` daemon.

For **native**, **one PRoot** (openSUSE is the current fixture), and **every installed chroot**:

| ID | Probe | Pass |
| --- | --- | --- |
| A1 | `ps` Pulse uid = app uid, context `untrusted_app` | not root / not `ksu` |
| A2 | `pactl info` default sink ≠ `auto_null` | `AAudio_sink` or SLES |
| A3 | `ss`/`/proc/net/tcp` | **only** `127.0.0.1:4713`, not `0.0.0.0:4713` |
| A4 | Native `PULSE_SERVER=tcp:127.0.0.1 pactl info` | server 17.x, sink real |
| A5 | Native `module-sine` 1 s or `paplay` | audible |
| A6 | PRoot login (in-app or `proot-distro` + same env as builder) `pactl info` | host server + real sink |
| A7 | Each chroot `fluxlinux_chroot.sh sh --user flux -- 'PULSE_SERVER=tcp:127.0.0.1 pactl info'` | same. Distros without a client yet fail A7 until PR4 lands — that is expected if PR5 is split. |
| A8 | Start XFCE, stop XFCE | Pulse **still running**; X11 gone |
| A9 | `start_gui` log contains `FluxLinux: [AUDIO]` | no silent `2>/dev/null` |
| A10 | Host pid / app still alive after A6–A8 | no `am force-stop`, no PREFIX wipe |

Write a short `docs/plans/results/pulseaudio-device-report.md` with log pointers. Status of this plan flips to COMPLETE only with those pointers.

---

## 7. File touch list (expected)

| Area | Files |
| --- | --- |
| Bootstrap lists | `native/package-lists/termux-lib-ssot.txt`, `bootstrap-host.txt`, `scripts/assemble_bootstrap.py`, `scripts/verify_bootstrap.sh` |
| Host scripts | **new** `app/src/main/assets/scripts/host/start_pulse_host.sh` (or `debian/proot/start/` + deploy map), `start_gui.sh`, `start_gui_chroot.sh`, `start_gui_kde.sh`, `stop_gui.sh`, `stop_gui_chroot.sh`, `host/setup_termux.sh` |
| Overlay | `HostScriptDeployer.kt` and/or `BootstrapInstaller.kt` (copy missing `.so` only) |
| Kotlin env | `HostCommandBuilder.kt` (optional ensure), `ProotCommandBuilder.kt`, `ChrootCommandBuilder.kt`, tests next to them |
| Chroot helper | `fluxlinux_chroot.sh` `build_guest_env_args` |
| Guest | `flux_guest_common.sh` + family tails (or one prepended `setup_pulse_guest.sh`) |
| Docs | this plan, `docs/CHROOT_HARDWARE_ACCEL.md` (Termux → embedded PREFIX), `TroubleshootingScreen.kt` copy (“install pulseaudio in Termux” is stale) |
| In-app Audio UI | `PulseHost.kt` (query/start/restart + `cacheDir/pulse_host.log`), `AudioSettingsScreen.kt`, `SettingsScreen.kt` nav card after X11, `MainActivity.kt` `SETTINGS_AUDIO` |
| Tests | `ProotCommandBuilder` env contains `PULSE_SERVER=tcp:127.0.0.1`; helper env builder; `verify_bootstrap` fixture if one exists |

`HostScriptDeployer` must list the new host script or GUI/native will keep the old silent start.

---

## 8. Correctness review (planning pass)

Reviewed against the device probes, current scripts, and the “must not break” table. **No code was changed.**

### 8.1 The diagnosis is sufficient to implement

- The linker errors are deterministic and match the bootstrap tarball, not a one-off device corruption.
- AAudio vs SLES was proven on the same app uid / SELinux context the product uses (`ShellCommandRunner` children are `untrusted_app`).
- Guest TCP connect was proven on every chroot that already has `pactl` (Debian 13, Fedora, Arch, Manjaro). Missing clients are a package-list gap, not a network mystery.
- `env -i` omission of `PULSE_SERVER` is visible in both builders and `fluxlinux_chroot.sh`.

### 8.2 Risks that would re-break audio or other features

| Risk | Mitigation in this plan |
| --- | --- |
| Full bootstrap re-extract wipes PRoot | Overlay missing `.so`; `ensureExtracted` already preserves `proot-distro` if someone force-extracts |
| `stop_gui` keeps killing Pulse | Explicit contract change; CLI + second desktop start stay alive |
| TCP exposed on all interfaces | `listen=127.0.0.1` required |
| Guest PipeWire wins the session | `PULSE_SERVER` + `autospawn=no` + mask `pipewire-pulse.socket` |
| Debian 13 already has a Pulse **server** | Do not remove it; disable autospawn; `PULSE_SERVER` wins |
| Family script grows into `xfce4` metapackage | Named client packages only |
| Pulse start as root via `start_gui.sh` `IS_ROOT` | Delete `--system` |
| Supervisor failure blocks XFCE | Exit 0 + `[AUDIO] FAIL` line |
| Dual app-id RUNPATH | Overlay / debs must be per `applicationId` (same as today’s Pulse binary) |
| `pkill -f pulseaudio` matches guest tools | Stop using pkill; `pulseaudio --check` / `pactl info` |
| In-flight storage / kill PRs | No Settings/chroot-manager edits |
| Planning `.so` copies on the fixture phone | Documented dirty PREFIX; real fix replaces them with overlay/assemble |

### 8.3 What this plan is not claiming

- It does not claim SLES will work on Android 16. AAudio is the primary sink; SLES is fallback.
- It does not claim every family package name above is exact on Chimera/Void — PR4 is best-effort with `command -v pactl` as the gate.
- It does not claim microphone / `module-sles-source` / `RECORD_AUDIO`.
- It does not claim KDE-only paths beyond `start_gui_kde.sh` using the same supervisor.
- It does not claim zenithblue was device-tested (package not installed). The missing-lib set is in the **shared** recipe; zenithblue assemble must get H1–H3.

### 8.4 Verdict

The plan is implementable and does not require changing X11, VirGL, rootfs download, or guest-shell selection. The single product rule is: **host Pulse in the app PREFIX, TCP localhost, guest clients only.** Anything that starts a second server or swallows host start errors will recreate today’s “no sound on any distro” report.

---

## 9. Out of scope

- External Termux / `com.termux` Pulse.
- Bluetooth routing, per-app Android volume UI, pavucontrol as a required package (optional later).
- Rebuilding Pulse itself; use the existing `pulseaudio_17.0-1` debs.
- Fixing proot-distro `termux-profile.sh` host `PATH` leak (unrelated; do not expand).
- First-X SIGSYS / VirGL (already deferred in other plans).

---

## 10. Suggested first implementer commit

H1 + H3 + a failing-then-passing `verify_bootstrap.sh` run on the current tarball (it must **FAIL** today on the four `.so` files). That locks the diagnosis before any launch-script edits.

---

## 11. In-app Audio settings (follow-up)

Settings hub card **Audio** (after X11 Display) opens `AudioSettingsScreen`:

- Status from `pulseaudio --check` + `pactl info` (probe does **not** start the daemon).
- **Start** when stopped; **Restart** when running (`pulseaudio --kill` then `start_pulse_host.sh` — only this button kills Pulse).
- **View logs** / **Copy** of `cacheDir/pulse_host.log` (supervisor + status lines).
- Desktop stop still does **not** kill Pulse.
- `start_pulse_host.sh` execs `$PREFIX/bin/pulseaudio` under `/system/bin/env -i` (no `LD_PRELOAD`). App-uid `libbash` + termux-exec otherwise breaks `pulseaudio --start` (`/proc/self/exe` mismatch).
- TCP gate: `tcp_ok` passes `PULSE_SERVER=tcp:127.0.0.1` **inside** `env -i`; after `load-module` the supervisor re-checks `/proc/net/tcp` (`tcp_bound_localhost_only`) and `tcp_ok` before printing success. `/proc/net/tcp` must be **this app uid** (reinstall assigns a new uid; a leftover Pulse on `:4713` is not ours).
- `ensureStarted` retries on `[AUDIO] FAIL` / missing success line (once-per-process flag is not sticky on failure).
- Idle-daemon heal uses the first `pidof` PID only; missing `pidof` does not `--kill`; waits until `--check` is false before `--start`.
- Debian/Alpine write `PULSE_SERVER` via `_flux_write_pulse_client` (or the same append/replace) so `/etc/environment` is not truncated.
- Void (and the SSOT helper) never falls back to installing the guest `pulseaudio` daemon package.
- Settings → **Repair guests** runs `repair_pulse_guests.sh`: stages `setup_pulse_guest.sh` + `flux_guest_common.sh` into each installed PRoot (`--shared-tmp` + `env -i` guest PATH, same as `ProotCommandBuilder.guestLoginEnv`) and each chroot when `su` works. Guest `pactl` counts only `/usr/bin/pactl` / `/usr/sbin/pactl` / `/bin/pactl` (not host `$PREFIX/bin`). Toast is fail/partial/success from that output.
- Guest `sed -i` goes through `_flux_sed_i` (temp file + mv) so Chimera BSD sed does not fail.

Tests: `PulseHostTest` (parse status / log ring / `supervisorOk`), `AudioSettingsContractTest` (hub + wiring + TCP gate + no daemon fallback).
