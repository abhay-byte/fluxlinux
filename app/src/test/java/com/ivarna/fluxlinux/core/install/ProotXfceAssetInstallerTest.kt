package com.ivarna.fluxlinux.core.install

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ProotXfceAssetInstallerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun selection_dark_uses_papirusDark_and_transparency() {
        val sel = ProotXfceAssetInstaller.selectionFor("dark")
        assertEquals("Space-transparency", sel.themeName)
        assertEquals("Papirus-Dark", sel.iconName)
        assertEquals(ProotXfceAssetInstaller.ICON_NAME, sel.iconName)
        assertEquals("Vimix-white-cursors", sel.cursorName)
        assertTrue(sel.wallpaperFileName.contains("dark"))
    }

    @Test
    fun selection_light_also_uses_papirusDark_only() {
        val sel = ProotXfceAssetInstaller.selectionFor("light")
        assertEquals("Space-light", sel.themeName)
        // No full Papirus / Papirus-Light — dark variant only for all themes
        assertEquals("Papirus-Dark", sel.iconName)
        assertEquals(ProotXfceAssetInstaller.ICON_NAME, sel.iconName)
        assertEquals("Vimix-cursors", sel.cursorName)
    }

    @Test
    fun isThemeInstalled_requires_marker() {
        val root = tmp.newFolder("rootfs")
        val themeDir = File(root, "usr/share/themes/Space-transparency").also { it.mkdirs() }
        assertFalse(ProotXfceAssetInstaller.isThemeInstalled(root, "Space-transparency"))
        File(themeDir, "gtk-3.0").mkdirs()
        assertTrue(ProotXfceAssetInstaller.isThemeInstalled(root, "Space-transparency"))
    }

    @Test
    fun isIconInstalled_detects_index_or_nonempty() {
        val root = tmp.newFolder("rootfs2")
        val iconDir = File(root, "usr/share/icons/Papirus-Dark").also { it.mkdirs() }
        assertFalse(ProotXfceAssetInstaller.isIconInstalled(root, "Papirus-Dark"))
        File(iconDir, "index.theme").writeText("[Icon Theme]\nName=Papirus-Dark\n")
        assertTrue(ProotXfceAssetInstaller.isIconInstalled(root, "Papirus-Dark"))
    }

    @Test
    fun icon_asset_must_not_end_in_gz_aapt2_rule() {
        // aapt2 auto-decompresses *.gz assets (renames to *.tar) — asset paths
        // opened via AssetManager must never end in .gz.
        assertFalse(ProotXfceAssetInstaller.ICON_ASSET.endsWith(".gz"))
        assertFalse(ProotXfceAssetInstaller.ICON_FILE.endsWith(".gz"))
        assertTrue(ProotXfceAssetInstaller.ICON_FILE.endsWith(".tar.xz"))
        assertFalse(ProotXfceAssetInstaller.ICON_CATEGORIES_ASSET.endsWith(".gz"))
        assertTrue(ProotXfceAssetInstaller.ICON_CATEGORIES_FILE.endsWith(".tar.xz"))
    }

    @Test
    fun isSelectionInstalled_all_or_nothing() {
        val root = tmp.newFolder("rootfs3")
        val sel = ProotXfceAssetInstaller.selectionFor("dark")
        assertFalse(ProotXfceAssetInstaller.isSelectionInstalled(root, sel))
        File(root, "usr/share/themes/${sel.themeName}/xfwm4").mkdirs()
        File(root, "usr/share/icons/${sel.iconName}/index.theme").also {
            it.parentFile!!.mkdirs()
            it.writeText("x")
        }
        File(root, "usr/share/icons/${sel.cursorName}/cursors").mkdirs()
        assertTrue(ProotXfceAssetInstaller.isSelectionInstalled(root, sel))
    }
}
