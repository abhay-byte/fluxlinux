# FluxLinux Pro Script Execution Workflow

This document provides a comprehensive overview of how the FluxLinux Pro Android app handles script execution, from user interaction to script completion and callback handling.

---

## Table of Contents

- [High-Level Architecture](#high-level-architecture)
- [Core Components](#core-components)
- [Execution Flows](#execution-flows)
  - [Base Distro Installation](#base-distro-installation)
  - [Component Installation](#component-installation)
  - [GUI Launch/Stop](#gui-launchstop)
  - [CLI Launch](#cli-launch)
  - [Distro Uninstallation](#distro-uninstallation)
- [Script Delivery Methods](#script-delivery-methods)
- [Callback Mechanism](#callback-mechanism)
- [Installation Queue System](#installation-queue-system)
- [Script Encoding & Safety](#script-encoding--safety)
- [Context-Specific Execution](#context-specific-execution)

---

## High-Level Architecture

```mermaid
flowchart TB
    subgraph "FluxLinux Pro App"
        UI[UI Screen<br/>HomeScreen, DistroScreen,<br/>InstallConfigScreen]
        TIF[TermuxIntentFactory]
        SM[ScriptManager]
        QM[InstallationQueueManager]
        MA[MainActivity<br/>Callback Handler]
        LIS[LocalInstallServer]
    end
    
    subgraph "Android System"
        CB[Clipboard]
        IS[Intent System]
    end
    
    subgraph "Termux"
        RCS[RunCommandService]
        BASH[Bash Shell]
        PROOT[proot-distro]
    end
    
    subgraph "Linux Environment"
        DISTRO[PRoot Container<br/>or Chroot]
        SCRIPT[Script Execution]
    end
    
    subgraph "Callback Path"
        AM[am start<br/>Deep Link]
        DL[fluxlinux://callback]
    end
    
    UI --> |1. User Action| TIF
    TIF --> |2. Load Script| SM
    SM --> |3. Read Assets| SM
    TIF --> |4. Build Intent| IS
    IS --> |5. Start Service| RCS
    RCS --> |6. Execute| BASH
    BASH --> |7a. Run Direct<br/>or| SCRIPT
    BASH --> |7b. proot-distro login| PROOT
    PROOT --> |8. Execute in Container| DISTRO
    DISTRO --> SCRIPT
    SCRIPT --> |9. Send Callback| AM
    AM --> |10. Deep Link| DL
    DL --> |11. Handle| MA
    MA --> |12. Update State| UI
    
    UI -.-> |Manual Flow| CB
    CB -.-> |User Paste| BASH
    LIS -.-> |Serve Script| BASH
```

---

## Core Components

### TermuxIntentFactory

**File:** `core/data/TermuxIntentFactory.kt`

The central factory for creating all Termux-related intents. Key responsibilities:

| Method | Purpose |
|--------|---------|
| `buildRunCommandIntent()` | Creates base intent for Termux RUN_COMMAND |
| `buildInstallIntent()` | Builds distro installation intent |
| `buildUninstallIntent()` | Builds distro removal intent |
| `buildLaunchCliIntent()` | Builds CLI entry intent |
| `buildLaunchGuiIntent()` | Builds GUI launch intent |
| `buildStopGuiIntent()` | Builds GUI stop intent |
| `buildRunFeatureScriptIntent()` | Builds component script intent |
| `buildRunRootScriptIntent()` | Builds root script intent |
| `getBaseInstallScript()` | Generates compound installation script |
| `getSafeRootManualCommand()` | Generates safe clipboard command |

**Intent Constants:**
```kotlin
private const val ACTION_RUN_COMMAND = "com.termux.RUN_COMMAND"
private const val EXTRA_COMMAND_PATH = "com.termux.RUN_COMMAND_PATH"
private const val EXTRA_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS"
private const val EXTRA_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
private const val EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"
```

---

### ScriptManager

**File:** `core/data/ScriptManager.kt`

Reads script files from the app's assets folder:

```kotlin
class ScriptManager(private val context: Context) {
    fun getScriptContent(fileName: String): String {
        val inputStream = context.assets.open("scripts/$fileName")
        return inputStream.bufferedReader().use { it.readText() }
    }
}
```

**Asset Path Mapping:**
- `scripts/termux/*` → Termux scripts
- `scripts/common/*` → PRoot/Chroot common scripts
- `scripts/chroot/*` → Chroot-specific scripts

---

### InstallationQueueManager

**File:** `core/utils/InstallationQueueManager.kt`

Manages multi-step installations with reactive state:

```mermaid
stateDiagram-v2
    [*] --> Idle
    Idle --> Enqueuing: enqueue(tasks)
    Enqueuing --> Processing: next()
    Processing --> Processing: next() [queue not empty]
    Processing --> Complete: next() [queue empty]
    Complete --> Idle: clear()
    Processing --> Failed: Error
    Failed --> Idle: clear()
```

**Task Types:**
| Type | Description | Execution |
|------|-------------|-----------|
| `BASE_INSTALL` | Base distro setup | Manual (clipboard) |
| `HW_ACCEL` | Hardware acceleration | Automatic (intent) |
| `COMPONENT` | Feature/component script | Automatic (intent) |

---

### LocalInstallServer

**File:** `core/utils/LocalInstallServer.kt`

A lightweight HTTP server for serving large scripts to Termux:

```mermaid
sequenceDiagram
    participant App as FluxLinux Pro App
    participant Server as LocalInstallServer
    participant Clipboard as Clipboard
    participant Termux as Termux
    
    App->>Server: start(script)
    Server-->>App: port (ephemeral)
    App->>Clipboard: Copy "curl http://127.0.0.1:PORT/install | bash"
    App->>Termux: Open Termux
    Note over Termux: User pastes command
    Termux->>Server: GET /install
    Server-->>Termux: HTTP 200 + Script Content
    Server->>Server: onDownload callback
    Server->>Server: stop()
    Termux->>Termux: Execute script
```

**Why use a server?**
- Avoids clipboard size limits
- Handles special characters safely
- Supports GZIP compression for large scripts
- Cleaner user experience

---

### MainActivity (Callback Handler)

**File:** `MainActivity.kt`

Handles deep link callbacks from scripts:

```kotlin
override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    handleScriptCallback(intent)
}

private fun handleScriptCallback(intent: Intent) {
    if (intent.action == Intent.ACTION_VIEW && 
        intent.data?.scheme == "fluxlinux") {
        val result = uri?.getQueryParameter("result")
        val scriptName = uri?.getQueryParameter("name")
        // Process result, update state, advance queue
    }
}
```

---

## Execution Flows

### Base Distro Installation

This is the most complex flow, involving manual user interaction:

```mermaid
sequenceDiagram
    participant User
    participant UI as InstallConfigScreen
    participant TIF as TermuxIntentFactory
    participant QM as QueueManager
    participant LIS as LocalInstallServer
    participant CB as Clipboard
    participant Termux
    participant Script
    participant MA as MainActivity
    
    User->>UI: Click "Install"
    UI->>QM: Clear & Enqueue Tasks
    Note over QM: [BASE_INSTALL, HW_ACCEL, Components...]
    UI->>TIF: getBaseInstallScript(distro)
    TIF->>TIF: Compose script (GZIP + Base64)
    TIF-->>UI: Compressed script
    UI->>LIS: start(script)
    LIS-->>UI: Port number
    UI->>CB: Copy curl command
    UI->>User: Show dialog "Open Termux"
    User->>Termux: Open & Paste
    Termux->>LIS: GET /install
    LIS-->>Termux: Script content
    Termux->>Script: Execute
    Note over Script: Install distro, configure, etc.
    Script->>Script: am start fluxlinux://callback
    Script-->>MA: Deep Link Callback
    MA->>MA: handleScriptCallback()
    MA->>QM: next()
    alt Has more tasks
        MA->>TIF: buildRunFeatureScriptIntent()
        TIF-->>Termux: Start Service
        Note over Termux: Auto-execute next script
    else Queue empty
        MA->>MA: Show completion toast
    end
```

---

### Component Installation

Automatic flow after base installation or from Distro Settings:

```mermaid
sequenceDiagram
    participant User
    participant UI as DistroSettingsScreen
    participant TIF as TermuxIntentFactory
    participant SM as ScriptManager
    participant Termux as Termux RunCommandService
    participant PROOT as proot-distro
    participant Script as Component Script
    participant MA as MainActivity
    
    User->>UI: Select Component
    UI->>SM: getScriptContent("common/setup_*.sh")
    SM-->>UI: Script content
    UI->>TIF: buildRunFeatureScriptIntent(distroId, script, callbackName)
    Note over TIF: Encode script as Base64
    TIF-->>UI: Intent
    UI->>Termux: startService(intent)
    Termux->>Termux: bash -c "echo B64 | base64 -d > /tmp/script.sh"
    Termux->>PROOT: proot-distro login distro --shared-tmp
    PROOT->>Script: bash /tmp/flux_feature.sh
    Script->>Script: Install components...
    Script->>Script: am start fluxlinux://callback
    Script-->>MA: Callback
    MA->>MA: Update StateManager
    MA->>UI: Trigger refresh
```

---

### GUI Launch/Stop

```mermaid
flowchart TD
    subgraph "GUI Launch Flow"
        A[User clicks Start GUI] --> B{Distro Type?}
        B -->|PRoot| C[buildLaunchGuiIntent]
        B -->|Chroot| D[buildLaunchGuiIntent<br/>+ VirGL + PulseAudio]
        C --> E["bash $HOME/start_gui.sh distroId"]
        D --> F["su -c 'sh /data/local/tmp/start_*.sh'"]
        E --> G[start_gui.sh]
        F --> H[start_*_gui.sh]
        G --> I[Kill old X11 processes]
        H --> I
        I --> J[Start PulseAudio]
        J --> K[Start Termux:X11]
        K --> L{Distro Type?}
        L -->|PRoot| M[proot-distro login --shared-tmp]
        L -->|Chroot| N[chroot with mounts]
        M --> O[dbus-launch startxfce4]
        N --> O
    end
    
    subgraph "GUI Stop Flow"
        P[User clicks Stop GUI] --> Q{Distro Type?}
        Q -->|PRoot| R["bash $HOME/stop_gui.sh distroId"]
        Q -->|Chroot| S["su -c 'sh /data/local/tmp/stop_*.sh'"]
        R --> T[Kill XFCE4 processes]
        S --> T
        T --> U[Stop Termux:X11]
        U --> V[Stop PulseAudio]
        V --> W[Clean up sockets]
    end
```

---

### CLI Launch

```mermaid
flowchart LR
    A[User clicks CLI] --> B{Distro Type?}
    B -->|termux| C[Echo message]
    B -->|PRoot| D["proot-distro login distroId --user flux"]
    B -->|debian13_chroot| E["su -c 'sh /data/local/tmp/enter_debian13.sh'"]
    B -->|debian_chroot| F["su -c 'sh /data/local/tmp/enter_debian.sh'"]
    B -->|arch_chroot| G["su -c 'sh /data/local/tmp/enter_arch.sh'"]
    D --> H[Interactive Shell]
    E --> H
    F --> H
    G --> H
```

---

### Distro Uninstallation

```mermaid
flowchart TD
    A[User clicks Uninstall] --> B{Distro Type?}
    
    B -->|termux| C["pkg uninstall xfce4..."]
    
    B -->|PRoot| D{Try proot-distro remove}
    D -->|Success| E[Distro Removed]
    D -->|Fail| F[Retry Once]
    F -->|Success| E
    F -->|Fail| G[Manual rm -rf rootfs]
    G --> E
    
    B -->|debian13_chroot| H["su -c 'unmount + rm -rf'"]
    B -->|debian_chroot| I["su -c 'unmount + rm -rf'"]
    
    H --> J[Remove launcher scripts]
    I --> J
    E --> K[Send callback]
    J --> K
    C --> K
    
    K --> L[MainActivity receives callback]
    L --> M[StateManager.clearDistroState]
    M --> N[UI Updates]
```

---

## Script Delivery Methods

FluxLinux Pro uses multiple methods to deliver scripts to Termux:

### 1. Direct Intent (Small Scripts)

```mermaid
flowchart LR
    A[App] -->|Intent with -c arg| B[Termux Service]
    B --> C[bash -c 'script content']
```

**Used for:** Simple commands, quick actions

### 2. Base64 Inline (Medium Scripts)

```mermaid
flowchart LR
    A[App] -->|Base64 encoded| B[Termux]
    B -->|echo B64 ⎮ base64 -d| C[Decode]
    C --> D[Execute]
```

**Used for:** Component scripts, feature installations

### 3. GZIP + Base64 + LocalServer (Large Scripts)

```mermaid
flowchart LR
    A[App] -->|GZIP + Base64| B[LocalInstallServer]
    B -->|curl localhost:PORT| C[Termux]
    C -->|gunzip| D[Decompress]
    D --> E[Execute]
```

**Used for:** Base installation (combines multiple scripts)

### 4. Clipboard + Manual (Root Required)

```mermaid
flowchart LR
    A[App] -->|Copy to clipboard| B[Clipboard]
    B -->|User paste| C[su shell]
    C --> D[Execute as root]
```

**Used for:** Chroot installations requiring root

---

## Callback Mechanism

### Deep Link Format

```
fluxlinux://callback?result=<result>&name=<name>[&components=<list>]
```

| Parameter | Values | Description |
|-----------|--------|-------------|
| `result` | `success`, `failure` | Script execution result |
| `name` | Script identifier | e.g., `setup_termux`, `distro_install_debian` |
| `components` | Comma-separated IDs | (Legacy) Installed components |

### Script Callback Implementation

```bash
# Success callback
am start -a android.intent.action.VIEW \
    -d "fluxlinux://callback?result=success&name=component_id"

# Failure callback
am start -a android.intent.action.VIEW \
    -d "fluxlinux://callback?result=failure&name=component_id"
```

### Callback Processing Flow

```mermaid
flowchart TD
    A[Deep Link Received] --> B{Parse URI}
    B --> C{result == success?}
    
    C -->|Yes| D{name starts with distro_install_?}
    C -->|No| E[Show Error Toast]
    E --> F[Clear Queue]
    
    D -->|Yes| G[StateManager.setDistroInstalled]
    D -->|No| H{name starts with distro_uninstall_?}
    
    H -->|Yes| I[StateManager.clearDistroState]
    H -->|No| J{Check Queue}
    
    J -->|Task matches| K[Mark Component Installed]
    J -->|No match| L[StateManager.setScriptStatus]
    
    G --> M[processNextInstallTask]
    I --> M
    K --> M
    L --> M
    
    M --> N{Queue has pending?}
    N -->|Yes| O[Execute next task]
    N -->|No| P[Show Completion Toast]
    O --> Q[Wait for callback...]
```

---

## Installation Queue System

### Task Lifecycle

```mermaid
stateDiagram-v2
    [*] --> Queued: enqueue()
    Queued --> Current: next()
    Current --> Executing: startService/startActivity
    Executing --> WaitingCallback: Script Running
    WaitingCallback --> Complete: Callback Received
    Complete --> [*]: Queue Empty
    Complete --> Queued: More Tasks
    
    WaitingCallback --> Failed: Error/Timeout
    Failed --> [*]: Queue Cleared
```

### Queue State Flow

```kotlin
// Enqueue Phase
queueManager.clear()
queueManager.enqueue(listOf(
    InstallTask(type = BASE_INSTALL, isManual = true, ...),
    InstallTask(type = HW_ACCEL, scriptName = "common/setup_hw_accel_debian.sh", ...),
    InstallTask(type = COMPONENT, scriptName = "common/setup_webdev_debian.sh", ...)
))

// Processing Phase
val task = queueManager.next()  // Gets BASE_INSTALL
// ... Manual execution via clipboard ...
// ... Callback received ...

val task = queueManager.next()  // Gets HW_ACCEL
// ... Auto execution via intent ...
// ... Callback received ...

// Completion
if (!queueManager.hasPending()) {
    StateManager.setDistroInstalled(context, distroId, true)
    queueManager.clear()
}
```

---

## Script Encoding & Safety

### Base64 Encoding

Scripts are encoded to avoid shell escaping issues:

```kotlin
val scriptB64 = android.util.Base64.encodeToString(
    scriptContent.toByteArray(), 
    android.util.Base64.NO_WRAP
)
```

### GZIP Compression

For large scripts (base install combines multiple):

```kotlin
val byteArrayOutputStream = java.io.ByteArrayOutputStream()
java.util.zip.GZIPOutputStream(byteArrayOutputStream).use { 
    it.write(safeScript.toByteArray()) 
}
val fullScriptGzipB64 = android.util.Base64.encodeToString(
    byteArrayOutputStream.toByteArray(), 
    android.util.Base64.NO_WRAP
)
```

### Heredoc with Random EOF

To prevent command injection:

```kotlin
val eofMarker = "EOF_FLUX_INSTALL_${System.currentTimeMillis()}"
val command = """
    cat << '$eofMarker' > script.sh
    $scriptContent
    $eofMarker
    bash script.sh
""".trimIndent()
```

---

## Context-Specific Execution

### PRoot Execution

```bash
# Command wrapping for PRoot
proot-distro login $distroId --shared-tmp -- bash -c "
    export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
    bash /data/data/com.termux/files/home/flux_feature.sh
"
```

### Chroot Execution

```bash
# Command wrapping for Chroot (via run_debian13_root.sh helper)
ROOT_RUNNER="/data/local/tmp/run_debian13_root.sh"
if [ -f "$ROOT_RUNNER" ]; then
    sh "$ROOT_RUNNER" "bash /tmp/flux_feature.sh"
else
    # Fallback: inline mounts
    mnt=/data/local/tmp/chrootDebian13
    mount -t proc proc $mnt/proc
    mount -o bind /dev $mnt/dev
    # ... more mounts ...
    busybox chroot $mnt /bin/su - root -c "bash /tmp/flux_feature.sh"
fi
```

### Root Shell Execution

```bash
# Direct root execution
su -c '
    echo "BASE64_SCRIPT" | base64 -d > /data/local/tmp/task.sh
    chmod +x /data/local/tmp/task.sh
    sh /data/local/tmp/task.sh
    rm -f /data/local/tmp/task.sh
'
```

---

## Sequence Diagrams by User Action

### Complete Install Flow

```mermaid
sequenceDiagram
    actor User
    participant App as FluxLinux Pro
    participant Queue as QueueManager
    participant Server as LocalInstallServer  
    participant Termux
    participant Distro as Linux Env
    
    User->>App: Select Distro + Components
    User->>App: Click Install
    
    Note over App,Queue: Phase 1: Queue Setup
    App->>Queue: clear()
    App->>Queue: enqueue([BASE, HW_ACCEL, Components])
    
    Note over App,Server: Phase 2: Base Install (Manual)
    App->>Server: start(compressedScript)
    Server-->>App: port
    App->>App: Copy curl command
    App->>Termux: Open Termux
    
    User->>Termux: Paste & Execute
    Termux->>Server: GET /install
    Server-->>Termux: Script
    Termux->>Distro: Install base system
    Distro-->>App: Callback (success)
    
    Note over App,Termux: Phase 3: Auto Components
    App->>Queue: next() → HW_ACCEL
    App->>Termux: startService(HW_ACCEL intent)
    Termux->>Distro: Execute setup_hw_accel
    Distro-->>App: Callback (success)
    
    App->>Queue: next() → Component 1
    App->>Termux: startService(Component intent)
    Termux->>Distro: Execute component script
    Distro-->>App: Callback (success)
    
    Note over App: Repeat for all components...
    
    App->>Queue: next() → null (empty)
    App->>App: Show completion 🎉
```

---

## Error Handling

### Script Error Handling

```bash
# Pattern used in scripts
handle_error() {
    echo "❌ FluxLinux Pro Error: Script failed at step: $1"
    read -p "Press Enter to acknowledge error and exit..."
    exit 1
}

# Usage
apt install -y package || handle_error "Package Installation"
```

### App-Side Error Handling

```kotlin
// On failure callback
if (result != "success") {
    Toast.makeText(this, "Task '$scriptName' failed! ❌", Toast.LENGTH_LONG).show()
    InstallationQueueManager.clear() // Stop queue on failure
}
```

---

## See Also

- [Scripts Reference](scripts_reference.md) - Complete script documentation
- [Architecture Documentation](architecture.md) - Overall app architecture
- [Components Documentation](components.md) - Available installation components
