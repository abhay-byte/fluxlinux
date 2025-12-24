# Phase 4: Advanced Features (The "Wow" Factor)

**Goal:** Unlock high-performance capabilities that distinguish FluxLinux from basic script runners.

## 4.1 Hardware Acceleration (GPU)
We need to inject optimized drivers into the container.
1.  **Driver Identification:**
    *   Read `/proc/cpuinfo` to detect GPU vendor (Adreno vs Mali).
2.  **Turnip + Zink (Adreno):**
    *   Automate downloading the `libvulkan_freedreno.so` artifacts.
    *   Set env vars: `MESA_LOADER_DRIVER_OVERRIDE=zink`, `GALLIUM_DRIVER=zink`.
3.  **Virgl (Mali/Others):**
    *   Start `virgl_test_server` in Termux context.
    *   Run Proot with `GALLIUM_DRIVER=virpipe`.

## 4.2 Rooted "Turbo" Mode (Chroot)
1.  **Root Check:** Use `Runtime.getRuntime().exec("su")`.
2.  **Mounting Logic:**
    *   Instead of `proot-distro`, we use `busybox chroot`.
    *   Script:
        ```bash
        busybox mount --bind /dev /data/local/mnthrom/dev
        busybox mount -t proc proc /data/local/mnthrom/proc
        busybox mount -t sysfs sysfs /data/local/mnthrom/sys
        busybox chroot /data/local/mnthrom /bin/bash
        ```

## 4.3 Windows Emulation (Box64 + Wine)
*   **Repo Setup:** Add `tur-repo` (Termux User Repository) which contains `box64-droid`.
*   **Wine Config:** Automate `winecfg` first run setup (headless if possible, or guide user).

## 4.4 Deliverables for Phase 4
1.  **Settings Page:** Toggle for "Enable GPU Acceleration" and "Use Root Mode".
2.  **Performance Test:** Running `glxgears` showing > 60FPS.
