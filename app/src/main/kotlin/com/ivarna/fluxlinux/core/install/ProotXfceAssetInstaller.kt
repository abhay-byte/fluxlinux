package com.ivarna.fluxlinux.core.install

import android.content.Context
import android.util.Log
import com.ivarna.fluxlinux.core.terminal.TermuxHostPaths
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Host-side XFCE theme/icon/cursor/wallpaper install into the **proot rootfs**.
 *
 * Icons: **Papirus-Dark only** (shipped as `papirus-dark-only.tar.gz` ~690 KB —
 * not the full multi-variant Papirus pack). Light/dark UI themes both use it.
 *
 * Native host `tar` into the proot rootfs avoids slow proot extract.
 * Skips when theme + icons + cursor are already present.
 */
object ProotXfceAssetInstaller {

    private const val TAG = "ProotXfceAssets"
    private const val GH_BASE =
        "https://github.com/abhay-byte/fluxlinux/releases/download/debian-v1"

    /** Sole icon set we ship / install. */
    const val ICON_NAME = "Papirus-Dark"
    const val ICON_ASSET = "xfce4/icons/papirus-dark-only.tar.gz"
    const val ICON_FILE = "papirus-dark-only.tar.gz"

    data class Selection(
        val themeName: String,
        val themeAsset: String, // assets/xfce4/theme/…
        val iconName: String,
        val cursorName: String,
        val cursorAsset: String,
        val wallpaperAsset: String,
        val wallpaperFileName: String
    )

    fun selectionFor(theme: String): Selection =
        if (theme.equals("light", ignoreCase = true)) {
            Selection(
                themeName = "Space-light",
                themeAsset = "xfce4/theme/Space-light.tar.xz",
                iconName = ICON_NAME,
                cursorName = "Vimix-cursors",
                cursorAsset = "xfce4/cursor/01-Vimix-cursors.tar.xz",
                wallpaperAsset = "xfce4/wallpaper/fluxlinux-light.png",
                wallpaperFileName = "fluxlinux-light.png"
            )
        } else {
            Selection(
                themeName = "Space-transparency",
                themeAsset = "xfce4/theme/Space-transparency.tar.xz",
                iconName = ICON_NAME,
                cursorName = "Vimix-white-cursors",
                cursorAsset = "xfce4/cursor/02-Vimix-white-cursors.tar.xz",
                wallpaperAsset = "xfce4/wallpaper/fluxlinux-dark.png",
                wallpaperFileName = "fluxlinux-dark.png"
            )
        }

    fun prootRootfs(ctx: Context, prootName: String = "debian"): File =
        File(ctx.filesDir, "usr/var/lib/proot-distro/containers/$prootName/rootfs")

    fun isThemeInstalled(rootfs: File, themeName: String): Boolean {
        val dir = File(rootfs, "usr/share/themes/$themeName")
        return dir.isDirectory && (
            File(dir, "index.theme").isFile ||
                File(dir, "gtk-3.0").isDirectory ||
                File(dir, "xfwm4").isDirectory
            )
    }

    fun isIconInstalled(rootfs: File, iconName: String): Boolean {
        val dir = File(rootfs, "usr/share/icons/$iconName")
        return dir.isDirectory && (
            File(dir, "index.theme").isFile ||
                dir.list()?.isNotEmpty() == true
            )
    }

    fun isCursorInstalled(rootfs: File, cursorName: String): Boolean {
        val dir = File(rootfs, "usr/share/icons/$cursorName")
        return dir.isDirectory && (
            File(dir, "index.theme").isFile ||
                File(dir, "cursors").isDirectory
            )
    }

    fun isSelectionInstalled(rootfs: File, sel: Selection): Boolean =
        isThemeInstalled(rootfs, sel.themeName) &&
            isIconInstalled(rootfs, sel.iconName) &&
            isCursorInstalled(rootfs, sel.cursorName)

    /**
     * @return true if selected theme/icons/cursors are present after this call
     *   (already were, or host extract succeeded).
     */
    fun install(
        ctx: Context,
        theme: String,
        prootName: String = "debian",
        onLog: (String) -> Unit = {}
    ): Boolean {
        val app = ctx.applicationContext
        val rootfs = prootRootfs(app, prootName)
        if (!rootfs.isDirectory) {
            onLog("Proot rootfs missing — skip host theme extract")
            return false
        }
        val sel = selectionFor(theme)
        if (isSelectionInstalled(rootfs, sel)) {
            onLog("Themes/icons already installed (${sel.themeName} + ${sel.iconName}) — skip extract")
            // Still ensure wallpaper if missing (cheap)
            ensureWallpaper(app, rootfs, sel, onLog)
            return true
        }

        val cache = File(app.cacheDir, "flux_xfce_assets").also { it.mkdirs() }
        // Guest /tmp maps here with proot --shared-tmp (fallback path for script).
        val shared = File(TermuxHostPaths.TMPDIR, "flux_xfce_assets").also { it.mkdirs() }

        var ok = true
        try {
            if (!isThemeInstalled(rootfs, sel.themeName)) {
                onLog("Host-extract theme: ${sel.themeName}")
                val tar = materialize(
                    app, cache, shared,
                    assetPath = sel.themeAsset,
                    fileName = File(sel.themeAsset).name,
                    urlFallback = null,
                    onLog
                )
                val dest = File(rootfs, "usr/share/themes").also { it.mkdirs() }
                if (tar == null || !hostTarExtract(tar, dest, stripXz = true, onlyPrefix = null, onLog)) {
                    onLog("Theme extract failed for ${sel.themeName}")
                    ok = false
                }
            } else {
                onLog("Theme ${sel.themeName} already present — skip")
            }

            if (!isIconInstalled(rootfs, sel.iconName)) {
                onLog("Host-extract icons: $ICON_NAME (dark variant only)")
                val iconsTar = materialize(
                    app, cache, shared,
                    assetPath = ICON_ASSET,
                    fileName = ICON_FILE,
                    urlFallback = null,
                    onLog
                )
                val dest = File(rootfs, "usr/share/icons").also { it.mkdirs() }
                when {
                    iconsTar != null &&
                        (iconsTar.name.endsWith(".tar.gz") || iconsTar.name.endsWith(".tgz")) -> {
                        // Archive contains only Papirus-Dark/; still pass prefix for safety
                        if (!hostTarExtract(
                                iconsTar, dest,
                                stripXz = false,
                                onlyPrefix = ICON_NAME,
                                onLog
                            )
                        ) {
                            // Retry full extract if path filter fails on some tar builds
                            if (!hostTarExtract(
                                    iconsTar, dest,
                                    stripXz = false,
                                    onlyPrefix = null,
                                    onLog
                                )
                            ) {
                                onLog("Icon extract failed for $ICON_NAME")
                                ok = false
                            }
                        }
                    }
                    else -> {
                        onLog("No Papirus-Dark archive available ($ICON_FILE)")
                        ok = false
                    }
                }
            } else {
                onLog("Icons ${sel.iconName} already present — skip")
            }

            if (!isCursorInstalled(rootfs, sel.cursorName)) {
                onLog("Host-extract cursor: ${sel.cursorName}")
                val tar = materialize(
                    app, cache, shared,
                    assetPath = sel.cursorAsset,
                    fileName = File(sel.cursorAsset).name,
                    urlFallback = null,
                    onLog
                )
                val dest = File(rootfs, "usr/share/icons").also { it.mkdirs() }
                if (tar == null || !hostTarExtract(tar, dest, stripXz = true, onlyPrefix = null, onLog)) {
                    onLog("Cursor extract failed for ${sel.cursorName}")
                    ok = false
                }
            } else {
                onLog("Cursor ${sel.cursorName} already present — skip")
            }

            ensureWallpaper(app, rootfs, sel, onLog)
        } catch (e: Exception) {
            Log.e(TAG, "install failed", e)
            onLog("Host XFCE asset install error: ${e.message}")
            ok = false
        }

        val installed = isSelectionInstalled(rootfs, sel)
        if (installed) {
            onLog("Host theme/icon install complete (native extract)")
        } else if (ok) {
            onLog("Host extract finished but selection not fully detected")
        }
        return installed
    }

    private fun ensureWallpaper(
        app: Context,
        rootfs: File,
        sel: Selection,
        onLog: (String) -> Unit
    ) {
        val destDir = File(rootfs, "home/flux/Pictures/Wallpapers").also { it.mkdirs() }
        val dest = File(destDir, sel.wallpaperFileName)
        if (dest.isFile && dest.length() > 10_000) return
        try {
            app.assets.open(sel.wallpaperAsset).use { input ->
                FileOutputStream(dest).use { input.copyTo(it) }
            }
            onLog("Wallpaper: ${sel.wallpaperFileName}")
        } catch (_: Exception) {
            // optional
        }
    }

    private fun materialize(
        app: Context,
        cache: File,
        shared: File,
        assetPath: String,
        fileName: String,
        urlFallback: String?,
        onLog: (String) -> Unit
    ): File? {
        val cached = File(cache, fileName)
        if (cached.isFile && cached.length() > 1024) {
            cached.copyTo(File(shared, fileName), overwrite = true)
            return cached
        }
        // APK assets (offline)
        try {
            app.assets.open(assetPath).use { input ->
                FileOutputStream(cached).use { input.copyTo(it) }
            }
            if (cached.isFile && cached.length() > 1024) {
                onLog("Staged $fileName from app assets")
                cached.copyTo(File(shared, fileName), overwrite = true)
                return cached
            }
        } catch (_: Exception) {
        }
        if (urlFallback != null) {
            onLog("Downloading $fileName…")
            if (httpDownload(urlFallback, cached, onLog)) {
                cached.copyTo(File(shared, fileName), overwrite = true)
                return cached
            }
        }
        return cached.takeIf { it.isFile && it.length() > 1024 }
    }

    private fun httpDownload(url: String, dest: File, onLog: (String) -> Unit): Boolean {
        return try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 30_000
                readTimeout = 120_000
                instanceFollowRedirects = true
            }
            conn.inputStream.use { input ->
                FileOutputStream(dest).use { input.copyTo(it) }
            }
            conn.disconnect()
            dest.isFile && dest.length() > 1024
        } catch (e: Exception) {
            onLog("Download failed: ${e.message}")
            dest.delete()
            false
        }
    }

    /**
     * @param onlyPrefix if set, pass as additional tar path args (e.g. "Papirus-Dark")
     */
    private fun hostTarExtract(
        archive: File,
        destDir: File,
        stripXz: Boolean,
        onlyPrefix: String?,
        onLog: (String) -> Unit
    ): Boolean {
        destDir.mkdirs()
        val tarBin = when {
            File("/system/bin/tar").canExecute() -> "/system/bin/tar"
            else -> "tar"
        }
        val args = mutableListOf(tarBin)
        when {
            archive.name.endsWith(".tar.xz") || stripXz && archive.name.endsWith(".xz") -> {
                args += listOf("-xJf", archive.absolutePath, "-C", destDir.absolutePath)
            }
            archive.name.endsWith(".tar.gz") || archive.name.endsWith(".tgz") -> {
                args += listOf("-xzf", archive.absolutePath, "-C", destDir.absolutePath)
            }
            else -> {
                args += listOf("-xf", archive.absolutePath, "-C", destDir.absolutePath)
            }
        }
        if (!onlyPrefix.isNullOrBlank()) {
            // Only extract the selected icon theme tree
            args += onlyPrefix
        }
        return try {
            val pb = ProcessBuilder(args)
            pb.redirectErrorStream(true)
            val proc = pb.start()
            val out = proc.inputStream.bufferedReader().readText()
            val code = proc.waitFor()
            if (code != 0) {
                onLog("tar exit $code: ${out.take(400)}")
                false
            } else {
                true
            }
        } catch (e: Exception) {
            onLog("tar failed: ${e.message}")
            false
        }
    }

    /** Legacy icons.zip → nested tar.gz; extract only [onlyIcon] if present. */
    private fun hostUnzipNestedTars(
        zip: File,
        destDir: File,
        onlyIcon: String,
        onLog: (String) -> Unit
    ): Boolean {
        val tmp = File(zip.parentFile, "icons_unpack").also {
            it.deleteRecursively()
            it.mkdirs()
        }
        return try {
            val pb = ProcessBuilder("unzip", "-q", "-o", zip.absolutePath, "-d", tmp.absolutePath)
            pb.redirectErrorStream(true)
            val p = pb.start()
            p.inputStream.bufferedReader().readText()
            if (p.waitFor() != 0) return false
            val tars = tmp.walkTopDown().filter {
                it.isFile && (it.name.endsWith(".tar.gz") || it.name.endsWith(".tar.xz") || it.name.endsWith(".tgz"))
            }.toList()
            var any = false
            for (t in tars) {
                val ok = hostTarExtract(
                    t, destDir,
                    stripXz = t.name.endsWith(".xz"),
                    onlyPrefix = onlyIcon,
                    onLog
                )
                if (ok) any = true
            }
            // If path filter failed (archive layout differs), full extract of first tar
            if (!any && tars.isNotEmpty()) {
                any = hostTarExtract(tars.first(), destDir, stripXz = false, onlyPrefix = null, onLog)
            }
            any
        } catch (e: Exception) {
            onLog("unzip icons failed: ${e.message}")
            false
        } finally {
            tmp.deleteRecursively()
        }
    }
}
