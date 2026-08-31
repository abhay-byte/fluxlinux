# Worker 07 — Terminal, XFCE and Audio Across Guest Boundary

## Goal

Restore the useful v2 interactive experience without weakening the interpreter/VM boundary.

## Part A — Terminal

Refactor the terminal backend to an engine-neutral PTY/channel abstraction.

Expected capabilities:

- stdin/stdout/stderr
- resize rows/columns
- exit status
- interrupt/terminate
- session title/state

The terminal UI must not call direct guest executables.

## Part B — XFCE software-rendered MVP

Start with software rendering.

Choose a narrow transport:

- X11 over a controlled local/virtual socket to the Play-delivered in-app X11 server, or
- VNC/RDP-like guest display server and host client if full-system VM is selected.

Do not expose a generic Android Binder/JNI API bridge to guest applications.

Verify:

- start XFCE
- open terminal
- launch basic graphical app
- stop desktop cleanly

## Part C — Audio

Use a narrow audio protocol path, e.g. guest PulseAudio client -> controlled virtual/loopback transport -> host Play-delivered audio endpoint -> AAudio.

Do not bind broad host `/dev` audio nodes into guest.

## Tests

- terminal resize/input/output
- session stop/restart
- desktop startup/stop
- app background/foreground
- audio start/stop
- no guest direct host binary launch

## Acceptance

- [ ] Interactive terminal works under interpreted engine.
- [ ] XFCE/software desktop works for at least one distro.
- [ ] Basic audio works or has a documented blocker.
- [ ] No generic Android API bridge is exposed.
- [ ] Guest execution invariant remains green.
