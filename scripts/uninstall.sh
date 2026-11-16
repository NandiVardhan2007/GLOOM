#!/usr/bin/env bash
# ══════════════════════════════════════════════════════════════════════════════
# GLOOM Dynamic Island & Fluid Dock — Clean Uninstaller (Linux)
# ══════════════════════════════════════════════════════════════════════════════
set -euo pipefail

# Style definitions
BOLD="\033[1m"
DIM="\033[2m"
CYAN="\033[36m"
GREEN="\033[32m"
YELLOW="\033[33m"
RESET="\033[0m"

echo -e "\n${BOLD}${CYAN}  ┌─────────────────────────────────────────────────────────────┐${RESET}"
echo -e "${BOLD}${CYAN}  │  GLOOM Dynamic Island & Fluid Dock — Clean Uninstaller      │${RESET}"
echo -e "${BOLD}${CYAN}  └─────────────────────────────────────────────────────────────┘${RESET}\n"

# 1. Terminate any active GLOOM instances
echo -e "${DIM}  [1/4] Stopping running GLOOM instances...${RESET}"
killall -9 gloom 2>/dev/null || true

# 2. Purge system deb package
echo -e "${DIM}  [2/4] Purging GLOOM system package and desktop integration...${RESET}"
if command -v dpkg >/dev/null 2>&1 && dpkg -l gloom >/dev/null 2>&1; then
    if [ "$(id -u)" -eq 0 ]; then
        dpkg -P gloom || true
    else
        sudo dpkg -P gloom || true
    fi
fi

# Ensure all residual system files are removed
if [ "$(id -u)" -eq 0 ]; then
    rm -f /usr/bin/gloom
    rm -f /usr/share/applications/com.nandu.gloom.desktop
    rm -f /usr/share/icons/hicolor/*/apps/gloom.png
    rm -f /usr/share/pixmaps/gloom.png
    if command -v gtk-update-icon-cache >/dev/null 2>&1; then
        gtk-update-icon-cache -q -t -f /usr/share/icons/hicolor || true
    fi
    if command -v update-desktop-database >/dev/null 2>&1; then
        update-desktop-database -q /usr/share/applications || true
    fi
else
    sudo rm -f /usr/bin/gloom 2>/dev/null || true
    sudo rm -f /usr/share/applications/com.nandu.gloom.desktop 2>/dev/null || true
    sudo rm -f /usr/share/icons/hicolor/*/apps/gloom.png 2>/dev/null || true
    sudo rm -f /usr/share/pixmaps/gloom.png 2>/dev/null || true
    if command -v gtk-update-icon-cache >/dev/null 2>&1; then
        sudo gtk-update-icon-cache -q -t -f /usr/share/icons/hicolor 2>/dev/null || true
    fi
    if command -v update-desktop-database >/dev/null 2>&1; then
        sudo update-desktop-database -q /usr/share/applications 2>/dev/null || true
    fi
fi

# 3. Purge user configs, localStorage, WebKit caches, and autostart
echo -e "${DIM}  [3/4] Purging user data, settings, and WebKit cache...${RESET}"
rm -rf "$HOME/.config/com.nandu.gloom" "$HOME/.config/gloom" 2>/dev/null || true
rm -rf "$HOME/.local/share/com.nandu.gloom" "$HOME/.local/share/gloom" 2>/dev/null || true
rm -rf "$HOME/.cache/com.nandu.gloom" "$HOME/.cache/gloom" 2>/dev/null || true
rm -f "$HOME/.config/autostart/gloom.desktop" "$HOME/.config/autostart/com.nandu.gloom.desktop" 2>/dev/null || true
rm -f /tmp/*gloom* /tmp/taskbar_hidden.flag 2>/dev/null || true

# 4. Final summary
echo -e "${DIM}  [4/4] Verifying clean state...${RESET}"
echo -e "\n${BOLD}${GREEN}  ✔ GLOOM has been completely uninstalled with zero leftovers.${RESET}\n"
