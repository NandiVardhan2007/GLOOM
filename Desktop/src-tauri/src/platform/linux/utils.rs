//! Linux utility functions for GLOOM.

use std::collections::HashMap;
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicU64, Ordering};
use tauri::{AppHandle, Manager, WebviewWindow};

static TASKBAR_MARKER: std::sync::OnceLock<PathBuf> = std::sync::OnceLock::new();

pub fn init_taskbar_marker(app: &AppHandle) {
    if let Ok(dir) = app.path().app_config_dir() {
        let _ = TASKBAR_MARKER.set(dir.join("taskbar_hidden.flag"));
    }
}

pub fn taskbar_marker_exists() -> bool {
    TASKBAR_MARKER.get().map(|p| p.exists()).unwrap_or(false)
}

pub fn restore_taskbar_after_crash() {
    if let Some(marker) = TASKBAR_MARKER.get() {
        let _ = std::fs::remove_file(marker);
    }
}

pub fn run_taskbar_watchdog(_parent_pid: u32, _marker: PathBuf) {}

pub fn set_taskbar_visibility(_visible: bool, _always_on_top: bool) -> bool {
    true
}

pub fn set_native_osd_suppressed(_suppressed: bool) {}

pub fn get_best_monitor(app: &AppHandle, win: &WebviewWindow) -> Option<tauri::Monitor> {
    win.primary_monitor().ok().flatten()
        .or_else(|| win.current_monitor().ok().flatten())
        .or_else(|| app.primary_monitor().ok().flatten())
        .or_else(|| app.available_monitors().ok().and_then(|mut m| m.pop()))
}

pub fn reconcile_main_appbar(app: &AppHandle) {
    if let Some(main_win) = app.get_webview_window("main") {
        if let Some(monitor) = get_best_monitor(app, &main_win) {
            let m_pos = monitor.position();
            let m_size = monitor.size();
            let scale = monitor.scale_factor();
            let gloom_scale = get_gloom_scale(app);
            let target_height = (420.0 * gloom_scale * scale) as u32;
            let _ = main_win.set_position(tauri::PhysicalPosition::new(m_pos.x, m_pos.y));
            let _ = main_win.set_size(tauri::PhysicalSize::new(m_size.width, target_height));
        }
        if let Ok(gtk_win) = main_win.gtk_window() {
            use gtk::prelude::*;
            gtk_win.set_keep_above(true);
            gtk_win.set_skip_taskbar_hint(true);
            gtk_win.set_skip_pager_hint(true);
            gtk_win.stick();
        }
        let _ = main_win.show();
        let _ = main_win.set_always_on_top(true);
    }
}

pub fn register_dock_appbar(_window: WebviewWindow) {}

pub fn set_click_through(_hwnd: isize, _ignore: bool) -> bool {
    true
}

pub fn read_settings_file(
    path: &Path,
) -> Result<HashMap<String, serde_json::Value>, String> {
    match std::fs::read_to_string(path) {
        Ok(content) => serde_json::from_str(content.trim_start_matches('\u{feff}'))
            .map_err(|e| format!("Invalid settings file: {e}")),
        Err(e) if e.kind() == std::io::ErrorKind::NotFound => Ok(Default::default()),
        Err(e) => Err(format!("Could not read settings file: {e}")),
    }
}

pub fn atomic_write_file(path: &Path, content: &[u8]) -> Result<(), String> {
    static NEXT_TEMP: AtomicU64 = AtomicU64::new(0);
    let temp = path.with_file_name(format!(
        "{}.tmp-{}-{}",
        path.file_name()
            .and_then(|name| name.to_str())
            .unwrap_or("gloom"),
        std::process::id(),
        NEXT_TEMP.fetch_add(1, Ordering::Relaxed)
    ));
    if let Err(e) = std::fs::write(&temp, content) {
        let _ = std::fs::remove_file(&temp);
        return Err(format!("Could not write {}: {e}", temp.display()));
    }
    if let Err(e) = std::fs::rename(&temp, path) {
        let _ = std::fs::remove_file(&temp);
        return Err(format!("Could not replace {}: {e}", path.display()));
    }
    Ok(())
}

pub fn write_settings_file(
    path: &Path,
    settings: &HashMap<String, serde_json::Value>,
) -> Result<(), String> {
    let content = serde_json::to_vec(settings).map_err(|e| e.to_string())?;
    atomic_write_file(path, &content)
}

pub fn save_setting_file(
    path: &Path,
    key: String,
    value: serde_json::Value,
) -> Result<HashMap<String, serde_json::Value>, String> {
    let _guard = crate::state::SETTINGS_FILE_LOCK
        .lock()
        .map_err(|e| e.to_string())?;
    let mut settings = read_settings_file(path)?;
    settings.insert(key, value);
    write_settings_file(path, &settings)?;
    replace_settings_cache(settings.clone());
    Ok(settings)
}

pub fn init_settings_cache(app: &AppHandle) {
    use crate::state::SETTINGS_CACHE;
    let cache =
        SETTINGS_CACHE.get_or_init(|| std::sync::Mutex::new(HashMap::new()));
    if let Some(path) = app
        .path()
        .app_config_dir()
        .ok()
        .map(|p| p.join("settings.json"))
    {
        if let Ok(settings) = read_settings_file(&path) {
            if let Ok(mut cache) = cache.lock() {
                *cache = settings;
            }
        }
    }
}

pub fn replace_settings_cache(new_settings: HashMap<String, serde_json::Value>) {
    let cache = crate::state::SETTINGS_CACHE
        .get_or_init(|| std::sync::Mutex::new(HashMap::new()));
    if let Ok(mut cache) = cache.lock() {
        *cache = new_settings;
    }
}

pub fn get_gloom_scale(_app: &AppHandle) -> f64 {
    get_setting_str(_app, "gloom-scale")
        .and_then(|s| s.parse::<f64>().ok())
        .unwrap_or(1.0)
}

pub fn get_setting_str(_app: &AppHandle, key: &str) -> Option<String> {
    let cache = crate::state::SETTINGS_CACHE.get()?;
    let guard = cache.lock().ok()?;
    let val = guard.get(key)?;
    if let Some(s) = val.as_str() {
        Some(s.to_string())
    } else if let Some(b) = val.as_bool() {
        Some(b.to_string())
    } else if let Some(n) = val.as_i64() {
        Some(n.to_string())
    } else if let Some(f) = val.as_f64() {
        Some(f.to_string())
    } else {
        None
    }
}

pub fn dock_setting_enabled(value: Option<&serde_json::Value>) -> bool {
    value.is_none_or(|value| value.as_str() == Some("true"))
}

pub fn dock_enabled() -> bool {
    crate::state::SETTINGS_CACHE.get().is_none_or(|cache| {
        cache
            .lock()
            .map(|settings| dock_setting_enabled(settings.get("gloom-dock-enabled")))
            .unwrap_or(false)
    })
}

pub fn sanitize_window_style(_hwnd: isize) {}

