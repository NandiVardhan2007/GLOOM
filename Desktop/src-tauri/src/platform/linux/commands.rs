//! Linux Commands implementation for GLOOM.

use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use std::fs;
use std::process::Command;
use tauri::{AppHandle, Manager, Window};

use crate::state::COMMAND_SENDER;
use crate::types::{AppInfo, AudioSessionInfo, IntRect, SystemCommand, VolumeChangeEvent, WifiStatus};

#[derive(Serialize, Deserialize, Clone, Copy, Debug)]
pub struct WindowGeometry {
    pub x: i32,
    pub y: i32,
    pub width: i32,
    pub height: i32,
    pub is_minimized: bool,
}

#[derive(Deserialize)]
#[serde(rename_all = "kebab-case")]
pub enum SystemAction {
    TaskManager,
    DiskManagement,
    DeviceManager,
    ComputerManagement,
    Settings,
    TaskbarSettings,
    InstalledApps,
    PowerOptions,
    NetworkConnections,
    TaskView,
    FileExplorer,
    LockScreen,
}

#[tauri::command]
pub async fn set_menu_open(_open: bool, _rect: Option<IntRect>) {}

#[tauri::command]
pub async fn set_dock_hovered(_hovered: bool) {}

#[tauri::command]
pub async fn set_notch_hovered(_app: AppHandle, _hovered: bool) {}

#[tauri::command]
pub async fn set_notch_visible(_visible: bool) {}

#[tauri::command]
pub async fn set_notch_open(_open: bool) {}

#[tauri::command]
pub fn get_fullscreen_game_state() -> Result<crate::services::FullscreenGamePayload, String> {
    Ok(crate::services::FullscreenGamePayload {
        is_fullscreen: false,
        reason: "none".to_string(),
    })
}

#[tauri::command]
pub fn setup_shelf_drop(_app: AppHandle) {}

#[tauri::command]
pub async fn shelf_thumbnail(path: String) -> crate::shelf_preview::ShelfPreview {
    crate::shelf_preview::preview(&path)
}

#[tauri::command]
pub async fn shelf_open(path: String) -> Result<(), String> {
    Command::new("xdg-open")
        .arg(&path)
        .spawn()
        .map_err(|e| e.to_string())?;
    Ok(())
}

#[tauri::command]
pub fn get_platform() -> &'static str {
    "linux"
}

#[tauri::command]
pub async fn shelf_prune(paths: Vec<String>) -> Vec<String> {
    paths
        .into_iter()
        .filter(|p| std::path::Path::new(p).exists())
        .collect()
}

#[tauri::command]
pub fn shelf_drag_icon() -> Option<String> {
    None
}

#[tauri::command]
pub async fn update_dock_rect(_rect: IntRect) {}

#[tauri::command]
pub async fn update_notch_rect(_rect: IntRect) {}

#[tauri::command]
pub fn set_window_height(_window: Window, _height: f64) {}

#[tauri::command]
pub fn resize_settings_window(app: AppHandle, width: f64, height: f64) {
    if let Some(w) = app.get_webview_window("settings") {
        let _ = w.set_size(tauri::LogicalSize::new(width, height));
    }
}

#[tauri::command]
pub fn set_ignore_cursor_events(window: Window, ignore: bool) {
    if let Ok(gtk_win) = window.gtk_window() {
        use gtk::prelude::*;
        if ignore {
            let empty = cairo::Region::create();
            gtk_win.input_shape_combine_region(Some(&empty));
        } else {
            gtk_win.input_shape_combine_region(None);
        }
    }
}

#[tauri::command]
pub async fn init_dock(_app: AppHandle, _mode: String) {}

#[tauri::command]
pub async fn toggle_dock(_app: AppHandle, _enable: bool) {}

#[tauri::command]
pub async fn sync_appbar(app: AppHandle) {
    crate::utils::reconcile_main_appbar(&app);
}

#[tauri::command]
pub async fn change_dock_mode(_app: AppHandle, _mode: String) {}

#[tauri::command]
pub async fn change_notch_mode(app: AppHandle, _mode: String) {
    crate::utils::reconcile_main_appbar(&app);
}

#[tauri::command]
pub fn is_start_menu_open() -> bool {
    false
}

#[tauri::command]
pub async fn open_app(_app: AppHandle, app_name: String) {
    let _ = Command::new("gtk-launch").arg(&app_name).spawn().or_else(|_| {
        Command::new("xdg-open").arg(&app_name).spawn()
    });
}

#[tauri::command]
pub async fn launch_new_instance(app_path: String, _app_name: Option<String>) {
    let _ = Command::new(&app_path).spawn().or_else(|_| {
        Command::new("gtk-launch").arg(&app_path).spawn()
    });
}

#[tauri::command]
pub async fn get_active_windows() -> Vec<AppInfo> {
    Vec::new()
}

#[tauri::command]
pub async fn get_window_geometry(_hwnd: isize) -> Option<WindowGeometry> {
    None
}

#[tauri::command]
pub async fn focus_window(_app: AppHandle, _hwnd: isize) -> bool {
    true
}

#[tauri::command]
pub async fn focus_app_windows(_hwnds: Vec<isize>) {}

#[tauri::command]
pub fn is_aumid_path(_path: &str) -> bool {
    false
}

#[tauri::command]
pub fn find_pwa_shortcut_path(_keyword: &str) -> Option<String> {
    None
}

#[tauri::command]
pub fn find_pwa_shortcut_or_icon(_name_or_title: &str) -> Option<String> {
    None
}

#[tauri::command]
pub fn schedule_icon_cache_save(_app: AppHandle) {}

#[tauri::command]
pub async fn get_app_icon(
    _app: AppHandle,
    _path: String,
    _name: String,
) -> Option<String> {
    None
}

#[tauri::command]
pub fn save_pinned_apps(_app: AppHandle, _apps: Vec<AppInfo>) -> Result<(), String> {
    Ok(())
}

#[tauri::command]
pub async fn load_pinned_apps(_app: AppHandle) -> Vec<AppInfo> {
    Vec::new()
}

#[tauri::command]
pub async fn clear_icon_cache(_app: AppHandle) -> Result<(), String> {
    Ok(())
}

#[tauri::command]
pub async fn set_custom_icon(
    _app: AppHandle,
    _key: String,
    _data: String,
) -> Result<(), String> {
    Ok(())
}

#[tauri::command]
pub async fn remove_custom_icon(_app: AppHandle, _key: String) -> Result<(), String> {
    Ok(())
}

#[tauri::command]
pub async fn get_custom_icons(_app: AppHandle) -> Result<HashMap<String, String>, String> {
    Ok(HashMap::new())
}

#[tauri::command]
pub async fn get_installed_apps() -> Vec<AppInfo> {
    let mut apps = Vec::new();
    let dirs = [
        "/usr/share/applications",
        "/var/lib/snapd/desktop/applications",
        "/var/lib/flatpak/exports/share/applications",
    ];

    for d in dirs {
        if let Ok(entries) = fs::read_dir(d) {
            for entry in entries.flatten() {
                let path = entry.path();
                if path.extension().and_then(|s| s.to_str()) == Some("desktop") {
                    let name = path.file_stem().unwrap_or_default().to_string_lossy().to_string();
                    apps.push(AppInfo {
                        name,
                        path: path.to_string_lossy().to_string(),
                        icon: None,
                        is_running: false,
                        hwnd: None,
                        executable: None,
                        all_hwnds: None,
                        is_minimized: None,
                    });
                }
            }
        }
    }
    apps
}

#[tauri::command]
pub fn hide_native_osd() {}

#[tauri::command]
pub fn open_settings_window(app: AppHandle) {
    if let Some(w) = app.get_webview_window("settings") {
        let _ = w.show();
        let _ = w.set_focus();
    }
}

#[tauri::command]
pub fn hide_overlay(app: AppHandle) {
    if let Some(w) = app.get_webview_window("overlay") {
        let _ = w.hide();
    }
}

#[tauri::command]
pub fn set_splash_fullscreen(app: AppHandle, fullscreen: bool) {
    crate::state::OVERLAY_IN_SPLASH.store(fullscreen, std::sync::atomic::Ordering::Relaxed);
    if let Some(win) = app.get_webview_window("overlay") {
        if fullscreen {
            let _ = win.hide();
            if let Some(monitor) = crate::utils::get_best_monitor(&app, &win) {
                let size = monitor.size();
                let pos = monitor.position();
                let _ = win.set_position(tauri::PhysicalPosition::new(pos.x, pos.y));
                let _ = win.set_size(tauri::PhysicalSize::new(size.width, size.height));
            }
            let _ = win.show();
        } else {
            let _ = win.hide();
        }
    }
}

#[tauri::command]
pub fn sync_overlay_position(app: AppHandle) {
    if let Some(win) = app.get_webview_window("overlay") {
        if let Some(monitor) = crate::utils::get_best_monitor(&app, &win) {
            let size = monitor.size();
            let pos = monitor.position();
            let _ = win.set_position(tauri::PhysicalPosition::new(pos.x, pos.y));
            let _ = win.set_size(tauri::PhysicalSize::new(size.width, size.height));
        }
    }
}

#[tauri::command]
pub fn open_wifi_settings() {
    let _ = Command::new("gnome-control-center").arg("wifi").spawn();
}

#[tauri::command]
pub fn open_sound_settings() {
    let _ = Command::new("gnome-control-center").arg("sound").spawn();
}

#[tauri::command]
pub fn open_notification_center() {
    let _ = Command::new("gnome-control-center").arg("notifications").spawn();
}

#[tauri::command]
pub fn open_system_tray() {}

#[tauri::command]
pub fn media_play_pause() {
    if let Some(tx) = COMMAND_SENDER.get() {
        let _ = tx.send(SystemCommand::MediaPlayPause);
    }
}

#[tauri::command]
pub fn media_next() {
    if let Some(tx) = COMMAND_SENDER.get() {
        let _ = tx.send(SystemCommand::MediaNext);
    }
}

#[tauri::command]
pub fn media_previous() {
    if let Some(tx) = COMMAND_SENDER.get() {
        let _ = tx.send(SystemCommand::MediaPrevious);
    }
}

#[tauri::command]
pub fn media_seek(position_ms: f64) {
    if let Some(tx) = COMMAND_SENDER.get() {
        let _ = tx.send(SystemCommand::MediaSeek(position_ms as i64));
    }
}

#[tauri::command]
pub fn open_media_source_app() {}

#[tauri::command]
pub fn set_volume(volume: f32) {
    if let Some(tx) = COMMAND_SENDER.get() {
        let _ = tx.send(SystemCommand::SetVolume(volume));
    }
}

#[tauri::command]
pub async fn get_audio_sessions() -> Result<Vec<AudioSessionInfo>, String> {
    Ok(Vec::new())
}

#[tauri::command]
pub async fn set_app_volume(_pid: u32, _volume: f32) -> Result<(), String> {
    Ok(())
}

#[tauri::command]
pub async fn set_app_mute(_pid: u32, _muted: bool) -> Result<(), String> {
    Ok(())
}

#[tauri::command]
pub fn set_volume_mixer_rect(_x: f64, _y: f64, _width: f64, _height: f64) {}

#[tauri::command]
pub fn clear_volume_mixer_rect() {}

pub fn release_appbars(_handle: &AppHandle) {}

pub fn restore_taskbar_and_exit(handle: &AppHandle) {
    handle.exit(0);
}

#[tauri::command]
pub async fn quit_gloom(handle: AppHandle) {
    handle.exit(0);
}

#[tauri::command]
pub async fn restart_gloom(handle: AppHandle) {
    handle.restart();
}

#[tauri::command]
pub async fn close_window(_hwnd: isize) {}

#[tauri::command]
pub fn save_setting(app: AppHandle, key: String, value: serde_json::Value) -> Result<(), String> {
    use tauri::Emitter;
    let path = app
        .path()
        .app_config_dir()
        .map_err(|e| e.to_string())?
        .join("settings.json");
    if let Some(parent) = path.parent() {
        let _ = std::fs::create_dir_all(parent);
    }
    let settings = crate::utils::save_setting_file(&path, key.clone(), value)?;

    let _ = app.emit(
        "settings-changed",
        serde_json::json!({ "key": &key, "value": &settings[&key] }),
    );

    Ok(())
}

#[tauri::command]
pub fn load_settings(app: AppHandle) -> Result<HashMap<String, serde_json::Value>, String> {
    let path = app
        .path()
        .app_config_dir()
        .map_err(|e| e.to_string())?
        .join("settings.json");
    crate::utils::read_settings_file(&path)
}

#[tauri::command]
pub fn settings_file_readable(app: AppHandle) -> bool {
    app.path()
        .app_config_dir()
        .map(|p| p.join("settings.json").exists())
        .unwrap_or(false)
}

#[tauri::command]
pub fn get_autostart_status(app: AppHandle) -> bool {
    crate::autostart::is_autostart_enabled(&app)
}

#[tauri::command]
pub fn set_autostart_status(app: AppHandle, enabled: bool) -> Result<(), String> {
    if enabled {
        crate::autostart::enable_autostart(&app)?;
        save_setting(app, "gloom-autostart".to_string(), serde_json::Value::Bool(true))?;
    } else {
        crate::autostart::disable_all_autostart(&app);
        save_setting(app, "gloom-autostart".to_string(), serde_json::Value::Bool(false))?;
    }
    Ok(())
}

#[tauri::command]
pub async fn capture_window_thumbnail(
    _hwnd: isize,
    _max_width: u32,
    _max_height: u32,
) -> Option<String> {
    None
}

#[tauri::command]
pub fn get_volume() -> f32 {
    if let Ok(out) = Command::new("wpctl").args(["get-volume", "@DEFAULT_AUDIO_SINK@"]).output() {
        let s = String::from_utf8_lossy(&out.stdout);
        let parts: Vec<&str> = s.split_whitespace().collect();
        if parts.len() >= 2 {
            if let Ok(v) = parts[1].parse::<f32>() {
                return v.clamp(0.0, 1.0);
            }
        }
    }
    0.5
}

#[tauri::command]
pub async fn get_volume_state() -> Result<VolumeChangeEvent, String> {
    let mut vol = 0.5f32;
    let mut is_muted = false;
    if let Ok(out) = Command::new("wpctl").args(["get-volume", "@DEFAULT_AUDIO_SINK@"]).output() {
        let s = String::from_utf8_lossy(&out.stdout);
        is_muted = s.contains("[MUTED]");
        let parts: Vec<&str> = s.split_whitespace().collect();
        if parts.len() >= 2 {
            if let Ok(v) = parts[1].parse::<f32>() {
                vol = v.clamp(0.0, 1.0);
            }
        }
    }
    Ok(VolumeChangeEvent {
        volume: vol,
        is_muted,
    })
}

#[tauri::command]
pub fn get_brightness() -> u32 {
    if let Ok(out) = Command::new("gdbus")
        .args([
            "call", "--session",
            "--dest", "org.gnome.SettingsDaemon.Power",
            "--object-path", "/org/gnome/SettingsDaemon/Power",
            "--method", "org.freedesktop.DBus.Properties.Get",
            "org.gnome.SettingsDaemon.Power.Screen", "Brightness",
        ])
        .output()
    {
        let s = String::from_utf8_lossy(&out.stdout);
        if let Some(num_str) = s.split('<').nth(1).and_then(|part| part.split('>').next()) {
            if let Ok(b) = num_str.trim().parse::<u32>() {
                return b.clamp(1, 100);
            }
        }
    }
    if let Ok(entries) = fs::read_dir("/sys/class/backlight") {
        for entry in entries.flatten() {
            let path = entry.path();
            let cur = fs::read_to_string(path.join("brightness"))
                .ok()
                .and_then(|v| v.trim().parse::<u64>().ok());
            let max = fs::read_to_string(path.join("max_brightness"))
                .ok()
                .and_then(|v| v.trim().parse::<u64>().ok());
            if let (Some(c), Some(m)) = (cur, max) {
                if m > 0 {
                    return ((c * 100) / m).clamp(1, 100) as u32;
                }
            }
        }
    }
    100
}

#[tauri::command]
pub async fn get_wifi_status() -> Result<WifiStatus, String> {
    let ssid = crate::wifi::connected_ssid();
    Ok(WifiStatus {
        enabled: true,
        connected: ssid.is_some(),
        ssid,
    })
}

#[tauri::command]
pub async fn set_wifi_state(enabled: bool) -> Result<(), String> {
    let action = if enabled { "on" } else { "off" };
    let _ = Command::new("nmcli").args(["radio", "wifi", action]).status();
    Ok(())
}

#[tauri::command]
pub async fn get_bluetooth_state() -> Result<bool, String> {
    Ok(true)
}

#[tauri::command]
pub async fn set_bluetooth_state(_enabled: bool) -> Result<(), String> {
    Ok(())
}

#[tauri::command]
pub fn open_bluetooth_settings() {
    let _ = Command::new("gnome-control-center").arg("bluetooth").spawn();
}

#[tauri::command]
pub fn open_airplane_mode_settings() {
    let _ = Command::new("gnome-control-center").arg("network").spawn();
}

#[tauri::command]
pub fn set_brightness(_app: AppHandle, brightness: u32) {
    let b = brightness.clamp(1, 100);
    let _ = Command::new("gdbus")
        .args([
            "call", "--session",
            "--dest", "org.gnome.SettingsDaemon.Power",
            "--object-path", "/org/gnome/SettingsDaemon/Power",
            "--method", "org.freedesktop.DBus.Properties.Set",
            "org.gnome.SettingsDaemon.Power.Screen", "Brightness",
            &format!("<int32 {}>", b),
        ])
        .spawn();
}

#[tauri::command]
pub async fn get_battery_saver_state() -> Result<bool, String> {
    Ok(false)
}

#[tauri::command]
pub fn open_battery_saver_settings() {
    let _ = Command::new("gnome-control-center").arg("power").spawn();
}

static PREV_CPU: std::sync::Mutex<Option<(u64, u64)>> = std::sync::Mutex::new(None);

#[tauri::command]
pub fn get_cpu_usage() -> Result<u32, String> {
    if let Ok(content) = fs::read_to_string("/proc/stat") {
        if let Some(line) = content.lines().next() {
            let fields: Vec<u64> = line
                .split_whitespace()
                .skip(1)
                .filter_map(|s| s.parse().ok())
                .collect();
            if fields.len() >= 4 {
                let idle = fields[3];
                let total: u64 = fields.iter().sum();
                if let Ok(mut prev_lock) = PREV_CPU.lock() {
                    if let Some((prev_total, prev_idle)) = *prev_lock {
                        let total_delta = total.saturating_sub(prev_total);
                        let idle_delta = idle.saturating_sub(prev_idle);
                        *prev_lock = Some((total, idle));
                        if total_delta > 0 {
                            let usage = ((total_delta.saturating_sub(idle_delta)) * 100 / total_delta) as u32;
                            return Ok(usage.min(100));
                        }
                    } else {
                        *prev_lock = Some((total, idle));
                    }
                }
            }
        }
    }
    Ok(15)
}

#[tauri::command]
pub fn get_ram_usage() -> Result<f32, String> {
    // Read from /proc/meminfo
    if let Ok(content) = fs::read_to_string("/proc/meminfo") {
        let mut total = 0u64;
        let mut available = 0u64;
        for line in content.lines() {
            if line.starts_with("MemTotal:") {
                total = line
                    .split_whitespace()
                    .nth(1)
                    .and_then(|s| s.parse().ok())
                    .unwrap_or(0);
            } else if line.starts_with("MemAvailable:") {
                available = line
                    .split_whitespace()
                    .nth(1)
                    .and_then(|s| s.parse().ok())
                    .unwrap_or(0);
            }
        }
        if total > 0 {
            let used = total.saturating_sub(available);
            return Ok((used as f32 / total as f32) * 100.0);
        }
    }
    Ok(35.0)
}

#[tauri::command]
pub fn get_ram_details() -> Result<crate::types::RamDetails, String> {
    if let Ok(content) = fs::read_to_string("/proc/meminfo") {
        let mut total = 0u64;
        let mut available = 0u64;
        for line in content.lines() {
            if line.starts_with("MemTotal:") {
                total = line
                    .split_whitespace()
                    .nth(1)
                    .and_then(|s| s.parse().ok())
                    .unwrap_or(0);
            } else if line.starts_with("MemAvailable:") {
                available = line
                    .split_whitespace()
                    .nth(1)
                    .and_then(|s| s.parse().ok())
                    .unwrap_or(0);
            }
        }
        if total > 0 {
            let used = total.saturating_sub(available);
            let percent = (used as f32 / total as f32) * 100.0;
            return Ok(crate::types::RamDetails {
                percent,
                total_mb: total / 1024,
                used_mb: used / 1024,
                free_mb: available / 1024,
            });
        }
    }
    Ok(crate::types::RamDetails {
        percent: 35.0,
        total_mb: 16384,
        used_mb: 5734,
        free_mb: 10650,
    })
}

#[tauri::command]
pub fn get_system_overview() -> Result<crate::types::SystemOverview, String> {
    let cpu_percent = get_cpu_usage().unwrap_or(0);
    let ram = get_ram_details()?;
    let disk_free_gb = get_disk_space().unwrap_or(0) / (1024 * 1024 * 1024);
    let (net_out_bps, net_in_bps) = get_network_speed().unwrap_or((0, 0));

    Ok(crate::types::SystemOverview {
        cpu_percent,
        ram,
        disk_free_gb,
        disk_total_gb: 512,
        net_in_bps,
        net_out_bps,
    })
}

#[tauri::command]
pub fn get_disk_space() -> Result<u64, String> {
    unsafe {
        let mut stat: libc::statvfs = std::mem::zeroed();
        let path = std::ffi::CString::new("/").unwrap();
        if libc::statvfs(path.as_ptr(), &mut stat) == 0 {
            let free_bytes = (stat.f_bavail as u64) * (stat.f_frsize as u64);
            return Ok(free_bytes);
        }
    }
    Ok(50 * 1024 * 1024 * 1024)
}

static PREV_NET: std::sync::Mutex<Option<(u64, u64, std::time::Instant)>> = std::sync::Mutex::new(None);

#[tauri::command]
pub fn get_network_speed() -> Result<(u64, u64), String> {
    if let Ok(content) = fs::read_to_string("/proc/net/dev") {
        let mut rx_total = 0u64;
        let mut tx_total = 0u64;
        for line in content.lines().skip(2) {
            let mut parts = line.split_whitespace();
            if let Some(iface) = parts.next() {
                if iface.starts_with("lo:") {
                    continue;
                }
                if let Some(rx_bytes) = parts.next().and_then(|s| s.parse::<u64>().ok()) {
                    rx_total += rx_bytes;
                }
                let tx_bytes = parts.nth(7).and_then(|s| s.parse::<u64>().ok());
                if let Some(tx) = tx_bytes {
                    tx_total += tx;
                }
            }
        }
        let now = std::time::Instant::now();
        if let Ok(mut prev_lock) = PREV_NET.lock() {
            if let Some((prev_rx, prev_tx, prev_time)) = *prev_lock {
                let elapsed = now.duration_since(prev_time).as_secs_f64();
                *prev_lock = Some((rx_total, tx_total, now));
                if elapsed > 0.05 {
                    let rx_speed = ((rx_total.saturating_sub(prev_rx)) as f64 / elapsed) as u64;
                    let tx_speed = ((tx_total.saturating_sub(prev_tx)) as f64 / elapsed) as u64;
                    return Ok((rx_speed, tx_speed));
                }
            } else {
                *prev_lock = Some((rx_total, tx_total, now));
            }
        }
    }
    Ok((0, 0))
}

#[tauri::command]
pub fn get_windows_accent_color() -> Option<String> {
    get_system_accent_color().ok()
}

#[tauri::command]
pub fn get_system_accent_color() -> Result<String, String> {
    // Try querying GNOME desktop accent color
    let output = Command::new("gsettings")
        .args(["get", "org.gnome.desktop.interface", "accent-color"])
        .output();
    if let Ok(out) = output {
        if out.status.success() {
            let color_name = String::from_utf8_lossy(&out.stdout)
                .trim()
                .trim_matches('\'')
                .to_lowercase();
            let hex = match color_name.as_str() {
                "blue" => "#3584e4",
                "teal" => "#2190a4",
                "green" => "#3a944a",
                "yellow" => "#e5a50a",
                "orange" => "#e66100",
                "red" => "#e01b24",
                "pink" => "#d56199",
                "purple" => "#9141ac",
                "slate" => "#5e5c64",
                _ => "#3584e4",
            };
            return Ok(hex.to_string());
        }
    }
    Ok("#3584e4".to_string())
}

#[tauri::command]
pub async fn export_settings_to_file(app: AppHandle, path: String) -> Result<(), String> {
    let settings = load_settings(app)?;
    let content = serde_json::to_string_pretty(&settings).map_err(|e| e.to_string())?;
    fs::write(path, content).map_err(|e| e.to_string())?;
    Ok(())
}

#[tauri::command]
pub async fn import_settings_from_file(app: AppHandle, path: String) -> Result<(), String> {
    let content = fs::read_to_string(path).map_err(|e| e.to_string())?;
    let settings: HashMap<String, serde_json::Value> =
        serde_json::from_str(&content).map_err(|e| e.to_string())?;
    let config_path = app
        .path()
        .app_config_dir()
        .map_err(|e| e.to_string())?
        .join("settings.json");
    crate::utils::write_settings_file(&config_path, &settings)?;
    crate::utils::replace_settings_cache(settings);
    Ok(())
}

#[tauri::command]
pub fn reset_settings(app: AppHandle) -> Result<(), String> {
    let config_path = app
        .path()
        .app_config_dir()
        .map_err(|e| e.to_string())?
        .join("settings.json");
    let _ = fs::remove_file(config_path);
    crate::utils::replace_settings_cache(HashMap::new());
    Ok(())
}

#[tauri::command]
pub async fn get_tray_apps() -> Vec<crate::tray::TrayApp> {
    crate::tray::enumerate()
}

#[tauri::command]
pub async fn show_tray_context_menu(tray_id: String) -> Result<(), String> {
    crate::tray::interact(&tray_id, true)
}

#[tauri::command]
pub async fn open_system_action(action: SystemAction) -> Result<(), String> {
    match action {
        SystemAction::Settings | SystemAction::TaskbarSettings | SystemAction::InstalledApps | SystemAction::PowerOptions | SystemAction::NetworkConnections => {
            let _ = Command::new("gnome-control-center").spawn();
        }
        SystemAction::TaskManager => {
            let _ = Command::new("gnome-system-monitor").spawn();
        }
        SystemAction::FileExplorer => {
            let _ = Command::new("nautilus").spawn();
        }
        SystemAction::LockScreen => {
            let _ = Command::new("xdg-screensaver").arg("lock").spawn().or_else(|_| {
                Command::new("gnome-screensaver-command").arg("-l").spawn()
            });
        }
        _ => {}
    }
    Ok(())
}

#[tauri::command]
pub async fn activate_tray_icon(tray_id: String) -> Result<(), String> {
    crate::tray::interact(&tray_id, false)
}

#[tauri::command]
pub fn request_notch_keyboard_focus(_app_handle: AppHandle, _focus: bool) -> Result<(), String> {
    Ok(())
}

#[tauri::command]
pub fn get_clipboard_history() -> Result<Vec<crate::types::ClipboardItem>, String> {
    Ok(vec![])
}

#[tauri::command]
pub fn clear_clipboard_history() -> Result<(), String> {
    Ok(())
}


