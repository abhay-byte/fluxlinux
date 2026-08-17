# PulseAudio host + guest matrix — device report

| Field | Value |
| --- | --- |
| **Date** | 2026-08-16 |
| **Device** | `Y5WWBMJVOZSK4HU8` · Xiaomi 2311DRK48I (duchamp) · Android 16 · uid `10303` / `u0_a303` |
| **Package** | `com.ivarna.fluxlinux` versionCode 12 / versionName 2.0.0 |
| **Install** | `:app:assembleIvarnaRelease` + `adb install -r` (no uninstall, no PREFIX/chroot wipe) |
| **Host Pulse** | pid 12053 · uid 10303 · `u:r:untrusted_app:s0:c47,c257,c512,c768` · `pulseaudio --start --exit-idle-time=-1` |
| **Plan** | [docs/plans/pulseaudio-host-guest-all-distros.md](../pulseaudio-host-guest-all-distros.md) |

## Matrix

| ID | Probe | Result |
| --- | --- | --- |
| A1 | Pulse uid + SELinux | uid 10303, `untrusted_app` (not root) |
| A2 | Default sink | `AAudio_sink` (48 kHz path from earlier `pactl info`) |
| A3 | Listen | `/proc/net/tcp` `0100007F:1269` only — **not** `0.0.0.0:4713` |
| A4 | Native `PULSE_SERVER=tcp:127.0.0.1 pactl info` | server 17.0-dirty, sink `AAudio_sink` |
| A5 | `module-sine frequency=440` 1s | module index loaded; Pulse stayed up |
| A6 | PRoot `debian` + `opensuse` `pactl info` | **PASS** host 17.0 + `AAudio_sink` via `tcp:127.0.0.1` |
| A7 | All 12 installed chroots | **PASS** (table below) |
| A8 | `stop_gui.sh` | prints “VirGL (Pulse stays running)”; no `pulseaudio --kill` |
| A9 | `start_gui.sh` / supervisor | `FluxLinux: [AUDIO] sink=AAudio_sink tcp=127.0.0.1:4713` |
| A10 | App after probes | pid 14192 still alive; no `am force-stop`; no PREFIX wipe |

On-device logs: `/data/local/tmp/flux-pulse-matrix.txt` (repair), `/data/local/tmp/flux-pulse-probe2.txt` (connect).

### Guest connect (`pactl info` → host `AAudio_sink` / `tcp:127.0.0.1` / 17.0-dirty)

| Guest | Method | Result |
| --- | --- | --- |
| debian | PRoot | PASS |
| opensuse | PRoot | PASS |
| alpine | chroot | PASS |
| debian13 | chroot | PASS |
| fedora | chroot | PASS |
| void | chroot | PASS |
| opensuse | chroot | PASS |
| deepin | chroot | PASS |
| chimera | chroot | PASS (`libpulse` + `libpulse-progs`) |
| manjaro | chroot | PASS |
| ubuntu | chroot | PASS |
| kali | chroot | PASS |
| parrot | chroot | PASS |
| arch | chroot | PASS |

Existing guests repaired with `setup_pulse_guest.sh` (named client packages + `autospawn = no` + `default-server = tcp:127.0.0.1`). New installs get the same via `_flux_setup_pulse` in family tails.

## Implementer notes from the run

- Host `pactl` **must not** autospawn. A default autospawn daemon dies after ~20s (`--exit-idle-time` default). Supervisor writes `~/.config/pulse/client.conf` `autospawn = no`, uses `pulseaudio --check` (not `pactl`) to detect a live daemon, and starts with `--exit-idle-time=-1`.
- Host env must **not** set `PULSE_SERVER`. If it is set, `pulseaudio --start` prints `User-configured server at tcp:127.0.0.1, refusing to start/autospawn`. Guests still get `PULSE_SERVER=tcp:127.0.0.1` from `env -i` builders / `fluxlinux_chroot.sh` v2.9.
- Chimera client bits are `libpulse` + `libpulse-progs` (not a generic `pulseaudio` package).
- openSUSE `pulseaudio-utils` pulled a guest `pulseaudio` **server** RPM. Left installed; `autospawn = no` + `PULSE_SERVER` keep clients on the host.

## XFCE on the in-app X11 display (2026-08-16 follow-up)

Started Debian PRoot via `start_gui.sh debian` as uid 10303 / `untrusted_app` (same argv as `DesktopLauncher`). Host Pulse stayed pid 12053 (`--exit-idle-time=-1`). Log: `FluxLinux: [AUDIO] already running sink=AAudio_sink tcp=127.0.0.1:4713` then `X server PID=` / `startxfce4=READY`.

| Check | Result |
| --- | --- |
| `com.termux.x11.MainActivity` resumed | yes |
| XFCE paints | `xfce4-session` + `xfwm4` + `xfce4-panel` + penguin wallpaper ([pulseaudio-xfce-desktop.png](pulseaudio-xfce-desktop.png)) |
| Guest `pactl info` inside XFCE | `Server String: tcp:127.0.0.1`, `Default Sink: AAudio_sink`, Pulse 17.0 |
| `paplay …/complete.oga` from `xfce4-terminal` on `:0` | `PLAYED exit=0` ([pulseaudio-xfce-audio-terminal.png](pulseaudio-xfce-audio-terminal.png)) |
| During playback | `pactl list short sink-inputs` had a stream; sink went **SUSPENDED → RUNNING** |
| Panel plugin | `/usr/lib/aarch64-linux-gnu/xfce4/panel/plugins/libpulseaudio-plugin.so` present |
| Pulse after XFCE start | still uid 10303, same daemon |

## Settings → Audio (2026-08-16)

In-app page: Settings hub **Audio** → `AudioSettingsScreen`.

| Check | Result |
| --- | --- |
| Hub card | Present after X11 Display ([pulseaudio-settings-hub.png](pulseaudio-settings-hub.png)) |
| Status while daemon up | **Running** `sink=AAudio_sink tcp=127.0.0.1:4713` ([pulseaudio-settings-running.png](pulseaudio-settings-running.png)) |
| After `pulseaudio --kill` + Refresh | **Stopped** / **Start** |
| Tap **Start** | Supervisor `FluxLinux: [AUDIO] sink=AAudio_sink tcp=127.0.0.1:4713`; UI **Running** + **Restart**; User Name `u0_a303` ([pulseaudio-settings-started.png](pulseaudio-settings-started.png)) |
| View logs | Shows `=== STATUS ===` / `=== SUPERVISOR ===` from `cacheDir/pulse_host.log` |

Start from the app must run `pulseaudio` via `/system/bin/env -i` (no `LD_PRELOAD`). `libbash` + termux-exec makes `--start` fail with `/proc/self/exe` mismatch.

No PRoot containers or `/data/local/tmp/chroot*` trees were wiped. `EXTRACT_VERSION` was not bumped; missing `.so` files overlay from flavor `assets/pulse-runtime/`.

## Review follow-up (2026-08-17)

| Issue | Fix |
| --- | --- |
| `tcp_ok` dropped `PULSE_SERVER` via `env -i` | Probe is `pa_env PULSE_SERVER=tcp:127.0.0.1 pactl info`; post-load re-check of `/proc/net/tcp` + `tcp_ok` |
| `ensureStarted` sticky FAIL | Reset once-per-process flag unless supervisor prints success / already-running |
| `pidof` multi-PID always `--kill` | First PID only; skip cmdline test if `pidof` missing; wait for `--check` false |
| Debian/Alpine truncated `/etc/environment` | `_flux_write_pulse_client` or append/replace; common prepended on new installs |
| Void could install guest `pulseaudio` | Last fallback dropped in SSOT + inlined copies |
| `setup_pulse_guest.sh` never ran from the app | Settings → Audio **Repair guests** → `repair_pulse_guests.sh`. PRoot login uses `env -i` + guest PATH; client present only if `/usr/{bin,sbin}/pactl` or `/bin/pactl`. |
| Reinstall left old-uid Pulse on `:4713` | `/proc/net/tcp` must match **this** app uid; else Settings showed Running (no TCP). Stale `u0_a303` listener killed; supervisor FAIL names the foreign uid. |
