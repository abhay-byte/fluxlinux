#!/bin/bash
# flux_install.sh
# Install and configure PRoot distro with FluxLinux Pro setup
# Usage: bash flux_install.sh <distro_id> <base64_encoded_setup_script>

DISTRO=$1
SETUP_B64=$2

# Load full Termux environment
source /data/data/com.termux/files/usr/etc/profile

echo "FluxLinux Pro: Installing $DISTRO..."

if [ "$DISTRO" == "termux" ]; then
    echo "FluxLinux Pro: Native Termux Mode"
    EXIT_CODE=0
else
    # Check if distro is already installed by looking for its rootfs
    DISTRO_ROOTFS="/data/data/com.termux/files/usr/var/lib/proot-distro/installed-rootfs/$DISTRO"
    
    if [ -d "$DISTRO_ROOTFS" ]; then
        echo "FluxLinux Pro: $DISTRO already installed. Skipping base installation."
        EXIT_CODE=0
    else
        echo "FluxLinux Pro: Installing $DISTRO base system..."
        proot-distro install $DISTRO
        EXIT_CODE=$?
    fi
fi

if [ $EXIT_CODE -eq 0 ]; then
    echo "FluxLinux Pro: Install Successful!"
    if [ ! -z "$SETUP_B64" ] && [ "$SETUP_B64" != "null" ]; then
        echo "FluxLinux Pro: Configuring..."
        # Decode setup script
        echo "$SETUP_B64" | base64 -d > $HOME/flux_setup_temp.sh
        chmod +x $HOME/flux_setup_temp.sh
        
        if [ "$DISTRO" == "termux" ]; then
            # Run directly in Termux
            bash $HOME/flux_setup_temp.sh
            SETUP_EXIT=$?
        else
            # Move it to a shared location readable by proot
            proot-distro login $DISTRO --shared-tmp -- bash -c "bash /data/data/com.termux/files/home/flux_setup_temp.sh $DISTRO"
            SETUP_EXIT=$?
        fi
        
        rm $HOME/flux_setup_temp.sh

        if [ $SETUP_EXIT -ne 0 ]; then
             echo "FluxLinux Pro: Configuration/Setup Script Failed!"
             am start -a android.intent.action.VIEW -d "fluxlinux://callback?result=failure&name=distro_install_${DISTRO}"
             exit 1
        fi
        
        echo "FluxLinux Pro: Configuration Complete!"
    fi
    
    # Create marker file to track installation
    touch "$HOME/.fluxlinux_distro_${DISTRO}_installed"
    
    # Return to FluxLinux Pro app
    echo "Returning to FluxLinux Pro..."
    CLEAN_DISTRO=$(echo "$DISTRO" | tr -d '[:space:]')
    am start -a android.intent.action.VIEW -d "fluxlinux://callback?result=success&name=distro_install_$CLEAN_DISTRO"
else
    echo "FluxLinux Pro: Install Failed with code $EXIT_CODE!"
    CLEAN_DISTRO=$(echo "$DISTRO" | tr -d '[:space:]')
    am start -a android.intent.action.VIEW -d "fluxlinux://callback?result=failure&name=distro_install_$CLEAN_DISTRO"
    exit 1
fi
