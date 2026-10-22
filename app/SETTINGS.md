# Gloom Settings Reference

All settings are stored in `settings.json` in the app config directory (`%APPDATA%/gloom/`). The file is a flat JSON object with `gloom-` prefixed keys. Gloom watches this file for external changes and applies them in real-time.

## Quick Start

Edit `settings.json` with any text editor while Gloom is running. Changes are applied immediately — no restart required.

```json
{
	"gloom-dock-enabled": "true",
	"gloom-dock-mode": "smart",
	"gloom-theme-mode": "dark",
	"gloom-scale": "1.0"
}
```

## Settings Keys

### Dock

| Key                                | Type                             | Default       | Description                                                                                                                                                                      |
| ---------------------------------- | -------------------------------- | ------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `gloom-dock-enabled`               | `"true"` / `"false"`             | `"true"`      | Show or hide the Gloom Dock (taskbar replacement).                                                                                                                               |
| `gloom-dock-mode`                  | `"fixed"` / `"smart"` / `"peek"` | `"smart"`     | Dock visibility behavior. **fixed** = always visible as AppBar. **smart** = auto-hide when overlapped by fullscreen apps. **peek** = hidden until cursor approaches bottom edge. |
| `gloom-dock-edge-delay`            | milliseconds `"0"`–`"2000"`      | `"200"`       | In smart/peek mode, how long the cursor must rest against the bottom screen edge before the dock appears. `"0"` reveals instantly.                                               |
| `gloom-dock-preview-enabled`       | `"true"` / `"false"`             | `"true"`      | Show window thumbnail previews when hovering dock icons.                                                                                                                         |
| `gloom-dock-icon-only`             | `"true"` / `"false"`             | `"false"`     | Minimal icon-only style (no background/padding around icons).                                                                                                                    |
| `gloom-dock-separator-enabled`     | `"true"` / `"false"`             | `"true"`      | Show a divider between pinned and unpinned dock apps when unpinned apps are present. Unpinned apps remain on the right when disabled.                                            |
| `gloom-start-icon`                 | icon key / `custom:<data URI>`   | `"default"`   | Start button icon: `default`, `gloom-colorful`, `gloom-golden`, `gloom-biscuit`, `windows`, or `custom:` followed by an uploaded image's data URI.                               |
| `gloom-dock-task-view`             | `"true"` / `"false"`             | `"false"`     | Show a Task View button beside Start that opens Task View, like the taskbar's. Right-click it to hide it again.                                                                  |
| `gloom-dock-adaptive`              | `"true"` / `"false"`             | `"false"`     | Fixed dock only. Stretch the dock to full width like a traditional taskbar while a window is maximized, and contract back when it's restored.                                    |
| `gloom-dock-win-number-enabled`    | `"true"` / `"false"`             | `"true"`      | When the taskbar is replaced, Win+1 through Win+9 activate the matching pinned dock app (focus/minimize if running, launch otherwise) instead of the native taskbar slots.       |
| `gloom-dock-extras-position`       | `"off"` / `"left"` / `"right"`   | `"off"`       | Show a dock extras section with drives, folders and the Recycle Bin beside the app icons, on the left or right side of the dock.                                                 |
| `gloom-dock-extras-drives`         | `"true"` / `"false"`             | `"true"`      | Show fixed, removable (with media) and connected mapped network drives in the dock extras. Empty card readers and unloaded discs are hidden.                                     |
| `gloom-dock-extras-hidden-drives`  | comma-separated letters          | `""`          | Drives left out of the dock extras, e.g. `"D,E"`. Drives not listed appear, including newly connected ones. Right-clicking a drive and choosing Remove from Dock adds it here. |
| `gloom-dock-extras-recycle-bin`    | `"true"` / `"false"`             | `"true"`      | Show the Recycle Bin in the dock extras. The icon reflects whether the bin holds anything.                                                                                       |
| `gloom-dock-extras-folders`        | comma-separated ids              | `"downloads"` | Known folders in the dock extras: any of `desktop`, `downloads`, `documents`, `pictures`, `music`, `videos`, or `"none"` for no folders.                                         |
| `gloom-dock-extras-custom-folders` | JSON array                       | `"[]"`        | User-added folders, e.g. `[{"name":"Projects","path":"D:\\Projects"}]`. Folders that no longer exist are skipped until they return.                                              |

### Notch

| Key                      | Type                             | Default   | Description                                                                                                                    |
| ------------------------ | -------------------------------- | --------- | ------------------------------------------------------------------------------------------------------------------------------ |
| `gloom-notch-mode`       | `"fixed"` / `"smart"` / `"peek"` | `"fixed"` | Notch (top bar) visibility behavior. Same modes as dock. **peek** shows the notch briefly on media events and notifications.   |
| `gloom-notch-edge-delay` | milliseconds `"0"`–`"2000"`      | `"200"`   | In smart/peek mode, how long the cursor must rest against the top screen edge before the notch peeks. `"0"` reveals instantly. |

### Weather

| Key                              | Type                         | Default     | Description                                                                        |
| -------------------------------- | ---------------------------- | ----------- | ---------------------------------------------------------------------------------- |
| `gloom-weather-enabled`          | `"true"` / `"false"`         | `"true"`    | Show weather widget in the notch status bar.                                       |
| `gloom-weather-city`             | string                       | `""`        | Manually set city name for weather. Empty string = auto-detect via IP geolocation. |
| `gloom-weather-lat`              | number string                | (auto)      | Latitude coordinate for weather. Set automatically when a city is selected.        |
| `gloom-weather-lon`              | number string                | (auto)      | Longitude coordinate for weather. Set automatically when a city is selected.       |
| `gloom-weather-cached-temp`      | number string                | (none)      | Cached temperature value shown before next API fetch.                              |
| `gloom-weather-cached-condition` | string                       | (none)      | Cached weather condition text (e.g. "Partly Cloudy").                              |
| `gloom-temp-unit`                | `"celsius"` / `"fahrenheit"` | `"celsius"` | Temperature display unit.                                                          |

### Modules

| Key                         | Type                              | Default  | Description                                                                                              |
| --------------------------- | --------------------------------- | -------- | -------------------------------------------------------------------------------------------------------- |
| `gloom-calendar-enabled`    | `"true"` / `"false"`              | `"true"` | Enable calendar/timer mode in the notch.                                                                 |
| `gloom-music-mode-enabled`  | `"true"` / `"false"`              | `"true"` | Enable interactive music media widget.                                                                   |
| `gloom-music-compact-notch` | `"true"` / `"false"`              | `"true"` | Show compact music display (visualizer + artwork) in collapsed notch.                                    |
| `gloom-notch-remember-panel` | `"true"` / `"false"` | `"true"` | Click opens the last-used panel when on; when off, click opens `gloom-notch-default-panel`. |
| `gloom-notch-default-panel` | `"calendar"` / `"command-center"` | `"calendar"` | Fixed panel opened on click when panel memory is off. If calendar is disabled, Command Center opens instead. |
| `gloom-notch-last-panel`    | `"calendar"` / `"command-center"` | (unset)  | Last panel the notch was left on, saved by Gloom while panel memory is on. |
| `gloom-notch-click-to-open` | `"true"` / `"false"` | (legacy) | Old setting read only when `gloom-notch-remember-panel` is absent; `"false"` now selects fixed-panel behavior. |

### Shelf

| Key                   | Type                 | Default   | Description                                                                                                    |
| --------------------- | -------------------- | --------- | -------------------------------------------------------------------------------------------------------------- |
| `gloom-shelf-persist` | `"true"` / `"false"` | `"false"` | Keep notch shelf items across restarts. Off = items are cleared when Gloom closes. Max 10 items, newest first. |

### Music Appearance

| Key                                | Type                      | Default     | Description                                                                                     |
| ---------------------------------- | ------------------------- | ----------- | ----------------------------------------------------------------------------------------------- |
| `gloom-media-layout`               | `"classic"` / `"compact"` | `"classic"` | Expanded player style. **classic** = large album art. **compact** = small thumbnail + controls. |
| `gloom-media-ambience-enabled`     | `"true"` / `"false"`      | `"true"`    | Colored ambient glow behind expanded album art.                                                 |
| `gloom-media-compact-glow-enabled` | `"true"` / `"false"`      | `"true"`    | Glow effect around the collapsed compact thumbnail.                                             |
| `gloom-media-visualizer-enabled`   | `"true"` / `"false"`      | `"true"`    | Audio visualizer bars in music mode. Also accepts `gloom-visualizer-enabled` (legacy alias).    |
| `gloom-media-album-art-enabled`    | `"true"` / `"false"`      | `"true"`    | Show album artwork in the notch music display.                                                  |

### Overlays

| Key                                | Type                 | Default  | Description                                                              |
| ---------------------------------- | -------------------- | -------- | ------------------------------------------------------------------------ |
| `gloom-volume-overlay-enabled`     | `"true"` / `"false"` | `"true"` | Show Gloom volume HUD when volume changes (replaces native Windows OSD). |
| `gloom-volume-edge-enabled`        | `"true"` / `"false"` | `"true"` | Trigger volume HUD by hovering the left screen edge.                     |
| `gloom-brightness-overlay-enabled` | `"true"` / `"false"` | `"true"` | Show Gloom brightness HUD when brightness changes.                       |
| `gloom-brightness-edge-enabled`    | `"true"` / `"false"` | `"true"` | Trigger brightness HUD by hovering the right screen edge.                |

### Appearance

| Key                      | Type                                             | Default     | Description                                                                                                                                                 |
| ------------------------ | ------------------------------------------------ | ----------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `gloom-theme-mode`       | `"dark"` / `"light"` / `"custom"` / `"adaptive"` | `"dark"`    | Theme mode. **dark** = dark translucent. **light** = light translucent. **custom** = user-picked color. **adaptive** = follows Windows system accent color. |
| `gloom-theme-color`      | hex string                                       | `"#007aff"` | Custom theme color (used in `custom` and `adaptive` modes).                                                                                                 |
| `gloom-theme-opacity`    | float string                                     | `"0.80"`    | Background opacity (0.1 to 1.0).                                                                                                                            |
| `gloom-theme-saturation` | float string                                     | `"0.50"`    | Color saturation for custom/adaptive themes (0.0 to 1.0).                                                                                                   |
| `gloom-theme-brightness` | float string                                     | `"0.15"`    | Background brightness for custom/adaptive themes (0.0 to 1.0).                                                                                              |
| `gloom-corners-enabled`  | `"true"` / `"false"`                             | `"false"`   | Render rounded screen corner overlays on top edges.                                                                                                         |

### Status Widgets

| Key                    | Type        | Default                                    | Description                                                                                                 |
| ---------------------- | ----------- | ------------------------------------------ | ----------------------------------------------------------------------------------------------------------- |
| `gloom-status-widgets` | JSON string | `{"left":["weather"],"right":["battery"]}` | Widget layout in collapsed notch. Available: `"weather"`, `"battery"`, `"cpu"`, `"ram"`, `"disk"`, `"net"`. |

Example:

```json
{
	"gloom-status-widgets": "{\"left\":[\"cpu\",\"ram\"],\"right\":[\"battery\",\"net\"]}"
}
```

### System

| Key                            | Type                 | Default   | Description                                                                                          |
| ------------------------------ | -------------------- | --------- | ---------------------------------------------------------------------------------------------------- |
| `gloom-scale`                  | float string         | `"1.0"`   | UI scale factor (0.8 to 1.3). Changing this re-registers AppBars to resize the reserved screen area. |
| `gloom-low-battery-threshold`  | integer string       | `"20"`    | Battery percentage that triggers the low-battery alert pulse (5 to 50, step 5).                      |
| `gloom-auto-update`            | `"true"` / `"false"` | `"false"` | Check for and download updates automatically on startup.                                             |
| `gloom-show-update-indicator`  | `"true"` / `"false"` | `"true"`  | Show a green dot on the notch when an update is available.                                           |
| `gloom-announcement-dismissed` | announcement id      | `""`      | ID of the last dismissed in-app announcement (published via `website/public/announcements.json`).    |
| `gloom-time-format-24h`        | `"true"` / `"false"` | `"false"` | Use 24-hour clock format in the notch. When `"false"`, displays 12-hour format with AM/PM.           |

### Internal (Do Not Edit Manually)

| Key                 | Type     | Description                                                                           |
| ------------------- | -------- | ------------------------------------------------------------------------------------- |
| `gloom-first-run`   | sentinel | Set to `"done"` after first launch. Triggers splash screen if absent.                 |
| `gloom-app-version` | string   | Last known app version. If it differs from current, splash screen is shown on update. |

## Event System

Gloom uses two Tauri events for settings synchronization:

### `settings-changed`

- **Emitted by:** `save_setting` command (frontend or backend)
- **Payload:** `{ "key": "gloom-...", "value": ... }`
- **Purpose:** Broadcasts changes made through the Gloom UI to all windows
- **Key format:** Gloom-prefixed keys as-is (e.g. `"gloom-dock-mode"`)

### `settings-external-changed`

- **Emitted by:** File watcher (detects external edits to `settings.json`)
- **Payload:** `{ "key": "gloom-...", "value": ... }` or `{ "key": "gloom-...", "value": null }` for removed keys
- **Purpose:** Broadcasts changes made by external editors (VS Code, notepad, scripts)
- **Key format:** Gloom-prefixed keys as-is
- **Behavior:** Also syncs values to `localStorage` for instant frontend reads

### Flow

```
External editor saves settings.json
    ↓
File watcher detects change (ReadDirectoryChangesW)
    ↓
Diffs against SETTINGS_CACHE
    ↓
Emits settings-external-changed for each changed/removed key
    ↓
useSettingsSync hook updates React state + localStorage
    ↓
useSettingsSync hook updates React state
    ↓
UI re-renders with new values
```

## Example: Changing Dock Mode via Script

```powershell
# PowerShell: switch dock to smart mode
$json = Get-Content "$env:APPDATA\gloom\settings.json" | ConvertFrom-Json
$json.'gloom-dock-mode' = 'smart'
$json | ConvertTo-Json | Set-Content "$env:APPDATA\gloom\settings.json"
```

```python
# Python: disable dock
import json, os
path = os.path.join(os.environ['APPDATA'], 'gloom', 'settings.json')
with open(path) as f: settings = json.load(f)
settings['gloom-dock-enabled'] = 'false'
with open(path, 'w') as f: json.dump(settings, f)
```

## Notes

- All boolean values are strings (`"true"` / `"false"`) for consistency with `localStorage`.
- The `useSettingsSync` hook auto-converts `"true"` / `"false"` strings to booleans.
- `auto-hide` mode values in `gloom-dock-mode` and `gloom-notch-mode` are legacy aliases for `smart` — they are mapped automatically.
- Changing `gloom-scale` triggers AppBar re-registration to adjust reserved screen space.
- Settings → General → Reset to Defaults removes every `gloom-` key from `settings.json` and `localStorage` (except the `gloom-first-run` / `gloom-app-version` lifecycle sentinels), then restarts Gloom. Pinned apps (`pinned_apps.json`) and uploaded icons (`custom_icons/`) are preserved.
- Theme changes (`gloom-theme-*`) are applied by reading all theme values from `localStorage` and calling `applyTheme()` — the theme system depends on all five theme keys being in sync.
