use serde::{Deserialize, Serialize};
use tauri::AppHandle;

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct MonitorInfo {
    /// Stable system identifier (hardware interface path or PNP ID if available,
    /// falling back to GDI device name "\\\\.\\DISPLAY1")
    pub id: String,
    /// Win32 GDI device name (e.g. "\\\\.\\DISPLAY1")
    pub gdi_name: String,
    /// Friendly display name or model (e.g. "Dell U2720Q" or "DISPLAY1")
    pub name: String,
    /// Human-readable label (e.g. "Monitor 1 - Dell U2720Q (Primary) 1920×1080 - 125%")
    pub label: String,
    pub is_primary: bool,
    pub x: i32,
    pub y: i32,
    pub width: u32,
    pub height: u32,
    pub scale_factor: f64,
}

#[cfg(target_os = "windows")]
pub fn query_hardware_id_and_model(gdi_name: &str) -> (Option<String>, Option<String>) {
    use windows::Win32::Graphics::Gdi::{EnumDisplayDevicesW, DISPLAY_DEVICEW};
    use windows::core::PCWSTR;

    const EDD_GET_DEVICE_INTERFACE_NAME: u32 = 1;

    let wide_name: Vec<u16> = gdi_name.encode_utf16().chain(std::iter::once(0)).collect();
    let mut dd = DISPLAY_DEVICEW {
        cb: std::mem::size_of::<DISPLAY_DEVICEW>() as u32,
        ..Default::default()
    };

    // First attempt: EDD_GET_DEVICE_INTERFACE_NAME (persistent device interface path)
    if unsafe {
        EnumDisplayDevicesW(
            PCWSTR(wide_name.as_ptr()),
            0,
            &mut dd,
            EDD_GET_DEVICE_INTERFACE_NAME,
        )
    }
    .as_bool()
    {
        let id_len = dd.DeviceID.iter().position(|&c| c == 0).unwrap_or(dd.DeviceID.len());
        let str_len = dd.DeviceString.iter().position(|&c| c == 0).unwrap_or(dd.DeviceString.len());
        let dev_id = String::from_utf16_lossy(&dd.DeviceID[..id_len]).trim().to_string();
        let dev_string = String::from_utf16_lossy(&dd.DeviceString[..str_len]).trim().to_string();

        let hw_id = if !dev_id.is_empty() { Some(dev_id) } else { None };
        let model = if !dev_string.is_empty() { Some(dev_string) } else { None };
        if hw_id.is_some() || model.is_some() {
            return (hw_id, model);
        }
    }

    // Fallback: standard flags (retrieves monitor hardware PNP ID)
    let mut dd_fallback = DISPLAY_DEVICEW {
        cb: std::mem::size_of::<DISPLAY_DEVICEW>() as u32,
        ..Default::default()
    };
    if unsafe {
        EnumDisplayDevicesW(
            PCWSTR(wide_name.as_ptr()),
            0,
            &mut dd_fallback,
            0,
        )
    }
    .as_bool()
    {
        let id_len = dd_fallback.DeviceID.iter().position(|&c| c == 0).unwrap_or(dd_fallback.DeviceID.len());
        let str_len = dd_fallback.DeviceString.iter().position(|&c| c == 0).unwrap_or(dd_fallback.DeviceString.len());
        let dev_id = String::from_utf16_lossy(&dd_fallback.DeviceID[..id_len]).trim().to_string();
        let dev_string = String::from_utf16_lossy(&dd_fallback.DeviceString[..str_len]).trim().to_string();

        let hw_id = if !dev_id.is_empty() { Some(dev_id) } else { None };
        let model = if !dev_string.is_empty() { Some(dev_string) } else { None };
        return (hw_id, model);
    }

    (None, None)
}

#[cfg(not(target_os = "windows"))]
pub fn query_hardware_id_and_model(_gdi_name: &str) -> (Option<String>, Option<String>) {
    (None, None)
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

            let (hw_id, model) = query_hardware_id_and_model(&m_name);

            // Match primary by name or position (0,0 is standard primary origin on Windows)
            let is_primary = if let Some(ref p_name) = primary_name {
                !m_name.is_empty() && &m_name == p_name
            } else if let Some((px, py)) = primary_pos {
                pos.x == px && pos.y == py
            } else {
                pos.x == 0 && pos.y == 0
            };

            // Stable ID prefers hardware identifier when available, falling back to GDI device name
            let id = hw_id.unwrap_or_else(|| {
                if !m_name.is_empty() {
                    m_name.clone()
                } else {
                    format!("display_{}_{}", pos.x, pos.y)
                }
            });

            let display_num = idx + 1;
            let friendly_name = model.unwrap_or_else(|| {
                if !m_name.is_empty() {
                    m_name.trim_start_matches(r"\\.\").to_string()
                } else {
                    format!("Display {display_num}")
                }
            });

            let scale_pct = (scale * 100.0).round() as i32;
            let primary_tag = if is_primary { " (Primary)" } else { "" };
            let label = format!(
                "Monitor {display_num} - {}{primary_tag} {}×{} - {scale_pct}%",
                friendly_name,
                size.width,
                size.height
            );

            MonitorInfo {
                id,
                gdi_name: m_name.clone(),
                name: friendly_name,
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

/// Parses monitor setting strings which may be:
/// - JSON array string: `["id1", "id2"]`
/// - Comma-separated list: `"id1, id2"`
/// - Single ID: `"id1"`
pub fn parse_setting_ids(setting_value: &str) -> Vec<String> {
    let trimmed = setting_value.trim();
    if trimmed.is_empty() {
        return Vec::new();
    }
    // Try JSON array first
    if trimmed.starts_with('[') && trimmed.ends_with(']') {
        if let Ok(list) = serde_json::from_str::<Vec<String>>(trimmed) {
            let mut deduplicated = Vec::new();
            for item in list {
                let s = item.trim().to_string();
                if !s.is_empty() && !deduplicated.contains(&s) {
                    deduplicated.push(s);
                }
            }
            return deduplicated;
        }
    }
    // Comma-separated list
    if trimmed.contains(',') {
        let mut deduplicated = Vec::new();
        for part in trimmed.split(',') {
            let s = part.trim().to_string();
            if !s.is_empty() && !deduplicated.contains(&s) {
                deduplicated.push(s);
            }
        }
        return deduplicated;
    }
    vec![trimmed.to_string()]
}

/// Resolves which monitor(s) the component should appear on based on the saved setting.
///
/// Supported setting values:
/// - `"all"`: Appears on all connected displays.
/// - `"primary"` (default): Appears on the primary display.
/// - JSON array or comma-separated list of IDs: Appears on each matching connected display.
/// - Single monitor ID (hardware ID, GDI device name, or friendly name): Appears on that display.
///
/// Fallback rule:
/// If none of the saved monitors are currently connected, falls back to the primary display
/// without modifying the saved list, and restores the choice when a saved monitor reconnects.
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

    let trimmed = setting_value.trim();
    if trimmed.is_empty() || trimmed == "primary" {
        return vec![primary];
    }

    if trimmed == "all" {
        return available.to_vec();
    }

    let parsed_ids = parse_setting_ids(trimmed);
    if parsed_ids.is_empty() {
        return vec![primary];
    }

    let mut matched = Vec::new();
    for id in &parsed_ids {
        if let Some(mon) = available.iter().find(|m| {
            &m.id == id || &m.gdi_name == id || &m.name == id
        }) {
            if !matched.iter().any(|existing: &MonitorInfo| existing.id == mon.id) {
                matched.push(mon.clone());
            }
        }
    }

    if !matched.is_empty() {
        matched
    } else {
        // Fallback: none of the saved monitors are connected -> fall back to primary
        vec![primary]
    }
}

/// Generates a deterministic, URL-safe alphanumeric label suffix for a monitor
/// that contains no backslashes or dots (safe for Tauri window labels).
pub fn safe_monitor_label_suffix(mon: &MonitorInfo) -> String {
    if mon.is_primary {
        String::new()
    } else {
        use std::collections::hash_map::DefaultHasher;
        use std::hash::{Hash, Hasher};
        let mut hasher = DefaultHasher::new();
        mon.id.hash(&mut hasher);
        let hash = hasher.finish();

        let sanitized: String = mon
            .name
            .chars()
            .filter(|c| c.is_alphanumeric())
            .take(10)
            .collect();
        if sanitized.is_empty() {
            format!("{:08x}", hash & 0xffff_ffff)
        } else {
            format!("{}-{:08x}", sanitized.to_lowercase(), hash & 0xffff_ffff)
        }
    }
}

/// Derives a stable Tauri window label for a notch window on a given monitor.
/// Primary monitor is always "main"; secondary monitors are "main-{suffix}".
/// All match the capability pattern "main*".
pub fn safe_notch_window_label(mon: &MonitorInfo) -> String {
    if mon.is_primary {
        "main".to_string()
    } else {
        format!("main-{}", safe_monitor_label_suffix(mon))
    }
}

/// Derives a stable Tauri window label for a dock window on a given monitor.
/// Primary monitor is always "dock"; secondary monitors are "dock-{suffix}".
/// All match the capability pattern "dock*".
pub fn safe_dock_window_label(mon: &MonitorInfo) -> String {
    if mon.is_primary {
        "dock".to_string()
    } else {
        format!("dock-{}", safe_monitor_label_suffix(mon))
    }
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

    fn sample_3_monitors() -> Vec<MonitorInfo> {
        vec![
            MonitorInfo {
                id: r"MONITOR\DEL4128\1".into(),
                gdi_name: r"\\.\DISPLAY1".into(),
                name: "Dell U2720Q".into(),
                label: "Monitor 1 - Dell U2720Q (Primary) 1920×1080 - 100%".into(),
                is_primary: true,
                x: 0,
                y: 0,
                width: 1920,
                height: 1080,
                scale_factor: 1.0,
            },
            MonitorInfo {
                id: r"MONITOR\LG27850\2".into(),
                gdi_name: r"\\.\DISPLAY2".into(),
                name: "LG 27GL850".into(),
                label: "Monitor 2 - LG 27GL850 2560×1440 - 125%".into(),
                is_primary: false,
                x: 1920,
                y: 0,
                width: 2560,
                height: 1440,
                scale_factor: 1.25,
            },
            MonitorInfo {
                id: r"MONITOR\ASUSVG27\3".into(),
                gdi_name: r"\\.\DISPLAY3".into(),
                name: "ASUS VG279".into(),
                label: "Monitor 3 - ASUS VG279 1920×1080 - 100%".into(),
                is_primary: false,
                x: 4480,
                y: 0,
                width: 1920,
                height: 1080,
                scale_factor: 1.0,
            },
        ]
    }

    #[test]
    fn test_resolve_subset_of_3_monitors() {
        let mons = sample_3_monitors();
        // User selects Monitor 1 and Monitor 3 (skipping Monitor 2)
        let setting = serde_json::to_string(&vec![r"MONITOR\DEL4128\1", r"MONITOR\ASUSVG27\3"]).unwrap();
        let res = resolve_monitors_for_setting(&setting, &mons);
        assert_eq!(res.len(), 2);
        assert_eq!(res[0].id, r"MONITOR\DEL4128\1");
        assert_eq!(res[1].id, r"MONITOR\ASUSVG27\3");
    }

    #[test]
    fn test_resolve_selected_monitor_unplugged() {
        let mut mons = sample_3_monitors();
        // User had saved monitors 1 and 3
        let setting = serde_json::to_string(&vec![r"MONITOR\DEL4128\1", r"MONITOR\ASUSVG27\3"]).unwrap();

        // Monitor 3 is unplugged (only 1 and 2 remain connected)
        mons.pop();
        assert_eq!(mons.len(), 2);

        let res = resolve_monitors_for_setting(&setting, &mons);
        // Surviving selected monitor (Monitor 1) is returned
        assert_eq!(res.len(), 1);
        assert_eq!(res[0].id, r"MONITOR\DEL4128\1");
    }

    #[test]
    fn test_resolve_all_selected_monitors_unplugged_falls_back_to_primary() {
        let mons = sample_3_monitors();
        // User had saved monitor 2 and 3 only (external displays)
        let setting = serde_json::to_string(&vec![r"MONITOR\LG27850\2", r"MONITOR\ASUSVG27\3"]).unwrap();

        // Both external displays are disconnected; only primary (Monitor 1) remains connected
        let connected = vec![mons[0].clone()];
        let res = resolve_monitors_for_setting(&setting, &connected);
        assert_eq!(res.len(), 1);
        // Falls back to primary monitor
        assert_eq!(res[0].id, r"MONITOR\DEL4128\1");
        assert!(res[0].is_primary);
    }

    #[test]
    fn test_reconnect_restores_saved_choice_without_overwriting() {
        let mons = sample_3_monitors();
        let saved_setting = serde_json::to_string(&vec![r"MONITOR\ASUSVG27\3"]).unwrap();

        // Disconnected state: only Monitor 1 connected -> falls back to primary
        let connected_without_3 = vec![mons[0].clone(), mons[1].clone()];
        assert_eq!(resolve_monitors_for_setting(&saved_setting, &connected_without_3)[0].id, r"MONITOR\DEL4128\1");

        // Monitor 3 is reconnected -> resolves to Monitor 3 automatically
        let reconnected = resolve_monitors_for_setting(&saved_setting, &mons);
        assert_eq!(reconnected.len(), 1);
        assert_eq!(reconnected[0].id, r"MONITOR\ASUSVG27\3");
    }

    #[test]
    fn test_legacy_value_migration() {
        let mons = sample_3_monitors();

        // Legacy "primary"
        let res_primary = resolve_monitors_for_setting("primary", &mons);
        assert_eq!(res_primary.len(), 1);
        assert!(res_primary[0].is_primary);

        // Legacy "all"
        let res_all = resolve_monitors_for_setting("all", &mons);
        assert_eq!(res_all.len(), 3);

        // Legacy single GDI name e.g. "\\.\DISPLAY2"
        let res_gdi = resolve_monitors_for_setting(r"\\.\DISPLAY2", &mons);
        assert_eq!(res_gdi.len(), 1);
        assert_eq!(res_gdi[0].gdi_name, r"\\.\DISPLAY2");
        assert_eq!(res_gdi[0].id, r"MONITOR\LG27850\2");
    }

    #[test]
    fn test_ignores_unknown_and_duplicate_ids() {
        let mons = sample_3_monitors();
        // Contains unknown ID "MONITOR_PHANTOM" and duplicate IDs
        let setting = serde_json::to_string(&vec![
            r"MONITOR\DEL4128\1",
            r"MONITOR_PHANTOM_UNKNOWN",
            r"MONITOR\DEL4128\1",
            r"MONITOR\LG27850\2",
            r"MONITOR\LG27850\2",
        ]).unwrap();

        let res = resolve_monitors_for_setting(&setting, &mons);
        assert_eq!(res.len(), 2);
        assert_eq!(res[0].id, r"MONITOR\DEL4128\1");
        assert_eq!(res[1].id, r"MONITOR\LG27850\2");
    }

    #[test]
    fn test_window_label_stability_and_capability_conformance() {
        let mons = sample_3_monitors();

        let notch_primary = safe_notch_window_label(&mons[0]);
        let dock_primary = safe_dock_window_label(&mons[0]);
        assert_eq!(notch_primary, "main");
        assert_eq!(dock_primary, "dock");

        let notch_sec = safe_notch_window_label(&mons[1]);
        let dock_sec = safe_dock_window_label(&mons[1]);

        // Must start with main* and dock* for Tauri capabilities
        assert!(notch_sec.starts_with("main-"));
        assert!(dock_sec.starts_with("dock-"));

        // Must contain NO backslashes or dots
        assert!(!notch_sec.contains('\\'));
        assert!(!notch_sec.contains('.'));
        assert!(!dock_sec.contains('\\'));
        assert!(!dock_sec.contains('.'));

        // Suffix must be 100% deterministic and stable across calls
        assert_eq!(notch_sec, safe_notch_window_label(&mons[1]));
        assert_eq!(dock_sec, safe_dock_window_label(&mons[1]));
    }

    #[test]
    fn test_physical_to_logical_conversions() {
        assert_eq!(physical_to_logical(1920.0, 1.0), 1920.0);
        assert_eq!(logical_to_physical(1920.0, 1.0), 1920.0);
        assert_eq!(physical_to_logical(2560.0, 1.25), 2048.0);
        assert_eq!(logical_to_physical(2048.0, 1.25), 2560.0);
    }

    #[test]
    fn test_calculate_window_physical_bounds_top_notch() {
        let primary = MonitorInfo {
            id: r"MONITOR\1".into(),
            gdi_name: r"\\.\DISPLAY1".into(),
            name: "Primary".into(),
            label: "Primary".into(),
            is_primary: true,
            x: 0,
            y: 0,
            width: 1920,
            height: 1080,
            scale_factor: 1.0,
        };
        let (x, y, w, h) = calculate_window_physical_bounds(&primary, 420.0, 1.0, false);
        assert_eq!((x, y, w, h), (0, 0, 1920, 420));
    }

    #[test]
    fn test_calculate_window_physical_bounds_bottom_dock() {
        let sec = MonitorInfo {
            id: r"MONITOR\2".into(),
            gdi_name: r"\\.\DISPLAY2".into(),
            name: "Sec".into(),
            label: "Sec".into(),
            is_primary: false,
            x: 1920,
            y: 0,
            width: 2560,
            height: 1440,
            scale_factor: 1.25,
        };
        let (lx, ly, lw, lh) = calculate_window_physical_bounds(&sec, 600.0, 1.0, true);
        assert_eq!(lx, 1920);
        assert_eq!(lw, 2560);
        // 600 * 1.25 = 750 physical px
        assert_eq!(lh, 750);
        // y + height - physical_height = 0 + 1440 - 750 = 690
        assert_eq!(ly, 690);
    }
}
