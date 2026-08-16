package com.ivarna.fluxlinux.ui.screens

import androidx.compose.runtime.Composable
import com.ivarna.fluxlinux.core.chroot.ChrootInfoStore

/**
 * Legacy wrapper for ChrootSettingsScreen.
 * Delegates to parameterized [ChrootStorageDetailScreen].
 */
@Composable
fun ChrootSettingsScreen(
    onBack: () -> Unit,
    onNavigateToInstall: (() -> Unit)? = null
) {
    ChrootStorageDetailScreen(
        distroId = ChrootInfoStore.DEFAULT_DEBIAN_ID,
        onBack = onBack,
        onNavigateToInstall = onNavigateToInstall
    )
}
