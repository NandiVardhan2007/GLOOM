use serde::{Deserialize, Serialize};
use tauri::AppHandle;

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct MonitorInfo {
    /// Stable system identifier (e.g. "\\\\.\\DISPLAY1")
    pub id: String,
    /// Friendly display name (e.g. "DISPLAY1")
    pub name: String,
    /// Human-readable label (e.g. "Display 1 (Primary) 1920×1080")
    pub label: String,
    pub is_primary: bool,
    pub x: i32,
    pub y: i32,
    pub width: u32,
    pub height: u32,
    pub scale_factor: f64,
}

/// Query all connected monitors with stable identification, coordinates, and scale factors.
pub fn get_available_monitors(app: &AppHandle) -> Vec<MonitorInfo> {
    let Ok(monitors) = app.available_monitors() else {
        return Vec::new();
    };

    let primary_opt = app.primary_monitor().ok().flatten();
    let primary_name = primary_opt.as_ref().and_then(|m| m.name().cloned());
    let primary_pos = primary_opt.as_ref().map(|m| (m.position().x, m.position().y));

    let mut result: Vec<MonitorInfo> = monitors
        .into_iter()
        .enumerate()
        .map(|(idx, m)| {
            let m_name = m.name().cloned().unwrap_or_default();
            let pos = *m.position();
            let size = *m.size();
            let scale = m.scale_factor();

            // Match primary by name or position (0,0 is standard primary origin on Windows)
            let is_primary = if let Some(ref p_name) = primary_name {
                !m_name.is_empty() && &m_name == p_name
            } else if let Some((px, py)) = primary_pos {
                pos.x == px && pos.y == py
            } else {
                pos.x == 0 && pos.y == 0
            };

            // Stable ID is device name if available, else coordinate-based
            let id = if !m_name.is_empty() {
                m_name.clone()
            } else {
                format!("display_{}_{}", pos.x, pos.y)
            };

            // Friendly display number/name
            let display_num = idx + 1;
            let display_title = if !m_name.is_empty() {
                let trimmed = m_name.trim_start_matches(r"\\.\");
                format!("Display {display_num} ({trimmed})")
            } else {
                format!("Display {display_num}")
            };

            let primary_tag = if is_primary { " (Primary)" } else { "" };
            let label = format!(
                "{} {}{} {}×{}",
                display_title,
                primary_tag,
                if primary_tag.is_empty() { "" } else { " " },
                size.width,
                size.height
            );

            MonitorInfo {
                id,
                name: m_name,
                label,
                is_primary,
                x: pos.x,
                y: pos.y,
                width: size.width,
                height: size.height,
                scale_factor: scale,
            }
        })
        .collect();

    // Sort with primary first, then left-to-right by X coordinate
    result.sort_by(|a, b| {
        if a.is_primary && !b.is_primary {
            std::cmp::Ordering::Less
        } else if !a.is_primary && b.is_primary {
            std::cmp::Ordering::Greater
        } else {
            a.x.cmp(&b.x).then_with(|| a.y.cmp(&b.y))
        }
    });

    result
}

/// Resolves which monitor(s) the component should appear on based on the saved setting.
///
/// Supported setting values:
/// - `"all"`: Appears on all connected displays.
/// - `"primary"` (default): Appears on the primary display.
/// - `id` (e.g. `"\\\\.\\DISPLAY2"`): Appears on the specific display matching the id.
///
/// Fallback rule:
/// If the specified display is currently disconnected, falls back to the primary display.
/// When the display is reconnected, resolving again with the same setting returns it automatically.
pub fn resolve_monitors_for_setting(
    setting_value: &str,
    available: &[MonitorInfo],
) -> Vec<MonitorInfo> {
    if available.is_empty() {
        return Vec::new();
    }

    let primary = available
        .iter()
        .find(|m| m.is_primary)
        .cloned()
        .unwrap_or_else(|| available[0].clone());

    if setting_value == "all" {
        return available.to_vec();
    }

    if setting_value.is_empty() || setting_value == "primary" {
        return vec![primary];
    }

    // Check for exact ID match or friendly name match
    if let Some(target) = available
        .iter()
        .find(|m| m.id == setting_value || m.name == setting_value)
    {
        return vec![target.clone()];
    }

    // Fallback rule: saved monitor is disconnected, fall back to primary
    vec![primary]
}

/// Converts physical pixel dimensions to logical units based on the display scale factor.
pub fn physical_to_logical(physical_px: f64, scale_factor: f64) -> f64 {
    if scale_factor <= 0.0 {
        physical_px
    } else {
        physical_px / scale_factor
    }
}

/// Converts logical pixel dimensions to physical device pixels based on the display scale factor.
pub fn logical_to_physical(logical_px: f64, scale_factor: f64) -> f64 {
    if scale_factor <= 0.0 {
        logical_px
    } else {
        logical_px * scale_factor
    }
}

/// Calculates the window target rectangle (x, y, width, height) in physical pixels
/// for placing a window with given logical dimensions on a monitor with mixed DPI.
pub fn calculate_window_physical_bounds(
    mon: &MonitorInfo,
    logical_height: f64,
    gloom_scale: f64,
    is_bottom_dock: bool,
) -> (i32, i32, i32, i32) {
    let scaled_logical = logical_height * gloom_scale;
    let physical_height = logical_to_physical(scaled_logical, mon.scale_factor).round() as i32;
    let physical_width = mon.width as i32;
    let target_x = mon.x;
    let target_y = if is_bottom_dock {
        mon.y + mon.height as i32 - physical_height
    } else {
        mon.y
    };
    (target_x, target_y, physical_width, physical_height)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn sample_monitors() -> Vec<MonitorInfo> {
        vec![
            MonitorInfo {
                id: r"\\.\DISPLAY1".into(),
                name: "DISPLAY1".into(),
                label: "Display 1 (Primary) 1920×1080".into(),
                is_primary: true,
                x: 0,
                y: 0,
                width: 1920,
                height: 1080,
                scale_factor: 1.0,
            },
            MonitorInfo {
                id: r"\\.\DISPLAY2".into(),
                name: "DISPLAY2".into(),
                label: "Display 2 2560×1440".into(),
                is_primary: false,
                x: 1920,
                y: 0,
                width: 2560,
                height: 1440,
                scale_factor: 1.25,
            },
        ]
    }

    #[test]
    fn test_resolve_primary() {
        let mons = sample_monitors();
        let res = resolve_monitors_for_setting("primary", &mons);
        assert_eq!(res.len(), 1);
        assert_eq!(res[0].id, r"\\.\DISPLAY1");
        assert!(res[0].is_primary);
    }

    #[test]
    fn test_resolve_specific_monitor() {
        let mons = sample_monitors();
        let res = resolve_monitors_for_setting(r"\\.\DISPLAY2", &mons);
        assert_eq!(res.len(), 1);
        assert_eq!(res[0].id, r"\\.\DISPLAY2");
        assert_eq!(res[0].width, 2560);
    }

    #[test]
    fn test_resolve_all_monitors() {
        let mons = sample_monitors();
        let res = resolve_monitors_for_setting("all", &mons);
        assert_eq!(res.len(), 2);
        assert_eq!(res[0].id, r"\\.\DISPLAY1");
        assert_eq!(res[1].id, r"\\.\DISPLAY2");
    }

    #[test]
    fn test_fallback_when_saved_monitor_is_disconnected() {
        let mons = sample_monitors();
        // User had saved DISPLAY3 which is no longer connected
        let res = resolve_monitors_for_setting(r"\\.\DISPLAY3", &mons);
        assert_eq!(res.len(), 1);
        // Must fall back to primary display
        assert_eq!(res[0].id, r"\\.\DISPLAY1");
        assert!(res[0].is_primary);
    }

    #[test]
    fn test_restoration_when_disconnected_monitor_reconnects() {
        let mut mons = sample_monitors();
        let saved_setting = r"\\.\DISPLAY3";

        // Step 1: DISPLAY3 is not connected -> falls back to primary
        assert_eq!(resolve_monitors_for_setting(saved_setting, &mons)[0].id, r"\\.\DISPLAY1");

        // Step 2: DISPLAY3 is reconnected
        mons.push(MonitorInfo {
            id: r"\\.\DISPLAY3".into(),
            name: "DISPLAY3".into(),
            label: "Display 3 1920×1080".into(),
            is_primary: false,
            x: -1920,
            y: 0,
            width: 1920,
            height: 1080,
            scale_factor: 1.0,
        });

        // Step 3: Resolving with the same saved setting now targets DISPLAY3!
        let reconnected = resolve_monitors_for_setting(saved_setting, &mons);
        assert_eq!(reconnected.len(), 1);
        assert_eq!(reconnected[0].id, r"\\.\DISPLAY3");
        assert_eq!(reconnected[0].x, -1920);
    }

    #[test]
    fn test_empty_and_invalid_settings_default_to_primary() {
        let mons = sample_monitors();
        assert_eq!(resolve_monitors_for_setting("", &mons)[0].id, r"\\.\DISPLAY1");
        assert_eq!(resolve_monitors_for_setting("garbage_value", &mons)[0].id, r"\\.\DISPLAY1");
    }

    #[test]
    fn test_handles_empty_monitor_list_without_panic() {
        let res = resolve_monitors_for_setting("all", &[]);
        assert!(res.is_empty());
    }

    #[test]
    fn test_preserves_negative_coordinates_and_different_dpi() {
        let mons = vec![MonitorInfo {
            id: r"\\.\DISPLAY_LEFT".into(),
            name: "DISPLAY_LEFT".into(),
            label: "Left Monitor".into(),
            is_primary: false,
            x: -2560,
            y: -200,
            width: 2560,
            height: 1440,
            scale_factor: 1.5,
        }];
        let res = resolve_monitors_for_setting(r"\\.\DISPLAY_LEFT", &mons);
        assert_eq!(res[0].x, -2560);
        assert_eq!(res[0].y, -200);
        assert_eq!(res[0].scale_factor, 1.5);
    }

    #[test]
    fn test_physical_to_logical_conversions() {
        // 100% scale (scale factor 1.0)
        assert_eq!(physical_to_logical(1920.0, 1.0), 1920.0);
        assert_eq!(logical_to_physical(1920.0, 1.0), 1920.0);

        // 125% scale (scale factor 1.25)
        assert_eq!(physical_to_logical(2560.0, 1.25), 2048.0);
        assert_eq!(logical_to_physical(2048.0, 1.25), 2560.0);

        // 150% scale (scale factor 1.5)
        assert_eq!(physical_to_logical(1500.0, 1.5), 1000.0);
        assert_eq!(logical_to_physical(1000.0, 1.5), 1500.0);

        // 200% scale (scale factor 2.0)
        assert_eq!(physical_to_logical(3840.0, 2.0), 1920.0);
        assert_eq!(logical_to_physical(1920.0, 2.0), 3840.0);
    }

    #[test]
    fn test_calculate_window_physical_bounds_top_notch() {
        let primary = MonitorInfo {
            id: r"\\.\DISPLAY1".into(),
            name: "DISPLAY1".into(),
            label: "Primary".into(),
            is_primary: true,
            x: 0,
            y: 0,
            width: 1920,
            height: 1080,
            scale_factor: 1.0,
        };
        // Top notch with logical height 420.0, gloom scale 1.0
        let (x, y, w, h) = calculate_window_physical_bounds(&primary, 420.0, 1.0, false);
        assert_eq!((x, y, w, h), (0, 0, 1920, 420));

        // Mixed DPI secondary display at x=1920, 125% scale (1.25)
        let secondary = MonitorInfo {
            id: r"\\.\DISPLAY2".into(),
            name: "DISPLAY2".into(),
            label: "Secondary 125%".into(),
            is_primary: false,
            x: 1920,
            y: 0,
            width: 2560,
            height: 1440,
            scale_factor: 1.25,
        };
        let (sx, sy, sw, sh) = calculate_window_physical_bounds(&secondary, 420.0, 1.0, false);
        assert_eq!(sx, 1920);
        assert_eq!(sy, 0);
        assert_eq!(sw, 2560);
        // 420.0 * 1.25 = 525 physical px
        assert_eq!(sh, 525);
    }

    #[test]
    fn test_calculate_window_physical_bounds_bottom_dock() {
        // Bottom dock on monitor with negative X coordinate and 150% scaling
        let left_mon = MonitorInfo {
            id: r"\\.\DISPLAY_LEFT".into(),
            name: "DISPLAY_LEFT".into(),
            label: "Left Display".into(),
            is_primary: false,
            x: -2560,
            y: -200,
            width: 2560,
            height: 1440,
            scale_factor: 1.5,
        };
        // Dock height 600.0, scale 1.0
        let (lx, ly, lw, lh) = calculate_window_physical_bounds(&left_mon, 600.0, 1.0, true);
        assert_eq!(lx, -2560);
        // 600 * 1.5 = 900 physical px
        assert_eq!(lh, 900);
        // y + height - physical_height = -200 + 1440 - 900 = 340
        assert_eq!(ly, 340);
        assert_eq!(lw, 2560);
    }
}
