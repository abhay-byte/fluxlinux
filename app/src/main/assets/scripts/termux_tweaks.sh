#!/bin/bash
# termux_tweaks.sh
# Enhance Termux with Oh My Zsh, themes, fonts, and fastfetch
# Based on: https://github.com/abhay-byte/Linux_Setup

echo "🎨 FluxLinux: Enhancing Termux Environment..."

install_ohmyzsh() {
    echo "🐚 Installing Oh My Zsh..."
    RUNZSH=no CHSH=no sh -c "$(curl -fsSL https://raw.githubusercontent.com/ohmyzsh/ohmyzsh/master/tools/install.sh)"
    export ZSH_CUSTOM="${ZSH_CUSTOM:-$HOME/.oh-my-zsh/custom}"

    echo "🔌 Installing Zsh plugins..."
    git clone https://github.com/zsh-users/zsh-autosuggestions "$ZSH_CUSTOM/plugins/zsh-autosuggestions"
    git clone https://github.com/zsh-users/zsh-syntax-highlighting "$ZSH_CUSTOM/plugins/zsh-syntax-highlighting"
    git clone --depth 1 https://github.com/marlonrichert/zsh-autocomplete.git "$ZSH_CUSTOM/plugins/zsh-autocomplete"

    echo "⚙️ Configuring .zshrc..."
    if grep -q "plugins=" "$HOME/.zshrc" 2>/dev/null; then
        sed -i 's/plugins=(.*)/plugins=(git zsh-autosuggestions zsh-syntax-highlighting)/' "$HOME/.zshrc"
    else
        echo 'plugins=(git zsh-autosuggestions zsh-syntax-highlighting)' >> "$HOME/.zshrc"
    fi
    sed -i 's/^ZSH_THEME=.*$/ZSH_THEME="random"/' "$HOME/.zshrc"
    echo "✅ Zsh configuration complete."
}

set_termux_colors() {
    echo "🎨 Choose a Termux color scheme:"
    echo "1) GitHub Dark (default)"
    echo "2) Dracula"
    echo "3) Gruvbox Dark"
    printf "Enter choice [1-3]: "
    read choice

    mkdir -p ~/.termux

    case "$choice" in
        2)
            echo "🎨 Applying Dracula color scheme..."
            cat > ~/.termux/colors.properties << 'EOF'
foreground=#f8f8f2
background=#282a36
cursor=#f8f8f2
color0=#21222c
color1=#ff5555
color2=#50fa7b
color3=#f1fa8c
color4=#bd93f9
color5=#ff79c6
color6=#8be9fd
color7=#f8f8f2
color8=#6272a4
color9=#ff6e6e
color10=#69ff94
color11=#ffffa5
color12=#d6acff
color13=#ff92df
color14=#a4ffff
color15=#ffffff
EOF
            ;;
        3)
            echo "🎨 Applying Gruvbox Dark color scheme..."
            cat > ~/.termux/colors.properties << 'EOF'
foreground=#ebdbb2
background=#282828
cursor=#ebdbb2
color0=#282828
color1=#cc241d
color2=#98971a
color3=#d79921
color4=#458588
color5=#b16286
color6=#689d6a
color7=#a89984
color8=#928374
color9=#fb4934
color10=#b8bb26
color11=#fabd2f
color12=#83a598
color13=#d3869b
color14=#8ec07c
color15=#ebdbb2
EOF
            ;;
        *)
            echo "🎨 Applying GitHub Dark color scheme..."
            cat > ~/.termux/colors.properties << 'EOF'
foreground=#c9d1d9
background=#0d1117
cursor=#c9d1d9
color0=#484f58
color1=#ff7b72
color2=#3fb950
color3=#d29922
color4=#58a6ff
color5=#bc8cff
color6=#39c5cf
color7=#b1bac4
color8=#6e7681
color9=#ffa198
color10=#56d364
color11=#e3b341
color12=#79c0ff
color13=#d2a8ff
color14=#56d4dd
color15=#f0f6fc
EOF
            ;;
    esac
    echo "✅ Color scheme applied."
}

install_nerd_font() {
    echo "🔤 Choose a Nerd Font for Termux:"
    echo "1) Meslo (default)"
    echo "2) FiraCode"
    echo "3) JetBrainsMono"
    printf "Enter choice [1-3]: "
    read font_choice

    mkdir -p ~/.termux
    TMPFONT="$HOME/tmpfont"
    mkdir -p "$TMPFONT" && cd "$TMPFONT" || exit 1

    case "$font_choice" in
        2)
            echo "🔤 Installing FiraCode Nerd Font..."
            curl -fsSL -o font.zip https://github.com/ryanoasis/nerd-fonts/releases/latest/download/FiraCode.zip
            ;;
        3)
            echo "🔤 Installing JetBrainsMono Nerd Font..."
            curl -fsSL -o font.zip https://github.com/ryanoasis/nerd-fonts/releases/latest/download/JetBrainsMono.zip
            ;;
        *)
            echo "🔤 Installing Meslo Nerd Font (default)..."
            curl -fsSL -o font.zip https://github.com/ryanoasis/nerd-fonts/releases/latest/download/Meslo.zip
            ;;
    esac

    unzip -jo font.zip "*Regular.ttf" -d ~/.termux
    FONTFILE=$(ls ~/.termux/*Regular.ttf | head -n 1)
    if [ -n "$FONTFILE" ]; then
        mv -f "$FONTFILE" ~/.termux/font.ttf
        echo "✅ Font installed as Termux default."
    else
        echo "❌ No Regular font found!"
    fi

    cd ~ && rm -rf "$TMPFONT"
    termux-reload-settings
}

configure_fastfetch() {
    echo > "$PREFIX/etc/motd"
    rm -f "$PREFIX/etc/motd"

    echo "⚡ Configuring fastfetch..."
    mkdir -p ~/.local/share/fastfetch/presets
    curl -fsSL https://raw.githubusercontent.com/abhay-byte/Linux_Setup/dev/config/termux.jsonc \
        -o ~/.local/share/fastfetch/presets/termux.jsonc

    RCFILE="$HOME/.zshrc"
    [ -f "$RCFILE" ] || touch "$RCFILE"

    # Add fastfetch at top if missing
    if ! grep -q 'fastfetch --config termux' "$RCFILE"; then
        sed -i '1ifastfetch --config termux' "$RCFILE"
    fi

    echo "✅ Fastfetch configured."
    echo "🐚 Setting Zsh as default shell..."
    chsh -s zsh
}

# Main execution
install_ohmyzsh
set_termux_colors
install_nerd_font
configure_fastfetch

echo ""
echo "🎉 FluxLinux: Termux enhancement complete!"
echo "🔄 Restart Termux to see all changes."
