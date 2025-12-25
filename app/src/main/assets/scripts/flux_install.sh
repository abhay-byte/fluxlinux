#!/bin/bash
# flux_install.sh
# Install and configure PRoot distro with FluxLinux setup
# Usage: bash flux_install.sh <distro_id> <base64_encoded_setup_script>

DISTRO=$1
SETUP_B64=$2

# Load full Termux environment
source /data/data/com.termux/files/usr/etc/profile

echo "FluxLinux: Installing $DISTRO..."

# Use reset to handle both fresh install and reinstall cases
# This automatically removes existing installation if present
proot-distro reset $DISTRO 2>/dev/null || proot-distro install $DISTRO

EXIT_CODE=$?
if [ $EXIT_CODE -eq 0 ]; then
    echo "FluxLinux: Install Successful!"
    if [ ! -z "$SETUP_B64" ] && [ "$SETUP_B64" != "null" ]; then
        echo "FluxLinux: Configuring..."
        # Decode setup script
        echo "$SETUP_B64" | base64 -d > $HOME/flux_setup_temp.sh
        chmod +x $HOME/flux_setup_temp.sh
        
        # Move it to a shared location readable by proot
        proot-distro login $DISTRO --shared-tmp -- bash -c "bash /data/data/com.termux/files/home/flux_setup_temp.sh"
        
        rm $HOME/flux_setup_temp.sh
        echo "FluxLinux: Configuration Complete!"
    fi
    
    # Create marker file to track installation
    touch ~/.fluxlinux_distro_${DISTRO}_installed
    
    # Return to FluxLinux app
    echo "Returning to FluxLinux..."
    am start -n com.fluxlinux.app/.MainActivity
else
    echo "FluxLinux: Install Failed with code $EXIT_CODE!"
    exit 1
fi
