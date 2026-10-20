use serde::Serialize;
use tauri::{AppHandle, WebviewWindow};

pub const STRIP_HEIGHT: f64 = 650.0;

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct MonitorDetails {
    pub name: String,
    pub x: i32,
    pub y: i32,
    pub width: u32,
    pub height: u32,
    pub scale_factor: f64,
    pub is_primary: bool,
}

pub fn list_monitors(app: &AppHandle) -> Vec<MonitorDetails> {
    let mut details = Vec::new();
    let primary = app.primary_monitor().ok().flatten().and_then(|m| m.name().cloned());

    if let Ok(monitors) = app.available_monitors() {
        for m in monitors {
            let name = m.name().cloned().unwrap_or_else(|| "Display".to_string());
            let is_primary = primary.as_ref() == Some(&name);
            let pos = m.position();
            let size = m.size();
            details.push(MonitorDetails {
                name,
                x: pos.x,
                y: pos.y,
                width: size.width,
                height: size.height,
                scale_factor: m.scale_factor(),
                is_primary,
            });
        }
    }
    details
}

pub fn position_overlay(win: &WebviewWindow) {
    let monitor = win.primary_monitor().ok().flatten()
        .or_else(|| win.current_monitor().ok().flatten());

    if let Some(m) = monitor {
        let pos = m.position();
        let size = m.size();
        let scale = m.scale_factor();

        // Calculate logical width from physical width
        let logical_width = (size.width as f64) / scale;
        let logical_height = STRIP_HEIGHT;

        // Position at monitor top-left in physical coords
        let _ = win.set_size(tauri::Size::Logical(tauri::LogicalSize {
            width: logical_width,
            height: logical_height,
        }));

        let _ = win.set_position(tauri::Position::Physical(tauri::PhysicalPosition {
            x: pos.x,
            y: pos.y,
        }));
    }
}
