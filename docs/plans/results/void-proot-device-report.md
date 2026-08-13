# Void proot — device report

**Date:** 2026-08-13  
**Package:** `com.ivarna.fluxlinux`

| Step | Result |
|------|--------|
| Rootfs extract | **PASS** |
| Family (xbps XFCE) | **PASS** after `xbps-install -u xbps` (must update xbps before other pkgs). Full `-Syu` skipped in script (too large). |
| `sudo -n id` | **PASS** `uid=0(root)` |
| XFCE on embedded X11 | **PASS** `void_proot_xfce_pass.png` — panel, desktop icons, flux user. PolicyKit dialog (no systemd) is harmless. |

Family log: `void Void setup complete!` + `gpu_mode=virgl`.
