#!/bin/bash
# ============================================================
# FluxLinux Pro — Stop Native Termux KDE Plasma Desktop
# Location: termux/stop/stop_kde_termux.sh
# Runs on: HOST Termux (native, no container)
# Root required: no
# ============================================================

echo ""
echo "══════════════════════════════════════════════"
echo "  FluxLinux Pro — Stopping Native KDE Plasma"
echo "══════════════════════════════════════════════"
echo ""

# ── Graceful KDE shutdown ─────────────────────────────────
echo "FluxLinux Pro: Sending graceful shutdown to KDE..."
if command -v qdbus >/dev/null 2>&1 && [ -n "$DISPLAY" ]; then
    qdbus org.kde.ksmserver /KSMServer logout 0 3 3 2>/dev/null || true
    sleep 3
fi

# ── Kill KDE session processes ───────────────────────────
echo "FluxLinux Pro: Stopping KDE Plasma session..."
pkill -TERM -f "startplasma" 2>/dev/null || true
pkill -TERM -f "plasmashell" 2>/dev/null || true
pkill -TERM -f "kwin_x11" 2>/dev/null || true
pkill -TERM -f "kded5" 2>/dev/null || true
pkill -TERM -f "ksmserver" 2>/dev/null || true
pkill -TERM -f "baloo" 2>/dev/null || true
pkill -TERM -f "akonadi" 2>/dev/null || true
pkill -TERM -f "dolphin" 2>/dev/null || true
pkill -TERM -f "konsole" 2>/dev/null || true
sleep 2

# Force-kill if still running
pkill -KILL -f "plasmashell" 2>/dev/null || true
pkill -KILL -f "kwin_x11" 2>/dev/null || true
pkill -KILL -f "kded5" 2>/dev/null || true
echo " [✅] KDE Plasma session stopped"

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
echo "✅ KDE Plasma desktop stopped cleanly."
echo ""
