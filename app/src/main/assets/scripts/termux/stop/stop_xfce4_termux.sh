#!/bin/bash
# ============================================================
# FluxLinux Pro — Stop Native Termux XFCE4 Desktop
# Location: termux/stop/stop_xfce4_termux.sh
# Runs on: HOST Termux (native, no container)
# Root required: no
# ============================================================

echo ""
echo "══════════════════════════════════════════════"
echo "  FluxLinux Pro — Stopping Native XFCE4 Desktop"
echo "══════════════════════════════════════════════"
echo ""

# ── Graceful XFCE4 shutdown ───────────────────────────────
echo "FluxLinux Pro: Sending graceful shutdown to XFCE4..."
if command -v xfce4-session-logout >/dev/null 2>&1 && [ -n "$DISPLAY" ]; then
    xfce4-session-logout --logout 2>/dev/null || true
    sleep 2
fi

# ── Kill XFCE4 processes ──────────────────────────────────
echo "FluxLinux Pro: Stopping XFCE4 session..."
pkill -TERM -f "xfce4-session" 2>/dev/null || true
sleep 1
pkill -TERM -f "xfwm4" 2>/dev/null || true
pkill -TERM -f "xfdesktop" 2>/dev/null || true
pkill -TERM -f "xfce4-panel" 2>/dev/null || true
pkill -TERM -f "Thunar" 2>/dev/null || true
pkill -TERM -f "xfconfd" 2>/dev/null || true
sleep 1

# Force-kill if still running
pkill -KILL -f "xfce4-session" 2>/dev/null || true
pkill -KILL -f "xfwm4" 2>/dev/null || true
echo " [✅] XFCE4 session stopped"

# ── Kill X11 server ───────────────────────────────────────
echo "FluxLinux Pro: Stopping Termux:X11..."
pkill -TERM -f "termux-x11" 2>/dev/null || true
sleep 1
pkill -KILL -f "Xwayland" 2>/dev/null || true
pkill -KILL -f "termux-x11" 2>/dev/null || true
echo " [✅] X11 server stopped"

# ── Kill VirGL server ─────────────────────────────────────
echo "FluxLinux Pro: Stopping GPU server..."
pkill -TERM -f "virgl_test_server" 2>/dev/null || true
sleep 1
pkill -KILL -f "virgl_test_server" 2>/dev/null || true
echo " [✅] VirGL server stopped"

# ── Kill PulseAudio ───────────────────────────────────────
echo "FluxLinux Pro: Stopping audio server..."
pulseaudio --kill 2>/dev/null || \
pkill -TERM -f "pulseaudio" 2>/dev/null || true
echo " [✅] PulseAudio stopped"

# ── Clean up socket files ─────────────────────────────────
rm -f /tmp/.X0-lock 2>/dev/null || true
rm -f /tmp/.X11-unix/X0 2>/dev/null || true

echo ""
echo "✅ XFCE4 desktop stopped cleanly."
echo ""
