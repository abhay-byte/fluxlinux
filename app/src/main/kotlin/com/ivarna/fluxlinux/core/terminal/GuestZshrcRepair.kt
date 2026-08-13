package com.ivarna.fluxlinux.core.terminal

import android.content.Context
import android.util.Log
import com.ivarna.fluxlinux.core.root.ChrootPaths
import java.io.File

/**
 * One-shot repair for guest `~/.zshrc` that hard-sources missing oh-my-zsh or
 * invokes missing `pokemon-colorscripts` (seen on device after partial network
 * customization installs). Keeps the same optimized profile shape as
 * `setup_customization_debian.sh` / Alpine customization, but guards every
 * optional tool.
 *
 * Proot rootfs lives under the app filesDir (writable). Chroot is under
 * `/data/local/tmp` and is only patched when the file is world-writable or
 * already app-owned — otherwise skip (needs re-run customization as root).
 *
 * Also **creates** a missing `.zshrc` when the home dir is writable (Alpine
 * installs that never wrote the Flux profile).
 */
object GuestZshrcRepair {

    private const val TAG = "GuestZshrcRepair"

    /** Defensive zshrc — mirrors setup_customization_debian.sh (post-fix). */
    private val DEFENSIVE_ZSHRC = """
# Guest PATH only — never inherit host PREFIX/bin (nested proot glue errors).
export PATH="${'$'}HOME/.local/bin:/opt/nodejs/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"

if locale -a 2>/dev/null | grep -qiE 'en_US\.(utf8|UTF-8)'; then
  export LANG=en_US.UTF-8
  export LC_ALL=en_US.UTF-8
else
  export LANG=C.UTF-8
  export LC_ALL=C.UTF-8
fi

# Host PROOT_TMP_DIR is not writable as guest uid; use guest /tmp.
unset PROOT_TMP_DIR
export TMPDIR="${'$'}{TMPDIR:-/tmp}"

# Fix XDG_RUNTIME_DIR (not set in PRoot/chroot — no systemd-logind)
export XDG_RUNTIME_DIR="${'$'}{XDG_RUNTIME_DIR:-/tmp}"

# proot does not implement tcsetpgrp — zsh job control would ENOSYS and the
# shell can get SIGTTIN-killed. Disable MONITOR (job control) for the guest.
setopt no_monitor

# Background visuals - don't block shell startup; skip missing tools (no error spam)
{
  if command -v fastfetch >/dev/null 2>&1; then
    fastfetch --config termux 2>/dev/null || fastfetch 2>/dev/null || true
  fi
  if command -v pokemon-colorscripts >/dev/null 2>&1; then
    pokemon-colorscripts --no-title -r 1,2,3 2>/dev/null || true
  fi
} &!

# oh-my-zsh (optional — install may fail offline; shell still usable without it)
export ZSH="${'$'}{ZSH:-${'$'}HOME/.oh-my-zsh}"
if [ -f "${'$'}ZSH/oh-my-zsh.sh" ]; then
  ZSH_THEME="agnosterzak"
  DISABLE_UPDATE_PROMPT=true
  DISABLE_AUTO_UPDATE=true
  ZSH_DISABLE_COMPFIX=true
  # Removed zsh-autocomplete (very slow), kept essential plugins
  plugins=(git zsh-autosuggestions zsh-syntax-highlighting)
  source "${'$'}ZSH/oh-my-zsh.sh"
fi
""".trimStart()

    /**
     * Patch or create guest flux `.zshrc` when needed.
     * Safe to call on every session open — no-ops when already fixed.
     *
     * @param method `proot` | `chroot`
     * @param distroId card id (`debian`, `alpine`, `alpine_chroot`, …); resolves
     *   the correct rootfs / chroot path.
     */
    private val ALPINE_APK_WRAPPER = """

# Alpine/Chimera: package manager needs root. NOPASSWD sudo is configured for flux.
if command -v apk >/dev/null 2>&1 && command -v sudo >/dev/null 2>&1; then
  apk() { command sudo apk "${'$'}@"; }
fi
""".trimStart()

    private val GLIBC_PM_WRAPPER = """

# Guest package managers need root. NOPASSWD sudo is configured for flux.
if command -v sudo >/dev/null 2>&1; then
  if command -v apt-get >/dev/null 2>&1; then
    apt-get() { command sudo apt-get "${'$'}@"; }
  fi
  if command -v apt >/dev/null 2>&1; then
    apt() { command sudo apt "${'$'}@"; }
  fi
  if command -v pacman >/dev/null 2>&1; then
    pacman() { command sudo pacman "${'$'}@"; }
  fi
  if command -v dnf >/dev/null 2>&1; then
    dnf() { command sudo dnf "${'$'}@"; }
  fi
  if command -v dnf5 >/dev/null 2>&1; then
    dnf5() { command sudo dnf5 "${'$'}@"; }
  fi
  if command -v xbps-install >/dev/null 2>&1; then
    xbps-install() { command sudo xbps-install "${'$'}@"; }
  fi
  if command -v zypper >/dev/null 2>&1; then
    zypper() { command sudo zypper "${'$'}@"; }
  fi
fi
""".trimStart()

    fun repairIfNeeded(ctx: Context, method: String, distroId: String? = null) {
        val zshrc = resolveZshrc(ctx, method, distroId) ?: return
        val rootfs = zshrc.parentFile?.parentFile?.parentFile // home/flux/.zshrc → rootfs
        val isApkGuest = isApkGuest(distroId)
        val needsPmWrap = !isApkGuest && isGlibcGuest(distroId)
        try {
            if (zshrc.isFile) {
                val text = zshrc.readText()
                if (needsRepair(text)) {
                    if (!zshrc.canWrite()) {
                        Log.w(TAG, "zshrc needs repair but not writable: ${zshrc.absolutePath}")
                    } else {
                        zshrc.writeText(profileFor(isApkGuest, needsPmWrap))
                        Log.i(TAG, "Repaired defensive .zshrc at ${zshrc.absolutePath}")
                    }
                } else if (isApkGuest && !text.contains("apk() {") && zshrc.canWrite()) {
                    zshrc.appendText("\n$ALPINE_APK_WRAPPER")
                    Log.i(TAG, "Appended apk() wrapper to ${zshrc.absolutePath}")
                } else if (needsPmWrap && !text.contains("dnf() {") &&
                    !text.contains("xbps-install() {") &&
                    !text.contains("zypper() {") &&
                    !text.contains("apt-get() {") &&
                    !text.contains("pacman() {") &&
                    zshrc.canWrite()
                ) {
                    zshrc.appendText("\n$GLIBC_PM_WRAPPER")
                    Log.i(TAG, "Appended glibc PM wrappers to ${zshrc.absolutePath}")
                }
            } else {
                // Create missing Flux profile (Alpine used to never write .zshrc)
                val home = zshrc.parentFile ?: return
                if (!home.isDirectory) return
                if (!home.canWrite() && !zshrc.canWrite()) {
                    Log.w(TAG, "cannot create .zshrc (home not writable): ${home.absolutePath}")
                } else {
                    zshrc.writeText(profileFor(isApkGuest, needsPmWrap))
                    Log.i(TAG, "Created defensive .zshrc at ${zshrc.absolutePath}")
                }
            }
            ensureZprofile(zshrc.parentFile)
            // Prefer zsh login shell when binary exists (Alpine often left /bin/bash)
            if (rootfs != null) ensureFluxLoginShellZsh(rootfs)
            writeFastfetchPresets(zshrc.parentFile, rootfs)
        } catch (e: Exception) {
            Log.w(TAG, "zshrc repair failed: ${e.message}")
        }
    }

    /** Always overwrite the Flux fastfetch preset (disk=/ only, no pacman format). */
    internal val TERMUX_JSONC = """
{
  "logo": null,
  "display": { "separator": " ›  " },
  "modules": [
    { "type": "os", "key": "OS  " },
    { "type": "kernel", "key": "KER " },
    { "type": "cpu", "key": "CPU " },
    { "type": "gpu", "key": "GPU " },
    { "type": "packages", "key": "PKG " },
    { "type": "shell", "key": "SH  " },
    { "type": "terminal", "key": "TER " },
    {
      "type": "disk",
      "key": "DSK ",
      "folders": ["/"],
      "showRemovable": false,
      "showHidden": false,
      "showSubvolumes": false
    },
    { "type": "memory", "key": "MEM " },
    { "type": "swap", "key": "SWP " }
  ]
}
""".trimStart()

    internal fun writeFastfetchPresets(fluxHome: File?, rootfs: File?) {
        val homes = mutableListOf<File>()
        if (fluxHome != null) homes.add(fluxHome)
        if (rootfs != null) homes.add(File(rootfs, "root"))
        for (home in homes) {
            if (!home.isDirectory) continue
            val dest = File(home, ".local/share/fastfetch/presets/termux.jsonc")
            try {
                dest.parentFile?.mkdirs()
                if (!dest.canWrite() && dest.isFile) continue
                dest.writeText(TERMUX_JSONC)
                Log.i(TAG, "Wrote fastfetch preset at ${dest.absolutePath}")
            } catch (e: Exception) {
                Log.w(TAG, "fastfetch preset write failed: ${e.message}")
            }
        }
    }

    private fun profileFor(isApkGuest: Boolean, glibcPm: Boolean = false): String = when {
        isApkGuest -> DEFENSIVE_ZSHRC + "\n" + ALPINE_APK_WRAPPER
        glibcPm -> DEFENSIVE_ZSHRC + "\n" + GLIBC_PM_WRAPPER
        else -> DEFENSIVE_ZSHRC
    }

    private fun isGlibcGuest(distroId: String?): Boolean {
        val name = resolveProotName(distroId)
        return name == "fedora" || name == "void" || name == "opensuse" ||
            name == "deepin" || name == "manjaro"
    }

    /** apk-based guests: Alpine (v2) + Chimera (v3, musl). */
    private fun isApkGuest(distroId: String?): Boolean {
        val name = resolveProotName(distroId)
        return name == "alpine" || name == "chimera" ||
            (distroId?.contains("alpine", ignoreCase = true) == true) ||
            (distroId?.contains("chimera", ignoreCase = true) == true)
    }

    /** Login zsh (`su -` / `zsh -l`) does not read `.zshrc` unless also interactive. */
    internal fun ensureZprofile(home: File?) {
        if (home == null || !home.isDirectory) return
        val zprofile = File(home, ".zprofile")
        if (zprofile.isFile) return
        if (!home.canWrite() && !zprofile.canWrite()) return
        try {
            zprofile.writeText(
                "[[ -o interactive ]] || { [ -f \"\$HOME/.zshrc\" ] && . \"\$HOME/.zshrc\"; }\n"
            )
            Log.i(TAG, "Created .zprofile at ${zprofile.absolutePath}")
        } catch (e: Exception) {
            Log.w(TAG, "zprofile create failed: ${e.message}")
        }
    }

    /**
     * If guest has zsh and passwd still points flux at bash/sh, set /bin/zsh.
     * Proot rootfs under app filesDir is usually writable; chroot may skip.
     */
    internal fun ensureFluxLoginShellZsh(rootfs: File) {
        val zshBin = when {
            File(rootfs, "bin/zsh").isFile -> "/bin/zsh"
            File(rootfs, "usr/bin/zsh").isFile -> "/usr/bin/zsh"
            else -> return
        }
        val passwd = File(rootfs, "etc/passwd")
        if (!passwd.isFile || !passwd.canWrite()) return
        try {
            val lines = passwd.readLines()
            var changed = false
            val out = lines.map { line ->
                if (!line.startsWith("flux:")) return@map line
                val parts = line.split(":").toMutableList()
                if (parts.size < 7) return@map line
                val current = parts[6]
                if (current == zshBin || current.endsWith("/zsh")) return@map line
                parts[6] = zshBin
                changed = true
                parts.joinToString(":")
            }
            if (changed) {
                passwd.writeText(out.joinToString("\n") + "\n")
                Log.i(TAG, "Set flux login shell to $zshBin in ${passwd.absolutePath}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "passwd shell update failed: ${e.message}")
        }
    }

    internal fun resolveZshrc(ctx: Context, method: String, distroId: String?): File? {
        return when (method) {
            "chroot" -> {
                val root = when {
                    distroId != null -> ChrootPaths.pathForDistro(distroId)
                    else -> ChrootPaths.CHROOT_PATH
                }
                File(root, "home/flux/.zshrc")
            }
            "proot" -> {
                val prootName = resolveProotName(distroId)
                File(
                    ctx.filesDir,
                    "usr/var/lib/proot-distro/containers/$prootName/rootfs/home/flux/.zshrc"
                )
            }
            else -> null
        }
    }

    internal fun resolveProotName(distroId: String?): String {
        if (distroId.isNullOrBlank()) return "debian"
        return when (distroId) {
            "alpine", "alpine_chroot" -> "alpine"
            "debian", "debian13_chroot", "debian_chroot" -> "debian"
            else -> distroId.removeSuffix("_chroot").ifBlank { "debian" }
        }
    }

    /** True when profile hard-sources omz or always runs pokemon without guards. */
    internal fun needsRepair(text: String): Boolean {
        // String checks avoid Regex `$` = EOL pitfalls with shell `$ZSH`.
        val hardSourceOmz = text.lineSequence().any { line ->
            val t = line.trim()
            (t.startsWith("source ") || t.startsWith("source\t")) &&
                t.contains("oh-my-zsh.sh")
        }
        val guardedOmz = text.contains("[ -f") && text.contains("oh-my-zsh.sh")
        val hardPokemon = text.contains("pokemon-colorscripts") &&
            !text.contains("command -v pokemon-colorscripts")
        // Old template: unconditional source without existence guard.
        if (hardSourceOmz && !guardedOmz) return true
        if (hardPokemon) return true
        // Host TMPDIR/PROOT_TMP_DIR leaked → nested proot glue errors.
        if (!text.contains("unset PROOT_TMP_DIR")) return true
        // Missing job-control guard → zsh dies with "can't set tty pgrp" under
        // proot (tcsetpgrp ENOSYS → SIGTTIN → session SIGKILL).
        if (!text.contains("setopt no_monitor")) return true
        // Hard LANG=en_US.UTF-8 without locale -a fallback spam-fails on Fedora.
        if (!text.contains("locale -a")) return true
        return false
    }
}
