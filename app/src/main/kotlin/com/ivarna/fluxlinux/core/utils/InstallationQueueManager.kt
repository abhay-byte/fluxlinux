package com.ivarna.fluxlinux.core.utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class InstallTask(
    val id: String,
    val name: String,
    val type: TaskType,
    val scriptName: String? = null,
    val isManual: Boolean = false,
    val distroId: String, // Required for building intents
    val extraEnv: Map<String, String> = emptyMap(),
    val isUninstall: Boolean = false
)

enum class TaskType {
    BASE_INSTALL,
    HW_ACCEL,
    COMPONENT
}


data class InstallationState(
    val isInstalling: Boolean = false,
    val currentTaskName: String = "",
    val progressCurrent: Int = 0,
    val progressTotal: Int = 0,
    val currentDistroId: String? = null,
    val cancelledByUser: Boolean = false
)

object InstallationQueueManager {
    private val queue = ArrayDeque<InstallTask>()
    var currentTask: InstallTask? = null
        private set

    var activeDistroId: String? = null
        private set

    // Reactive State
    private val _installState = MutableStateFlow(InstallationState())
    val installState: StateFlow<InstallationState> = _installState.asStateFlow()

    fun enqueue(tasks: List<InstallTask>) {
        if (tasks.isNotEmpty()) {
            activeDistroId = tasks.first().distroId
        }
        queue.addAll(tasks)
        
        // Initial State Update
        _installState.value = _installState.value.copy(
            isInstalling = true,
            progressTotal = tasks.size, // This adds to existing queue size ideally, but we usually clear first
            currentDistroId = activeDistroId
        )
    }

    fun next(): InstallTask? {
        currentTask = queue.removeFirstOrNull()
        if (currentTask != null) {
            // Update State
            _installState.value = _installState.value.copy(
                currentTaskName = currentTask?.name ?: "Processing...",
                progressCurrent = _installState.value.progressCurrent + 1
            )
        }
        return currentTask
    }

    fun hasPending(): Boolean = queue.isNotEmpty()

    fun clear() {
        queue.clear()
        currentTask = null
        activeDistroId = null
        _installState.value = InstallationState()
    }

    fun cancelByUser() {
        _installState.value = _installState.value.copy(
            isInstalling = false,
            cancelledByUser = true,
            currentTaskName = "",
            progressCurrent = 0,
            progressTotal = 0
        )
        queue.clear()
        currentTask = null
        activeDistroId = null
    }
    
    fun peek(): InstallTask? = queue.firstOrNull()
}
