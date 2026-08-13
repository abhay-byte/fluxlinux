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

# Generate UTF-8 first so theme / fastfetch / pokemon always render.
if [ -x /usr/local/sbin/flux-ensure-locale ] && command -v sudo >/dev/null 2>&1; then
  sudo /usr/local/sbin/flux-ensure-locale 2>/dev/null || true
fi
_have=${'$'}(locale -a 2>/dev/null || true)
_pick=""
for _c in en_US.UTF-8 en_US.utf8 C.UTF-8 C.utf8; do
  echo "${'$'}_have" | grep -qxFi "${'$'}_c" && { _pick="${'$'}_c"; break; }
done
if [ -n "${'$'}_pick" ]; then
  export LANG="${'$'}_pick" LC_ALL="${'$'}_pick"
else
  unset LC_ALL
  export LANG=C
fi
unset _have _c _pick
export PYTHONIOENCODING=UTF-8

# Host PROOT_TMP_DIR is not writable as guest uid; use guest /tmp.
unset PROOT_TMP_DIR
export TMPDIR="${'$'}{TMPDIR:-/tmp}"

# Fix XDG_RUNTIME_DIR (not set in PRoot/chroot — no systemd-logind)
export XDG_RUNTIME_DIR="${'$'}{XDG_RUNTIME_DIR:-/tmp}"

# proot does not implement tcsetpgrp — zsh job control would ENOSYS and the
# shell can get SIGTTIN-killed. Disable MONITOR (job control) for the guest.
setopt no_monitor

# Background visuals — theme/fastfetch/pokemon always run when installed.
{
  if command -v fastfetch >/dev/null 2>&1; then
    _ff="${'$'}HOME/.local/share/fastfetch/presets/termux.jsonc"
    if [ -f "${'$'}_ff" ]; then
      fastfetch --config "${'$'}_ff" 2>/dev/null || true
    else
      fastfetch --config termux 2>/dev/null || true
    fi
    unset _ff
  fi
  if command -v pokemon-colorscripts >/dev/null 2>&1; then
    pokemon-colorscripts --no-title -r 1,2,3 2>/dev/null || true
  fi
} &!

export ZSH="${'$'}{ZSH:-${'$'}HOME/.oh-my-zsh}"
if [ -f "${'$'}ZSH/oh-my-zsh.sh" ]; then
  ZSH_THEME="agnosterzak"
  DISABLE_UPDATE_PROMPT=true
  DISABLE_AUTO_UPDATE=true
  ZSH_DISABLE_COMPFIX=true
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
            if (rootfs != null) {
                ensureFluxLoginShellZsh(rootfs)
                writeLocaleProfile(rootfs)
                writeEnsureLocaleScript(rootfs)
            }
            writeFastfetchPresets(zshrc.parentFile, rootfs)
        } catch (e: Exception) {
            Log.w(TAG, "zshrc repair failed: ${e.message}")
        }
    }

    internal val LOCALE_PROFILE = """
_have=${'$'}(locale -a 2>/dev/null || true)
_pick=""
for _c in en_US.UTF-8 en_US.utf8 C.UTF-8 C.utf8; do
  echo "${'$'}_have" | grep -qxFi "${'$'}_c" && { _pick="${'$'}_c"; break; }
done
if [ -n "${'$'}_pick" ]; then
  export LANG="${'$'}_pick" LC_ALL="${'$'}_pick"
else
  unset LC_ALL
  export LANG=C
fi
unset _have _c _pick
""".trimStart()

    internal val ENSURE_LOCALE_SH = """
#!/bin/sh
# Generate directory locales when locale-archive is empty (Manjaro/Arch proot).
[ "${'$'}(id -u)" = 0 ] || exit 1
locale -a 2>/dev/null | grep -qxFi en_US.utf8 && exit 0
locale -a 2>/dev/null | grep -qxFi en_US.UTF-8 && exit 0
command -v localedef >/dev/null 2>&1 || exit 0
mkdir -p /usr/lib/locale
_map=/tmp/flux-UTF-8
if [ -f /usr/share/i18n/charmaps/UTF-8.gz ] && command -v gzip >/dev/null 2>&1; then
  gzip -dc /usr/share/i18n/charmaps/UTF-8.gz > "${'$'}_map" || true
elif [ -f /usr/share/i18n/charmaps/UTF-8 ]; then
  cp -f /usr/share/i18n/charmaps/UTF-8 "${'$'}_map" || true
fi
if [ -s "${'$'}_map" ]; then
  localedef --no-archive -c -i en_US -f "${'$'}_map" /usr/lib/locale/en_US.utf8 || true
  localedef --no-archive -c -i POSIX -f "${'$'}_map" /usr/lib/locale/C.utf8 || true
else
  localedef --no-archive -c -i en_US -f UTF-8 /usr/lib/locale/en_US.utf8 || true
fi
rm -f "${'$'}_map"
if locale -a 2>/dev/null | grep -qiE 'en_US\.(utf8|UTF-8)'; then
  printf 'LANG=en_US.UTF-8\n' > /etc/locale.conf
fi
exit 0
""".trimStart()

    internal fun writeEnsureLocaleScript(rootfs: File) {
        val dest = File(rootfs, "usr/local/sbin/flux-ensure-locale")
        try {
            dest.parentFile?.mkdirs()
            if (dest.isFile && !dest.canWrite()) return
            dest.writeText(ENSURE_LOCALE_SH)
            dest.setExecutable(true, false)
            Log.i(TAG, "Wrote ${dest.absolutePath}")
        } catch (e: Exception) {
            Log.w(TAG, "flux-ensure-locale write failed: ${e.message}")
        }
    }

    /** Existing guests: replace flux-locale.sh that blindly exports C.UTF-8. */
    internal fun writeLocaleProfile(rootfs: File) {
        val dest = File(rootfs, "etc/profile.d/flux-locale.sh")
        try {
            dest.parentFile?.mkdirs()
            if (dest.isFile && !dest.canWrite()) return
            dest.writeText(LOCALE_PROFILE)
            Log.i(TAG, "Wrote locale profile at ${dest.absolutePath}")
        } catch (e: Exception) {
            Log.w(TAG, "locale profile write failed: ${e.message}")
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
        val dests = mutableListOf<File>()
        if (fluxHome != null) {
            dests.add(File(fluxHome, ".local/share/fastfetch/presets/termux.jsonc"))
        }
        if (rootfs != null) {
            dests.add(File(rootfs, "root/.local/share/fastfetch/presets/termux.jsonc"))
            dests.add(File(rootfs, "usr/share/fastfetch/presets/termux.jsonc"))
        }
        for (dest in dests) {
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
        // Old fallback exported C.UTF-8 even when that locale is missing
        // (Manjaro ARM) → setlocale + "prompt_segment: character not in range".
        if (text.contains("export LANG=C.UTF-8") && !text.contains("grep -qxFi")) return true
        // Bare `fastfetch` lists every Android bind mount under proot.
        if (text.contains("|| fastfetch 2>/dev/null")) return true
        // Theme / pokemon must not be gated on LC_ALL (user-visible shell).
        if (text.contains("ZSH_THEME=\"\"")) return true
        if (text.contains("[ -n \"\${LC_ALL:-}\" ] && command -v pokemon-colorscripts")) return true
        return false
    }
}
