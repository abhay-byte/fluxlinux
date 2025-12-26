#!/bin/bash
# setup_arch_family.sh
# Installs XFCE4 and VNC on Arch Linux (via Proot)

DISTRO=$1
echo "FluxLinux: Setting up Arch Linux ($DISTRO)..."

# 1. Update Pacman
pacman -Sy --noconfirm || exit 1

# 2. Install XFCE4 and Essentials
# xfce4: Desktop Environment
# tigervnc: VNC Server
# dbus: Required for XFCE
# ttf-dejavu: Basic fonts
# sudo: For user privileges
pacman -S --noconfirm xfce4 tigervnc dbus ttf-dejavu sudo || exit 1

# 3. Create User if not exists (flux)
if ! id "flux" &>/dev/null; then
    useradd -m -s /bin/bash flux
    echo "flux:flux" | chpasswd
    echo "flux ALL=(ALL) NOPASSWD: ALL" >> /etc/sudoers
fi

# 4. Configure VNC for User
mkdir -p /home/flux/.vnc
echo "#!/bin/bash
export PULSE_SERVER=127.0.0.1
xrdb $HOME/.Xresources
startxfce4" > /home/flux/.vnc/xstartup
chmod +x /home/flux/.vnc/xstartup
chown -R flux:flux /home/flux/.vnc

echo "FluxLinux: Arch Setup Complete!"
