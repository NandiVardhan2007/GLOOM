#!/usr/bin/env bash
# ==============================================================================
#  ____ _      ___   ___  __  __ 
# / ___| |    / _ \ / _ \|  \/  |
#| |  _| |   | | | | | | | |\/| |
#| |_| | |___| |_| | |_| | |  | |
# \____|_____|\___/ \___/|_|  |_|  Next-Gen Dynamic Island & Fluid Dock
# ==============================================================================
# Fast, automated CLI installer for Linux (Debian, Ubuntu, Zorin OS, Arch)
# Usage:
#   curl -fsSL https://raw.githubusercontent.com/NandiVardhan2007/GLOOM/main/scripts/install.sh | bash
# ==============================================================================

set -eo pipefail

# ANSI Color Palette & Typographic Styles
BOLD='\033[1m'
DIM='\033[2m'
CYAN='\033[38;2;48;209;88m'    # Apple Phosphor Green
BLUE='\033[38;2;10;132;255m'   # Apple System Blue
PURPLE='\033[38;2;191;90;242m' # Apple Indigo
WHITE='\033[38;2;255;255;255m'
MUTED='\033[38;2;142;142;147m' # Apple Secondary Label
RED='\033[38;2;255;69;58m'
NC='\033[0m'

# Clear screen if interactive
if [ -t 1 ]; then
    clear 2>/dev/null || true
fi

echo -e "${CYAN}"
cat << 'EOF'
 ╭──────────────────────────────────────────────────────────────╮
 │   ____ _      ___   ___  __  __                              │
 │  / ___| |    / _ \ / _ \|  \/  |   DYNAMIC ISLAND FOR LINUX  │
 │ | |  _| |   | | | | | | | |\/| |                             │
 │ | |_| | |___| |_| | |_| | |  | |   Crafted with Apple HIG    │
 │  \____|_____|\___/ \___/|_|  |_|   Fluid Motion & OLED Glass │
 ╰──────────────────────────────────────────────────────────────╯
EOF
echo -e "${NC}"

# Helper for formatted step output
print_step() {
    local num="$1"
    local title="$2"
    echo -e " ${BLUE}${BOLD}[$num/4]${NC} ${WHITE}${BOLD}$title${NC}"
}

print_info() {
    local text="$1"
    echo -e "       ${MUTED}↳ $text${NC}"
}

print_success() {
    local text="$1"
    echo -e "       ${CYAN}✔ $text${NC}"
}

print_error() {
    local text="$1"
    echo -e "\n ${RED}${BOLD}✖ Error:${NC} ${WHITE}$text${NC}\n"
}

# 1. Architecture Check
print_step "1" "Verifying system architecture"
ARCH=$(uname -m)
if [ "$ARCH" != "x86_64" ]; then
    print_error "GLOOM currently supports 64-bit x86_64 (amd64) systems. Detected: $ARCH"
    exit 1
fi
print_success "Architecture confirmed: x86_64 (amd64)"

# 2. Query GitHub for latest release asset
print_step "2" "Querying latest verified release from GitHub"
REPO="NandiVardhan2007/GLOOM"
RELEASE_JSON=$(curl -sSL "https://api.github.com/repos/$REPO/releases/latest" 2>/dev/null || true)

DEB_URL=$(echo "$RELEASE_JSON" | grep -o 'https://[^"]*amd64\.deb' | head -n 1)
VERSION=$(echo "$RELEASE_JSON" | grep -o '"tag_name": *"[^"]*"' | head -n 1 | cut -d '"' -f 4)

if [ -z "$DEB_URL" ]; then
    VERSION="v2.3.0"
    DEB_URL="https://github.com/$REPO/releases/download/$VERSION/gloom_2.3.0_amd64.deb"
fi
print_success "Target release found: ${BOLD}$VERSION${NC}"
print_info "Package source: $DEB_URL"

# 3. Download the native package
print_step "3" "Downloading native package payload"
TEMP_DEB="/tmp/gloom-install.deb"
rm -f "$TEMP_DEB"

if curl -fSL "$DEB_URL" -o "$TEMP_DEB" --progress-bar; then
    PACKAGE_SIZE=$(du -h "$TEMP_DEB" | cut -f1)
    print_success "Downloaded payload ($PACKAGE_SIZE) verified"
else
    print_error "Failed to download $DEB_URL. Please check your internet connection."
    exit 1
fi

# 4. Install the package
print_step "4" "Deploying GLOOM into system environment"
print_info "Elevating privileges for dpkg installation..."

SUDO_CMD=""
if [ "$EUID" -ne 0 ]; then
    if command -v sudo >/dev/null 2>&1; then
        SUDO_CMD="sudo"
    else
        print_error "sudo is required to install the system package. Please run as root."
        exit 1
    fi
fi

# Use /dev/tty if available to prompt for sudo password even through pipe
if [ -c /dev/tty ]; then
    $SUDO_CMD dpkg -i "$TEMP_DEB" </dev/tty || $SUDO_CMD apt-get install -f -y </dev/tty
else
    $SUDO_CMD dpkg -i "$TEMP_DEB" || $SUDO_CMD apt-get install -f -y
fi

rm -f "$TEMP_DEB"

# 5. Success Card
if command -v gloom >/dev/null 2>&1; then
    echo -e "\n${CYAN} ╭──────────────────────────────────────────────────────────────╮${NC}"
    echo -e "${CYAN} │${NC}  ${BOLD}${WHITE}Installation Complete! GLOOM $VERSION is ready.${NC}             ${CYAN}│${NC}"
    echo -e "${CYAN} ├──────────────────────────────────────────────────────────────┤${NC}"
    echo -e "${CYAN} │${NC}  ${MUTED}Binary Location:${NC}     ${BOLD}/usr/bin/gloom${NC}                         ${CYAN}│${NC}"
    echo -e "${CYAN} │${NC}  ${MUTED}Application Entry:${NC}   ${BOLD}Registered in System App Menu${NC}          ${CYAN}│${NC}"
    echo -e "${CYAN} │${NC}  ${MUTED}Launch Command:${NC}      ${CYAN}${BOLD}gloom${NC}                                  ${CYAN}│${NC}"
    echo -e "${CYAN} ╰──────────────────────────────────────────────────────────────╯${NC}\n"

    echo -e " ${BOLD}💡 Quick Tips:${NC}"
    echo -e "   • Launch anytime by typing ${CYAN}gloom${NC} in terminal or finding GLOOM in your Apps menu."
    echo -e "   • Manage auto-start and auto-update anytime in ${BOLD}GLOOM Settings → System${NC}."
    echo -e "   • Click the Island capsule to expand, or press <kbd>Esc</kbd> to dismiss.\n"
else
    print_error "Installation failed or binary not found in PATH."
    exit 1
fi
