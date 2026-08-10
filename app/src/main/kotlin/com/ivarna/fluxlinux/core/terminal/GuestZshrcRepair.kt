package com.ivarna.fluxlinux.core.terminal

import android.content.Context
import android.util.Log
import com.ivarna.fluxlinux.core.root.ChrootPaths
import java.io.File

/**
 * One-shot repair for guest `~/.zshrc` that hard-sources missing oh-my-zsh or
 * invokes missing `pokemon-colorscripts` (seen on device after partial network
 * customization installs). Keeps the same optimized profile shape as
 * `setup_customization_debian.sh`, but guards every optional tool.
 *
 * Proot rootfs lives under the app filesDir (writable). Chroot is under
 * `/data/local/tmp` and is only patched when the file is world-writable or
 * already app-owned — otherwise skip (needs re-run customization as root).
 */
object GuestZshrcRepair {

    private const val TAG = "GuestZshrcRepair"

    /** Defensive zshrc — mirrors setup_customization_debian.sh (post-fix). */
    private val DEFENSIVE_ZSHRC = """
# PATH setup - local bin, npm global modules
export PATH="${'$'}HOME/.local/bin:/opt/nodejs/bin:${'$'}PATH"

# Setup Locales
export LANG=en_US.UTF-8
export LC_ALL=en_US.UTF-8

# Fix XDG_RUNTIME_DIR (not set in PRoot/chroot — no systemd-logind)
export XDG_RUNTIME_DIR="${'$'}{XDG_RUNTIME_DIR:-/tmp}"

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
     * Patch guest flux `.zshrc` when it looks like the old non-defensive template.
     * Safe to call on every session open — no-ops when already fixed or missing.
     */
    fun repairIfNeeded(ctx: Context, method: String) {
        val zshrc = when (method) {
            "chroot" -> File(ChrootPaths.CHROOT_PATH, "home/flux/.zshrc")
            "proot" -> File(
                ctx.filesDir,
                "usr/var/lib/proot-distro/containers/debian/rootfs/home/flux/.zshrc"
            )
            else -> return
        }
        try {
            if (!zshrc.isFile) return
            val text = zshrc.readText()
            if (!needsRepair(text)) return
            if (!zshrc.canWrite()) {
                Log.w(TAG, "zshrc needs repair but not writable: ${zshrc.absolutePath}")
                return
            }
            zshrc.writeText(DEFENSIVE_ZSHRC)
            Log.i(TAG, "Repaired defensive .zshrc at ${zshrc.absolutePath}")
        } catch (e: Exception) {
            Log.w(TAG, "zshrc repair failed: ${e.message}")
        }
    }

    /** True when profile hard-sources omz or always runs pokemon without guards. */
    internal fun needsRepair(text: String): Boolean {
        // Match literal `$ZSH` in guest files (Kotlin `$` would interpolate otherwise).
        val hardSourceOmz = Regex("""source\s+["']?${'$'}ZSH/oh-my-zsh\.sh["']?""").containsMatchIn(text)
        val guardedOmz = text.contains("[ -f \"\$ZSH/oh-my-zsh.sh\" ]") ||
            text.contains("[ -f \$ZSH/oh-my-zsh.sh ]") ||
            Regex("""\[\s*-f\s+["']?${'$'}ZSH/oh-my-zsh\.sh["']?\s*]""").containsMatchIn(text)
        val hardPokemon = text.contains("pokemon-colorscripts") &&
            !text.contains("command -v pokemon-colorscripts")
        // Old template: unconditional source without existence guard.
        if (hardSourceOmz && !guardedOmz) return true
        if (hardPokemon) return true
        return false
    }
}
