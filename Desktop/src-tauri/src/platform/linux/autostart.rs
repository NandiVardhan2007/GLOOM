//! Linux XDG Autostart implementation for GLOOM.

use std::fs;
use std::path::PathBuf;
use tauri::AppHandle;

fn autostart_file_path() -> Option<PathBuf> {
    let home = std::env::var_os("HOME")?;
    let path = PathBuf::from(home).join(".config").join("autostart").join("gloom.desktop");
    Some(path)
}

pub fn disable_all_autostart(_app: &AppHandle) {
    if let Some(path) = autostart_file_path() {
        if path.exists() {
            let _ = fs::remove_file(path);
        }
    }
}

pub fn enable_autostart(_app: &AppHandle) -> Result<(), String> {
    let path = autostart_file_path().ok_or("Cannot determine HOME directory")?;
    if let Some(parent) = path.parent() {
        let _ = fs::create_dir_all(parent);
    }
    let current_exe = std::env::current_exe().map_err(|e| e.to_string())?;
    let desktop_content = format!(
        "[Desktop Entry]\nType=Application\nName=GLOOM\nComment=GLOOM Dynamic Island & Notch\nExec={}\nTerminal=false\nX-GNOME-Autostart-enabled=true\n",
        current_exe.to_string_lossy()
    );
    fs::write(path, desktop_content).map_err(|e| e.to_string())?;
    Ok(())
}

pub fn is_autostart_enabled(app: &AppHandle) -> bool {
    if let Some(val) = crate::utils::get_setting_str(app, "gloom-autostart") {
        if val == "false" {
            return false;
        } else if val == "true" {
            return true;
        }
    }
    autostart_file_path().map(|p| p.exists()).unwrap_or(false)
}
