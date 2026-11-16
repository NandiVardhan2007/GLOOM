//! Linux Dock Extras implementation for GLOOM.

use serde::Serialize;
use std::fs;
use std::path::PathBuf;
use std::process::Command;
use tauri::AppHandle;

#[derive(Clone, Serialize)]
pub struct DockExtra {
    pub id: String,
    pub kind: String,
    pub name: String,
    pub path: String,
    pub icon: Option<String>,
    pub removable: bool,
}

#[tauri::command]
pub async fn get_dock_extras(_app: AppHandle) -> Vec<DockExtra> {
    let mut extras = Vec::new();
    let home = std::env::var("HOME").unwrap_or_else(|_| "/home".to_string());
    let home_path = PathBuf::from(&home);

    let standard_dirs = [
        ("downloads", "Downloads", home_path.join("Downloads")),
        ("documents", "Documents", home_path.join("Documents")),
        ("pictures", "Pictures", home_path.join("Pictures")),
        ("music", "Music", home_path.join("Music")),
        ("videos", "Videos", home_path.join("Videos")),
        ("trash", "Trash", home_path.join(".local/share/Trash/files")),
    ];

    for (id, name, path) in standard_dirs {
        extras.push(DockExtra {
            id: id.to_string(),
            kind: "folder".to_string(),
            name: name.to_string(),
            path: path.to_string_lossy().to_string(),
            icon: None,
            removable: false,
        });
    }

    extras
}

#[tauri::command]
pub async fn get_dock_drives() -> Vec<DockExtra> {
    let mut drives = Vec::new();
    let user = std::env::var("USER").unwrap_or_default();
    let media_dir = PathBuf::from(format!("/media/{}", user));

    if let Ok(entries) = fs::read_dir(media_dir) {
        for entry in entries.flatten() {
            let name = entry.file_name().to_string_lossy().to_string();
            let path = entry.path().to_string_lossy().to_string();
            drives.push(DockExtra {
                id: format!("drive_{}", name),
                kind: "drive".to_string(),
                name,
                path,
                icon: None,
                removable: true,
            });
        }
    }

    drives
}

#[tauri::command]
pub async fn open_dock_extra(_kind: String, path: String) -> Result<(), String> {
    Command::new("xdg-open")
        .arg(&path)
        .spawn()
        .map_err(|e| e.to_string())?;
    Ok(())
}

#[tauri::command]
pub async fn eject_dock_extra(path: String) -> Result<(), String> {
    let status = Command::new("udisksctl")
        .args(["unmount", "-p", &path])
        .status()
        .or_else(|_| Command::new("umount").arg(&path).status())
        .map_err(|e| e.to_string())?;

    if status.success() {
        Ok(())
    } else {
        Err("Failed to unmount drive".to_string())
    }
}
