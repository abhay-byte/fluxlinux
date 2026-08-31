# Worker 06 — Convert Full Distro Catalog to Play Feature Modules

## Goal

After Workers 01–05 pass, convert the supported Play distro catalog from GitHub rootfs URLs to Google Play on-demand dynamic feature modules.

## Preconditions

Workers 01–05 must be PASS. Do not start otherwise.

## Convert incrementally

Suggested batches:

1. Debian + Alpine
2. Fedora + Void + openSUSE
3. Ubuntu + Arch + Manjaro
4. Deepin + Chimera
5. Kali + Parrot

Each distro module must contain:

- `flux-distro-manifest.json`
- exact rootfs payload
- build-time hash/version metadata
- no runtime download URL fallback

## Refactor metadata

Do not make Play `DistroInstallProfile` depend on `rootfsUrl`.

Introduce a delivery identity such as:

```text
moduleName
distroId
payloadName
sha256
minimumSize
formatVersion
```

Keep non-Play URL metadata in `ivarna` implementation/source set.

## UX

For each not-installed distro:

- show that additional Linux files will be installed from Google Play
- show progress/cancel/error
- allow retry
- do not show GitHub/F-Droid binary fallback

## Tests per distro

- module resolves
- manifest id matches card
- payload integrity checks
- extraction succeeds
- first shell starts under interpreter
- reinstall/uninstall module/rootfs state handled

## Build-size check

Use bundletool/Play Console to verify every feature remains within current Play module limits.

## Acceptance

- [ ] All intended Play distros use PFD modules.
- [ ] No Play distro depends on GitHub rootfs delivery.
- [ ] All distro shells use interpreted engine.
- [ ] All modules stay inside Play size limits.
- [ ] Non-Play URL-based distro flow remains functional.
