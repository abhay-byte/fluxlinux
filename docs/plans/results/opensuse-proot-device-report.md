# openSUSE proot — device report

**Date:** 2026-08-13  
**Package:** `com.ivarna.fluxlinux`

| Step | Result |
|------|--------|
| Rootfs extract | **PASS** |
| Family XFCE (zypper, no curl / libcurl-mini kept) | **PASS** |
| `sudo -n id` as flux | **PASS** after `libevp_md2.so` preload (`uid=0(root)`) |
| zypper present | **PASS** |
| XFCE on embedded X11 | **PASS** `opensuse_proot_xfce_theme.png` / `opensuse_proot_xfce_theme2.png` — Space wallpaper, Menu/Applications, flux user. Default TW panel may prompt once about a missing pulseaudio plugin. |

## Fixes

- Never replace `libcurl-mini` with full curl (pulls libldap → OpenSSL `EVP_md2` missing).
- Ship `libevp_md2.so` + `/etc/ld.so.preload` so setuid sudo can resolve `EVP_md2@OPENSSL_3.0.0`.
- Native/script bwrap shim so glycin PNG/SVG loaders do not abort xfce4-panel.
