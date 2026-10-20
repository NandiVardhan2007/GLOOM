use tauri::menu::{Menu, MenuItem};
use tauri::tray::{TrayIconBuilder, TrayIconEvent};
use tauri::{AppHandle, Emitter, Manager};
use crate::window::ISLAND_LABEL;

pub fn setup_tray(app: &AppHandle) -> Result<(), Box<dyn std::error::Error>> {
    let show_hide = MenuItem::with_id(app, "toggle_visible", "Show / Hide Island", true, None::<&str>)?;
    let toggle_mode = MenuItem::with_id(app, "toggle_mode", "Toggle Pill / Notch Mode", true, None::<&str>)?;
    let open_settings = MenuItem::with_id(app, "open_settings", "Settings...", true, None::<&str>)?;
    let recenter = MenuItem::with_id(app, "recenter", "Recenter to Display", true, None::<&str>)?;
    let quit = MenuItem::with_id(app, "quit", "Exit JARVIS", true, None::<&str>)?;

    let menu = Menu::with_items(app, &[&show_hide, &toggle_mode, &open_settings, &recenter, &quit])?;

    let _tray = TrayIconBuilder::new()
        .menu(&menu)
        .tooltip("JARVIS - Dynamic Island")
        .icon(app.default_window_icon().unwrap().clone())
        .on_menu_event(|app, event| {
            match event.id.as_ref() {
                "toggle_visible" => {
                    if let Some(win) = app.get_webview_window(ISLAND_LABEL) {
                        if let Ok(visible) = win.is_visible() {
                            if visible {
                                let _ = win.hide();
                            } else {
                                let _ = win.show();
                            }
                        }
                    }
                }
                "toggle_mode" => {
                    let _ = app.emit("jarvis://toggle-mode", ());
                }
                "open_settings" => {
                    let _ = app.emit("jarvis://open-settings", ());
                }
                "recenter" => {
                    if let Some(win) = app.get_webview_window(ISLAND_LABEL) {
                        crate::display::position_overlay(&win);
                    }
                }
                "quit" => {
                    app.exit(0);
                }
                _ => {}
            }
        })
        .on_tray_icon_event(|tray, event| {
            if let TrayIconEvent::Click { button, .. } = event {
                if button == tauri::tray::MouseButton::Left {
                    let app = tray.app_handle();
                    if let Some(win) = app.get_webview_window(ISLAND_LABEL) {
                        if let Ok(visible) = win.is_visible() {
                            if visible {
                                let _ = win.hide();
                            } else {
                                let _ = win.show();
                            }
                        }
                    }
                }
            }
        })
        .build(app)?;

    Ok(())
}
