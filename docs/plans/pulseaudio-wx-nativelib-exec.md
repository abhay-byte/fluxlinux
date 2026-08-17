# Plan: W^X-safe host Pulse (`libpulseaudio.so` / `libpactl.so`)

| Field | Value |
| --- | --- |
| **Date** | 2026-08-17 |
| **Status** | **COMPLETE** (device, 2026-08-17) |
| **Flavor** | Ivarna first (`com.ivarna.fluxlinux`); zenithblue gets the same jniLibs |
| **APK** | `:app:assembleIvarnaRelease` + `adb install -r`. No PREFIX/chroot wipe. |
| **Scope lock** | Host Pulse exec only. Do not change X11 / VirGL / `libXlorie` / `loader.apk` / proot W^X loaders. Do not edit `docs/releases/v2.0.0-fdroid-release-plan.md`. Do not bump `EXTRACT_VERSION`. |

Parent plan: [pulseaudio-host-guest-all-distros.md](./pulseaudio-host-guest-all-distros.md). Guests stay TCP clients (`PULSE_SERVER=tcp:127.0.0.1`). This slice only makes the **host daemon startable** under targetSdk 36.

---

## 1. Point of failure (device, 2026-08-17)

Settings → Audio after a fresh host extract:

```
FluxLinux: [AUDIO] FAIL pulseaudio cannot exec
(env: exec /data/data/com.ivarna.fluxlinux/files/usr/bin/pulseaudio: Permission denied)
```

| Check | Result |
| --- | --- |
| `$PREFIX/bin/pulseaudio` exists, mode `711`, owned by app uid | yes |
| Root `pulseaudio --version` | **works** (`17.0-dirty`) |
| App uid + `/system/bin/env -i $PREFIX/bin/pulseaudio --version` | **EACCES** |
| Same for `$PREFIX/bin/bash` | already known; product uses `nativeLibraryDir/libbash.so` |
| Overlay `.so` mode-600 | already patched; this error is **execve**, not `dlopen` |

`start_pulse_host.sh` `pa_env` does `/system/bin/env -i … "$PREFIX/bin/pulseaudio"`. That is an `execve` of a file under `files/` (`app_data_file`). targetSdk 36 W^X forbids it for `untrusted_app`. Root / `ksu` can still exec it — earlier adb probes as `setuidgid` without `untrusted_app` were a false green.

This is the same reason `HostCommandBuilder` already sets `PD_PROOT_BIN` / `libbash.so` from `nativeLibraryDir`, not `$PREFIX/bin`.

`libbash` + termux-exec is still the wrong interpreter for Pulse: `/proc/self/exe` becomes `libbash.so` and `pulseaudio --start` refuses to daemonize. The fix is to exec the **real Pulse binary from `nativeLibraryDir`**, so `/proc/self/exe` is Pulse and W^X allows it.

### 1.1 Second failure (same day) — toybox `env` + `=` in `nativeLibraryDir`

After `libpulseaudio.so` was staged, Settings → Audio printed:

```
env: exec --version: No such file or directory
```

Android 7+ extracts jniLibs to:

```
/data/app/~~XXXX==/com.ivarna.fluxlinux-YYYY==/lib/arm64/libpulseaudio.so
```

Toybox `env` treats every `NAME=VALUE` argument as an assignment. That path contains `==`, so:

```
/system/bin/env -i PATH=… LD_LIBRARY_PATH=… "$PA" --version
```

becomes “set a bogus env var, then exec `--version`”. Device confirmation as uid `10308` (2026-08-17): same command with `$PREFIX/bin/pulseaudio` prints `17.0-dirty`; the nld path prints `env: exec --version`.

`--start` / `--check` / `--kill` are also unusable from nld: Pulse is compiled to re-exec `$PREFIX/bin/pulseaudio` and warns `cannot self execute. Are you playing games?`. `--daemonize=yes` plus `pactl info` / `kill $(pidof libpulseaudio.so)` work. Probe as uid `10308` (no root for the daemon): `AAudio_sink`, `tcp:127.0.0.1:4713`, pid uid=`10308`.

---

## 2. Target

```
app uid  —  nativeLibraryDir/libpulseaudio.so   (exec, W^X-safe)
            nativeLibraryDir/libpactl.so
                 │  LD_LIBRARY_PATH=$PREFIX/lib
                 ▼
         $PREFIX/lib/*.so + pulse modules   (dlopen is allowed from files/)
                 │
                 ▼
         module-aaudio-sink + TCP listen=127.0.0.1
```

No guest, X11, or bootstrap-tar change. Existing PREFIX stays.

---

## 3. Must not break

| Do not | Why |
| --- | --- |
| Exec Pulse from `$PREFIX/bin` as the product path | W^X EACCES on this device |
| Launch Pulse via `libbash.so` / `LD_PRELOAD=libtermux-exec.so` | `/proc/self/exe` mismatch; `--start` refuses |
| `pulseaudio --system` / start as root | AAudio needs app uid |
| Bump `EXTRACT_VERSION` / wipe PREFIX | Destroys PRoot containers |
| Touch `libproot` / `libloader` / `libXlorie` / loader.apk | Out of scope |
| Edit F-Droid release plan | Already implemented; leave it |
| Bind TCP `0.0.0.0` | Keep `listen=127.0.0.1` |
| Block XFCE if Pulse fails | `audio_fail` still exits 0 |

---

## 4. Implementation

| ID | Work |
| --- | --- |
| W1 | `assemble_bootstrap.py` `copy_to_jni_libs`: `usr/bin/pulseaudio` → `libpulseaudio.so`, `usr/bin/pactl` → `libpactl.so`. Stage copies now from the existing extract root (no full re-assemble required). |
| W2 | `package_host_assets.sh` + Gradle `jniRequired` + `verify_apk_host_assets.sh` + `verify_bootstrap.sh` require the two new `.so` files. |
| W3 | `TermuxHostPaths.libPulseaudio` / `libPactl`. `HostCommandBuilder.envMap` sets `PD_PULSEAUDIO_BIN` / `PD_PACTL_BIN` (same pattern as `PD_PROOT_BIN`). |
| W4 | `start_pulse_host.sh`: `PA`/`PACTL` prefer those env vars when the file is executable; keep PREFIX as fallback for non-W^X / debug. Still `/system/bin/env -i` (no `LD_PRELOAD`). Still `unset PULSE_SERVER`. Still chmod overlay `.so` files. |
| W5 | `PulseHost.query` / `restart` kill: use `PD_*` / `TermuxHostPaths` binaries, not `$PREFIX/bin`. |
| W6 | `setup_termux.sh` version smoke uses `$PD_PULSEAUDIO_BIN` when set. |
| W7 | Contract tests: APK/script strings mention `libpulseaudio.so` / `PD_PULSEAUDIO_BIN`. |
| W8 | Ivarna `assembleIvarnaRelease` + `adb install -r`. Device: Settings → Audio → Start → `AAudio_sink` + `tcp=127.0.0.1:4713` as app uid. |

---

## 5. Device pass

| ID | Pass |
| --- | --- |
| D1 | `lib/arm64-v8a/libpulseaudio.so` and `libpactl.so` in the installed APK |
| D2 | Supervisor line `FluxLinux: [AUDIO] sink=AAudio_sink tcp=127.0.0.1:4713` (or `already running`) |
| D3 | `ps` Pulse uid = app uid, not root |
| D4 | `/proc/net/tcp` listen `0100007F:1269` only |
| D5 | Desktop stop still does not kill Pulse |

Device (Xiaomi 2311DRK48I, uid `10308` / `u0_a308`), Settings → Audio → Start, **no root**:

```
FluxLinux: [AUDIO] sink=AAudio_sink tcp=127.0.0.1:4713
u0_a308  10308  …  u:r:untrusted_app:s0:c52,c257,c512,c768
  libpulseaudio.so --daemonize=yes --exit-idle-time=-1 …
/proc/net/tcp  0100007F:1269  uid=10308
```

UI: **Running** / `sink=AAudio_sink  tcp=127.0.0.1:4713`. Screenshot: [results/pulseaudio-settings-running-untrusted.png](results/pulseaudio-settings-running-untrusted.png).

`pulseaudio --start` / `--check` / `--kill` are unused: nld `/proc/self/exe` is not `$PREFIX/bin/pulseaudio` (“playing games”). Host uses `--daemonize=yes`; status uses a direct `libpactl.so info` over `PULSE_SERVER=tcp:127.0.0.1`.

---

## 6. Out of scope

- Rebuilding Pulse itself
- Microphone / `RECORD_AUDIO`
- Guest daemon / PipeWire (already handled)
- Re-uploading the GitHub `rootfs` bootstrap tar (jniLibs are in the APK, not that tar)
