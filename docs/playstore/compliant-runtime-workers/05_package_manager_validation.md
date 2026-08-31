# Worker 05 — Package Manager Validation Inside Interpreter

## Goal

Prove the main reason for the new architecture: packages downloaded from normal Linux repositories remain guest code and continue to execute only inside the interpreter/VM.

## Start with

- Alpine `apk`, or
- Debian `apt`

Use whichever distro passed Worker 03.

## Required scenario

Fresh Play-delivered rootfs:

```bash
# Debian example
apt update
apt install -y curl
curl --version
```

or:

```bash
apk update
apk add curl
curl --version
```

The installed `curl` did not exist in the Play-delivered base payload and therefore proves execution of later externally acquired guest code.

## Verify

- package manager itself runs through QEMU
- downloaded ELF stays inside guest filesystem
- downloaded ELF is never handed to Android `execve`
- downloaded libraries are never host `dlopen`/JNI inputs
- package scripts run under interpreted guest shell
- engine cannot be replaced by package manager

Also test package uninstall and upgrade.

## Security tests

Attempt package/post-install behavior that tries to:

- access host `/system`
- load a guest `.so` as host JNI
- launch APK installer
- modify the Play-delivered engine

All must fail/be unavailable.

## Evidence

Create:

`docs/playstore/evidence/package_manager_interpreter_validation.md`

Include trace/log evidence of the launch chain.

## Acceptance

- [ ] Repository update works.
- [ ] New executable package installs.
- [ ] Newly installed executable runs through QEMU/interpreter.
- [ ] Upgrade and uninstall work.
- [ ] No direct host execution path observed.
- [ ] Engine remains immutable from guest/package manager.
- [ ] Evidence committed.
