package com.ivarna.fluxlinux.core.legacy

import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import com.ivarna.fluxlinux.core.utils.StateManager

object LegacyTermuxCallbacks {
    private const val TAG = "LegacyTermux"

    fun handle(ctx: Context, result: String?, scriptName: String, uri: Uri?) {
        handle(ctx, result, scriptName) { key ->
            try {
                uri?.getQueryParameter(key)
            } catch (_: Exception) {
                null
            }
        }
    }

    fun handle(
        ctx: Context,
        result: String?,
        scriptName: String,
        getParam: (String) -> String?
    ) {
        if (!scriptName.startsWith("legacy_termux_")) return
        when {
            scriptName == "legacy_termux_list" -> {
                if (result == "success") {
                    val rawIds = getParam("ids")
                    val rawBytes = getParam("bytes")
                    val rawLayouts = getParam("layouts")

                    val idsList = if (rawIds.isNullOrBlank()) {
                        emptyList()
                    } else {
                        rawIds.split(",").filter { it.isNotBlank() }
                    }

                    // Validate ids allowlist: reject whole payload if any token is unsafe
                    if (idsList.any { !LegacyTermuxBridge.isSafeProotId(it) }) {
                        Log.e(TAG, "Malformed ids in list callback: $rawIds")
                        LegacyTermuxStore.saveError(ctx, "Scan returned an invalid list.")
                        StateManager.triggerRefresh()
                        return
                    }

                    val bytesList = rawBytes?.split(",")?.map { it.toLongOrNull() } ?: emptyList()
                    val layoutsList = rawLayouts?.split(",") ?: emptyList()

                    val rows = idsList.mapIndexed { index, id ->
                        val b = bytesList.getOrNull(index)
                        val layout = layoutsList.getOrNull(index)
                        val path = LegacyTermuxBridge.hostPath(id, layout)
                        LegacyTermuxStore.Row(
                            id = id,
                            bytes = b,
                            layout = layout,
                            hostPath = path
                        )
                    }
                    LegacyTermuxStore.saveScan(ctx, rows, System.currentTimeMillis())
                    LegacyTermuxStore.setPingOk(ctx)
                    StateManager.triggerRefresh()
                } else {
                    LegacyTermuxStore.saveError(ctx, "Scan failed.")
                    StateManager.triggerRefresh()
                }
            }
            scriptName == "legacy_termux_ping" -> {
                if (result == "success") {
                    LegacyTermuxStore.setPingOk(ctx)
                    StateManager.triggerRefresh()
                }
            }
            scriptName.startsWith("legacy_termux_uninstall_") -> {
                val distroId = scriptName.removePrefix("legacy_termux_uninstall_")
                if (!LegacyTermuxBridge.isSafeProotId(distroId)) {
                    Log.e(TAG, "Unsafe distroId in uninstall callback: $distroId")
                    return
                }
                if (result == "success") {
                    val removed = LegacyTermuxStore.remove(ctx, distroId)
                    LegacyTermuxStore.recordActionFinished(ctx)
                    if (removed) {
                        StateManager.triggerRefresh()
                    } else {
                        StateManager.triggerRefresh()
                    }
                    val msg = if (distroId == "debian") {
                        "Leftover debian removed from Termux"
                    } else {
                        "Container $distroId removed from Termux"
                    }
                    try {
                        Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
                    } catch (_: Exception) {}
                } else {
                    val reason = getParam("reason") ?: "unknown"
                    Log.w(TAG, "Termux uninstall failed for $distroId: $reason")
                    LegacyTermuxStore.recordActionFinished(ctx, "Termux did not remove $distroId ($reason)")
                    StateManager.triggerRefresh()
                    try {
                        Toast.makeText(ctx, "Termux did not remove $distroId", Toast.LENGTH_LONG).show()
                    } catch (_: Exception) {}
                }
            }
            else -> {
                Log.d(TAG, "Ignored legacy_termux callback: $scriptName")
            }
        }
    }
}
