#!/usr/bin/env bash
# ==============================================================================
# GLOOM Linux One-Line Installer
# Seamlessly downloads and installs the latest verified GLOOM Dynamic Island package.
# Usage:
#   curl -fsSL https://raw.githubusercontent.com/NandiVardhan2007/GLOOM/main/scripts/install.sh | bash
# ==============================================================================

set -e

# Terminal Colors
CYAN='\033[0;36m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${CYAN}"
cat << 'EOF'
  ==============================================================
   ____ _      ___   ___  __  __ 
  / ___| |    / _ \ / _ \|  \/  |
 | |  _| |   | | | | | | | |\/| |
 | |_| | |___| |_| | |_| | |  | |
  \____|_____|\___/ \___/|_|  |_|  Dynamic Island & Desktop Dock
  ==============================================================
EOF
echo -e "${NC}"

# 1. Architecture Check
ARCH=$(uname -m)
if [ "$ARCH" != "x86_64" ]; then
    echo -e "${RED}Error: GLOOM currently supports x86_64 (amd64) architecture. Detected: $ARCH${NC}"
    exit 1
fi

# 2. Query GitHub for the latest release asset
echo -e "${YELLOW} [1/3] Querying latest GLOOM release from GitHub...${NC}"
REPO="NandiVardhan2007/GLOOM"
RELEASE_JSON=$(curl -sSL "https://api.github.com/repos/$REPO/releases/latest" 2>/dev/null || true)

DEB_URL=$(echo "$RELEASE_JSON" | grep -o 'https://[^"]*amd64\.deb' | head -n 1)

if [ -z "$DEB_URL" ]; then
    # Fallback to direct latest release asset pattern
    DEB_URL="https://github.com/$REPO/releases/latest/download/gloom_2.3.0_amd64.deb"
fi

# 3. Download the package
TEMP_DEB="/tmp/gloom-latest.deb"
rm -f "$TEMP_DEB"

echo -e "${YELLOW} [2/3] Downloading latest GLOOM native package...${NC}"
curl -fSL "$DEB_URL" -o "$TEMP_DEB" --progress-bar

# 4. Install the .deb package
echo -e "${YELLOW} [3/3] Installing GLOOM to system...${NC}"
if command -v sudo >/dev/null 2>&1; then
    sudo dpkg -i "$TEMP_DEB" || sudo apt-get install -f -y
else
    dpkg -i "$TEMP_DEB" || apt-get install -f -y
fi

rm -f "$TEMP_DEB"

# 5. Success Confirmation
if command -v gloom >/dev/null 2>&1; then
    echo -e "\n${GREEN}  ✓ GLOOM installed successfully!${NC}"
    echo -e "  📍 Executable: ${CYAN}/usr/bin/gloom${NC}"
    echo -e "  🖥️  Launcher:   ${CYAN}GLOOM registered in system application menu${NC}"
    echo -e "\n  🚀 Start GLOOM anytime by running: ${CYAN}gloom${NC} or launching it from your apps menu.\n"
else
    echo -e "\n${YELLOW}  ⚠️  Installation completed, but 'gloom' was not found in PATH.${NC}"
fi
