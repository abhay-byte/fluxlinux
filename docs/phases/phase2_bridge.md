# Phase 2: The Bridge (Termux Integration)

**Goal:** Establish a secure, automated communication channel between FluxLinux and Termux using Android Intents.

## 2.1 Permission Handling
To control Termux, we need the specialized permission.
1.  **Manifest:** `<uses-permission android:name="com.termux.permission.RUN_COMMAND" />`
2.  **Runtime Request:** When the user clicks "Connect", prompt them to grant this permission if Android requires explicit approval (rare for this specific permission, but good practice).

## 2.2 Termux Configuration Helper
*problem:* Termux defaults `allow-external-apps = false`.
*Solution:*
1.  **Detection:** Check if we can run a simple `echo "hello"` command via Intent.
2.  **On Failure:** Show a helpful dialog tutorial: "Please run this command in Termux once to authorize FluxLinux."
    *   Provide a "Copy Command" button: `sed -i 's/^allow-external-apps.*/allow-external-apps = true/' ~/.termux/termux.properties && termux-reload-settings`

## 2.3 IntentFactory Class
Create a Kotlin class `TermuxIntentFactory` to generate standard intents.
```kotlin
fun buildRunCommandIntent(scriptPath: String, args: Array<String>): Intent {
    val intent = Intent()
    intent.setClassName("com.termux", "com.termux.app.RunCommandService")
    intent.action = "com.termux.RUN_COMMAND"
    intent.putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
    intent.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf(scriptPath) + args)
    intent.putExtra("com.termux.RUN_COMMAND_BACKGROUND", true) // Run without stealing focus?
    return intent
}
```

## 2.4 Deliverables for Phase 2
1.  **Connection Status:** UI indicator showing if Termux is linked.
2.  **Test Button:** A button that successfully makes a Toast appear in Termux or writes a file, proving the bridge works.
