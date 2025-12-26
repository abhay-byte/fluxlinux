#!/bin/bash
# flux_install.sh
# Install and configure PRoot distro with FluxLinux setup
# Usage: bash flux_install.sh <distro_id> <base64_encoded_setup_script>

DISTRO=$1
SETUP_B64=$2

# Load full Termux environment
source /data/data/com.termux/files/usr/etc/profile

echo "FluxLinux: Installing $DISTRO..."

if [ "$DISTRO" == "termux" ]; then
    echo "FluxLinux: Native Termux Mode"
    EXIT_CODE=0
else
    # Use reset to handle both fresh install and reinstall cases
    # This automatically removes existing installation if present
    proot-distro reset $DISTRO 2>/dev/null || proot-distro install $DISTRO
    EXIT_CODE=$?
fi

if [ $EXIT_CODE -eq 0 ]; then
    echo "FluxLinux: Install Successful!"
    if [ ! -z "$SETUP_B64" ] && [ "$SETUP_B64" != "null" ]; then
        echo "FluxLinux: Configuring..."
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
             echo "FluxLinux: Configuration/Setup Script Failed!"
             am start -a android.intent.action.VIEW -d "fluxlinux://callback?result=failure&name=distro_install_${DISTRO}"
             exit 1
        fi
        
        echo "FluxLinux: Configuration Complete!"
    fi
    
    # Create marker file to track installation
    touch "$HOME/.fluxlinux_distro_${DISTRO}_installed"
    
    # Return to FluxLinux app
    echo "Returning to FluxLinux..."
    CLEAN_DISTRO=$(echo "$DISTRO" | tr -d '[:space:]')
    am start -a android.intent.action.VIEW -d "fluxlinux://callback?result=success&name=distro_install_$CLEAN_DISTRO"
else
    echo "FluxLinux: Install Failed with code $EXIT_CODE!"
    CLEAN_DISTRO=$(echo "$DISTRO" | tr -d '[:space:]')
    am start -a android.intent.action.VIEW -d "fluxlinux://callback?result=failure&name=distro_install_$CLEAN_DISTRO"
    exit 1
fi
