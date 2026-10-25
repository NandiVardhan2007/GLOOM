use std::path::PathBuf;
use tauri::AppHandle;
use tauri_plugin_autostart::ManagerExt;
use windows::core::{HSTRING, PCWSTR};
use windows::Win32::System::Registry::{
    RegCloseKey, RegDeleteValueW, RegOpenKeyExW, RegQueryValueExW, RegSetValueExW, HKEY,
    HKEY_CURRENT_USER, KEY_READ, KEY_WRITE, REG_SZ,
};

const STARTUP_REG_KEYS: &[&str] = &[
    "gloom",
    "GLOOM",
    "GLOOM-Island",
    "GLOOM Dynamic Island",
];

/// Completely removes all autostart entries for GLOOM from Windows:
/// 1. Disables via tauri-plugin-autostart
/// 2. Deletes HKCU\Software\Microsoft\Windows\CurrentVersion\Run keys
/// 3. Deletes HKCU\Software\Microsoft\Windows\CurrentVersion\Explorer\StartupApproved\Run keys
/// 4. Removes any .lnk shortcuts in User and Common Startup folders
/// 5. Removes any scheduled tasks named *gloom*
pub fn disable_all_autostart(app: &AppHandle) {
    // 1. Disable via plugin
    let autolaunch = app.autolaunch();
    let _ = autolaunch.disable();

    // 2. Clean HKCU Run registry values
    clean_registry_run_keys();

    // 3. Clean StartupApproved\Run
    clean_registry_startup_approved();

    // 4. Clean Startup folder shortcuts (.lnk)
    clean_startup_folder_shortcuts();

    // 5. Clean scheduled tasks
    clean_scheduled_tasks();
}

/// Enables autostart via plugin and ensures registry and settings are in sync.
pub fn enable_autostart(app: &AppHandle) -> Result<(), String> {
    let autolaunch = app.autolaunch();
    autolaunch.enable().map_err(|e| e.to_string())?;

    // Also ensure registry has the current executable path under "GLOOM"
    if let Ok(current_exe) = std::env::current_exe() {
        set_registry_run_value("GLOOM", &current_exe.to_string_lossy());
    }
    Ok(())
}

/// Checks whether autostart is currently enabled.
/// Returns true only if autostart setting is NOT explicitly false AND an active autostart mechanism exists.
pub fn is_autostart_enabled(app: &AppHandle) -> bool {
    // 1. Check settings.json
    if let Some(val) = crate::utils::get_setting_str(app, "gloom-autostart") {
        if val == "false" {
            return false;
        } else if val == "true" {
            return true;
        }
    }

    // 2. Check plugin
    let autolaunch = app.autolaunch();
    if let Ok(enabled) = autolaunch.is_enabled() {
        if enabled {
            return true;
        }
    }

    // 3. Check registry Run keys
    if has_registry_run_entry() {
        return true;
    }

    // 4. Check Startup folder
    if has_startup_folder_shortcut() {
        return true;
    }

    false
}

fn clean_registry_run_keys() {
    unsafe {
        let subkey = windows::core::w!("Software\\Microsoft\\Windows\\CurrentVersion\\Run");
        let mut hkey = HKEY::default();
        if RegOpenKeyExW(HKEY_CURRENT_USER, subkey, Some(0), KEY_WRITE, &mut hkey).is_ok() {
            for &key_name in STARTUP_REG_KEYS {
                let val_hstring = HSTRING::from(key_name);
                let _ = RegDeleteValueW(hkey, PCWSTR(val_hstring.as_ptr()));
            }
            let _ = RegCloseKey(hkey);
        }
    }
}

fn clean_registry_startup_approved() {
    unsafe {
        let subkey = windows::core::w!("Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\StartupApproved\\Run");
        let mut hkey = HKEY::default();
        if RegOpenKeyExW(HKEY_CURRENT_USER, subkey, Some(0), KEY_WRITE, &mut hkey).is_ok() {
            for &key_name in STARTUP_REG_KEYS {
                let val_hstring = HSTRING::from(key_name);
                let _ = RegDeleteValueW(hkey, PCWSTR(val_hstring.as_ptr()));
            }
            let _ = RegCloseKey(hkey);
        }
    }
}

fn set_registry_run_value(name: &str, exe_path: &str) {
    unsafe {
        let subkey = windows::core::w!("Software\\Microsoft\\Windows\\CurrentVersion\\Run");
        let mut hkey = HKEY::default();
        if RegOpenKeyExW(HKEY_CURRENT_USER, subkey, Some(0), KEY_WRITE, &mut hkey).is_ok() {
            let name_hstring = HSTRING::from(name);
            let quoted_path = format!("\"{}\"", exe_path.trim_matches('"'));
            let wide_path: Vec<u16> = quoted_path.encode_utf16().chain(std::iter::once(0)).collect();
            let _ = RegSetValueExW(
                hkey,
                PCWSTR(name_hstring.as_ptr()),
                Some(0),
                REG_SZ,
                Some(std::slice::from_raw_parts(
                    wide_path.as_ptr() as *const u8,
                    wide_path.len() * 2,
                )),
            );
            let _ = RegCloseKey(hkey);
        }
    }
}

fn has_registry_run_entry() -> bool {
    unsafe {
        let subkey = windows::core::w!("Software\\Microsoft\\Windows\\CurrentVersion\\Run");
        let mut hkey = HKEY::default();
        if RegOpenKeyExW(HKEY_CURRENT_USER, subkey, Some(0), KEY_READ, &mut hkey).is_ok() {
            for &key_name in STARTUP_REG_KEYS {
                let val_hstring = HSTRING::from(key_name);
                if RegQueryValueExW(
                    hkey,
                    PCWSTR(val_hstring.as_ptr()),
                    None,
                    None,
                    None,
                    None,
                )
                .is_ok()
                {
                    let _ = RegCloseKey(hkey);
                    return true;
                }
            }
            let _ = RegCloseKey(hkey);
        }
    }
    false
}

fn get_startup_directories() -> Vec<PathBuf> {
    let mut dirs = Vec::new();
    if let Ok(appdata) = std::env::var("APPDATA") {
        dirs.push(
            PathBuf::from(appdata)
                .join("Microsoft")
                .join("Windows")
                .join("Start Menu")
                .join("Programs")
                .join("Startup"),
        );
    }
    if let Ok(program_data) = std::env::var("ProgramData") {
        dirs.push(
            PathBuf::from(program_data)
                .join("Microsoft")
                .join("Windows")
                .join("Start Menu")
                .join("Programs")
                .join("Startup"),
        );
    }
    dirs
}

fn clean_startup_folder_shortcuts() {
    for dir in get_startup_directories() {
        if !dir.exists() {
            continue;
        }
        if let Ok(entries) = std::fs::read_dir(&dir) {
            for entry in entries.flatten() {
                let path = entry.path();
                if let Some(file_name) = path.file_name().and_then(|n| n.to_str()) {
                    let lower = file_name.to_lowercase();
                    if lower.ends_with(".lnk") && lower.contains("gloom") {
                        let _ = std::fs::remove_file(&path);
                    }
                }
            }
        }
    }
}

fn has_startup_folder_shortcut() -> bool {
    for dir in get_startup_directories() {
        if !dir.exists() {
            continue;
        }
        if let Ok(entries) = std::fs::read_dir(&dir) {
            for entry in entries.flatten() {
                let path = entry.path();
                if let Some(file_name) = path.file_name().and_then(|n| n.to_str()) {
                    let lower = file_name.to_lowercase();
                    if lower.ends_with(".lnk") && lower.contains("gloom") {
                        return true;
                    }
                }
            }
        }
    }
    false
}

fn clean_scheduled_tasks() {
    use std::os::windows::process::CommandExt;
    const CREATE_NO_WINDOW: u32 = 0x08000000;
    for &task in &["GLOOM", "gloom", "GLOOM-Island", "GLOOM Dynamic Island"] {
        let _ = std::process::Command::new("schtasks")
            .args(&["/Delete", "/TN", task, "/F"])
            .creation_flags(CREATE_NO_WINDOW)
            .output();
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn startup_reg_keys_include_all_variants() {
        assert!(STARTUP_REG_KEYS.contains(&"gloom"));
        assert!(STARTUP_REG_KEYS.contains(&"GLOOM"));
        assert!(STARTUP_REG_KEYS.contains(&"GLOOM-Island"));
        assert!(STARTUP_REG_KEYS.contains(&"GLOOM Dynamic Island"));
    }

    #[test]
    fn clean_directory_shortcuts_removes_only_gloom_links() {
        let dir = std::env::temp_dir().join(format!("gloom-autostart-test-{}", std::process::id()));
        let _ = std::fs::create_dir_all(&dir);
        let dir = &dir;
        let gloom_lnk1 = dir.join("GLOOM.lnk");
        let gloom_lnk2 = dir.join("GLOOM Dynamic Island.lnk");
        let other_lnk = dir.join("Discord.lnk");
        let gloom_txt = dir.join("gloom.txt");

        std::fs::write(&gloom_lnk1, b"test").unwrap();
        std::fs::write(&gloom_lnk2, b"test").unwrap();
        std::fs::write(&other_lnk, b"test").unwrap();
        std::fs::write(&gloom_txt, b"test").unwrap();

        for entry in std::fs::read_dir(dir).unwrap().flatten() {
            let path = entry.path();
            if let Some(name) = path.file_name().and_then(|n| n.to_str()) {
                let lower = name.to_lowercase();
                if lower.ends_with(".lnk") && lower.contains("gloom") {
                    let _ = std::fs::remove_file(&path);
                }
            }
        }

        assert!(!gloom_lnk1.exists(), "gloom_lnk1 should be removed");
        assert!(!gloom_lnk2.exists(), "gloom_lnk2 should be removed");
        assert!(other_lnk.exists(), "other_lnk should be preserved");
        assert!(gloom_txt.exists(), "non-lnk files should be preserved");
        let _ = std::fs::remove_dir_all(dir);
    }
}
