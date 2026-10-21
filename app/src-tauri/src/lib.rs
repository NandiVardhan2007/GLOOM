pub mod config;
pub mod display;
pub mod fullscreen;
pub mod protocol;
pub mod providers;
pub mod tray;
pub mod updater;
pub mod window;

use config::{AppConfig, ConfigStore};
use display::{list_monitors, position_overlay, MonitorDetails};
use providers::clipboard::{get_clipboard_items, ClipboardItem};
use providers::media::control_media;
use providers::system::{get_telemetry, SystemTelemetry};
use providers::volume::set_master_volume;
use providers::brightness::set_display_brightness;
use serde_json::Value;
use tauri::{AppHandle, Emitter, Manager, State};
use window::{make_overlay, start_pointer_watcher, update_hit_rects, jarvis_focus_window, HitRect, ISLAND_LABEL};

pub fn open_settings_window(app: &AppHandle, tab: Option<String>) -> Result<(), String> {
    let target_tab = tab.unwrap_or_else(|| "general".into());

    if let Some(win) = app.get_webview_window("settings") {
        let _ = win.show();
        let _ = win.unminimize();
        let _ = win.set_focus();
        let _ = win.emit("settings-open-tab", target_tab);
        return Ok(());
    }

    let url = tauri::WebviewUrl::App("index.html".into());

    let win = tauri::WebviewWindowBuilder::new(app, "settings", url)
        .title("JARVIS Settings")
        .inner_size(880.0, 630.0)
        .min_inner_size(740.0, 500.0)
        .center()
        .transparent(false)
        .decorations(false)
        .always_on_top(true)
        .resizable(true)
        .skip_taskbar(false)
        .initialization_script("window.__IS_SETTINGS__ = true; window.name = 'settings';")
        .build()
        .map_err(|e| e.to_string())?;

    let win_clone = win.clone();
    let tab_clone = target_tab.clone();
    std::thread::spawn(move || {
        std::thread::sleep(std::time::Duration::from_millis(350));
        let _ = win_clone.emit("settings-open-tab", tab_clone);
    });

    Ok(())
}

#[tauri::command]
fn jarvis_open_settings_window(app: AppHandle, _tab: Option<String>) -> Result<(), String> {
    let _ = app.emit("jarvis://open-settings", ());
    Ok(())
}

#[tauri::command]
fn jarvis_update_hit_rects(rects: Vec<HitRect>) {
    update_hit_rects(rects);
}

#[tauri::command]
fn jarvis_media_control(action: String, payload: Option<Value>) -> Result<bool, String> {
    control_media(&action, payload)
}

#[tauri::command]
fn jarvis_set_volume(level: f32) -> Result<(), String> {
    set_master_volume(level)
}

#[tauri::command]
fn jarvis_set_brightness(brightness: u32) {
    set_display_brightness(brightness);
}

#[tauri::command]
fn jarvis_get_telemetry() -> SystemTelemetry {
    get_telemetry()
}

#[tauri::command]
fn jarvis_exit(app: AppHandle) {
    app.exit(0);
}

#[tauri::command]
fn jarvis_install_update(
    app: AppHandle,
    download_url: String,
    target_version: Option<String>,
    expected_sha256: Option<String>,
) -> Result<String, String> {
    let msg = updater::stage_and_install_update(
        &download_url,
        target_version.as_deref(),
        expected_sha256.as_deref(),
    )?;

    std::thread::spawn(move || {
        std::thread::sleep(std::time::Duration::from_millis(1000));
        app.exit(0);
    });

    Ok(msg)
}

#[tauri::command]
fn jarvis_send_mobile_control(
    action: String,
    x: Option<f32>,
    y: Option<f32>,
    delta_x: Option<f32>,
    delta_y: Option<f32>,
    target_ip: Option<String>,
) -> Result<(), String> {
    providers::livebridge::send_control_to_device(action, x, y, delta_x, delta_y, target_ip)
}

#[tauri::command]
fn jarvis_get_config(store: State<'_, ConfigStore>) -> AppConfig {
    store.get()
}

#[tauri::command]
fn jarvis_set_config(app: AppHandle, config: AppConfig, store: State<'_, ConfigStore>) {
    store.set(config.clone());

    // Sync autostart plugin state
    use tauri_plugin_autostart::ManagerExt;
    if config.autostart {
        let _ = app.autolaunch().enable();
    } else {
        let _ = app.autolaunch().disable();
    }

    // Reposition island if needed
    if let Some(win) = app.get_webview_window(ISLAND_LABEL) {
        display::position_overlay(&win);
    }

    let _ = app.emit("jarvis://config-updated", config);
}

#[tauri::command]
fn jarvis_close_settings_window(app: AppHandle) {
    if let Some(win) = app.get_webview_window("settings") {
        let _ = win.close();
    }
}

#[tauri::command]
fn jarvis_minimize_settings_window(app: AppHandle) {
    if let Some(win) = app.get_webview_window("settings") {
        let _ = win.minimize();
    }
}

#[tauri::command]
fn jarvis_get_monitors(app: AppHandle) -> Vec<MonitorDetails> {
    list_monitors(&app)
}

#[tauri::command]
fn jarvis_recenter(app: AppHandle) {
    if let Some(win) = app.get_webview_window(ISLAND_LABEL) {
        position_overlay(&win);
    }
}

#[tauri::command]
fn jarvis_get_clipboard() -> Vec<ClipboardItem> {
    get_clipboard_items()
}

#[tauri::command]
fn jarvis_check_fullscreen() -> bool {
    fullscreen::is_foreground_fullscreen()
}

#[tauri::command]
fn jarvis_system_action(app: AppHandle, action: String) -> Result<(), String> {
    match action.as_str() {
        "lock" => {
            let _ = std::process::Command::new("rundll32.exe")
                .args(["user32.dll,LockWorkStation"])
                .spawn();
            Ok(())
        }
        "taskmgr" => {
            let _ = std::process::Command::new("taskmgr.exe").spawn();
            Ok(())
        }
        "settings" => {
            let _ = app.emit("jarvis://open-settings", ());
            Ok(())
        }
        "bluetooth" => {
            // Self-contained in Dynamic Island - do not open external Windows settings
            Ok(())
        }
        "network" => {
            // Self-contained in Dynamic Island
            Ok(())
        }
        "calc" => {
            let _ = std::process::Command::new("calc.exe").spawn();
            Ok(())
        }
        "snip" => {
            let _ = std::process::Command::new("cmd")
                .args(["/c", "start", "ms-screenclip:"])
                .spawn();
            Ok(())
        }
        "sound" => {
            // Self-contained in Dynamic Island
            Ok(())
        }
        _ => Err("Unknown action".into()),
    }
}


#[tauri::command]
fn jarvis_get_bridge_devices() -> Vec<providers::livebridge::BridgeDevice> {
    providers::livebridge::get_paired_devices()
}

#[tauri::command]
fn jarvis_get_bridge_activities() -> Vec<providers::livebridge::BridgeActivity> {
    providers::livebridge::get_activities()
}

#[tauri::command]
fn jarvis_get_bridge_transfers() -> Vec<providers::livebridge::BridgeTransfer> {
    providers::livebridge::get_transfers()
}

#[tauri::command]
fn jarvis_send_bridge_file(app: AppHandle, device_name: Option<String>, file_name: String, file_size: u64) -> Result<(), String> {
    let name = device_name.unwrap_or_else(|| "Motorola Edge 50 Pro".to_string());
    providers::livebridge::beam_file_to_device(app, name, file_name, file_size, "".to_string(), None)
}

#[tauri::command]
fn jarvis_send_bridge_file_bytes(
    app: AppHandle,
    device_name: Option<String>,
    file_name: String,
    file_size: u64,
    data_base64: String,
    target_ip: Option<String>,
) -> Result<(), String> {
    let name = device_name.unwrap_or_else(|| "Motorola Edge 50 Pro".to_string());
    providers::livebridge::beam_file_to_device(app, name, file_name, file_size, data_base64, target_ip)
}

#[tauri::command]
fn jarvis_send_bridge_file_path(
    app: AppHandle,
    file_path: String,
    target_ip: Option<String>,
) -> Result<(), String> {
    providers::livebridge::beam_file_from_path(app, &file_path, target_ip)
}


#[tauri::command]
fn jarvis_get_connection_status() -> protocol::ConnectionStatusPayload {
    providers::livebridge::get_connection_status()
}


#[tauri::command]
fn jarvis_open_transfer_folder() {
    providers::livebridge::open_transfer_folder();
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_autostart::Builder::new().build())
        .plugin(tauri_plugin_global_shortcut::Builder::new().build())
        .manage(ConfigStore::new())
        .invoke_handler(tauri::generate_handler![
            jarvis_update_hit_rects,
            jarvis_media_control,
            jarvis_set_volume,
            jarvis_set_brightness,
            jarvis_get_telemetry,
            jarvis_focus_window,
            jarvis_exit,
            jarvis_get_config,
            jarvis_set_config,
            jarvis_get_monitors,
            jarvis_recenter,
            jarvis_get_clipboard,
            jarvis_check_fullscreen,
            jarvis_system_action,
            jarvis_open_settings_window,
            jarvis_close_settings_window,
            jarvis_minimize_settings_window,
            jarvis_get_bridge_devices,
            jarvis_get_bridge_activities,
            jarvis_get_bridge_transfers,
            jarvis_send_bridge_file,
            jarvis_send_bridge_file_bytes,
            jarvis_send_bridge_file_path,
            jarvis_get_connection_status,
            jarvis_open_transfer_folder,
            jarvis_install_update,
            jarvis_send_mobile_control
        ])
        .setup(|app| {
            let handle = app.handle().clone();

            if let Some(win) = app.get_webview_window(ISLAND_LABEL) {
                // Configure native overlay properties (borderless, transparent, non-activating)
                make_overlay(&win);
                position_overlay(&win);
                let _ = win.show();
            }

            // Setup system tray
            let _ = tray::setup_tray(&handle);

            // Start background watchers
            start_pointer_watcher(handle.clone());
            providers::media::start_media_service(handle.clone());
            providers::system::start_system_service(handle.clone());
            providers::volume::start_volume_service(handle.clone());
            providers::clipboard::start_clipboard_service(handle.clone());
            providers::livebridge::start_livebridge_service(handle.clone());

            // Fullscreen suppression watcher
            let fs_handle = handle.clone();
            std::thread::spawn(move || {
                let mut was_fullscreen = false;
                loop {
                    let is_fs = fullscreen::is_foreground_fullscreen();
                    if is_fs != was_fullscreen {
                        was_fullscreen = is_fs;
                        let _ = fs_handle.emit("jarvis://fullscreen-changed", is_fs);
                    }
                    std::thread::sleep(std::time::Duration::from_millis(800));
                }
            });

            // Multi-monitor hotplug & resolution change watcher
            let display_handle = handle.clone();
            std::thread::spawn(move || {
                let mut last_monitor_count = 0;
                loop {
                    std::thread::sleep(std::time::Duration::from_secs(3));
                    if let Some(win) = display_handle.get_webview_window(ISLAND_LABEL) {
                        let count = win.available_monitors().map(|m| m.len()).unwrap_or(0);
                        if count != last_monitor_count {
                            last_monitor_count = count;
                            display::position_overlay(&win);
                        }
                    }
                }
            });

            Ok(())
        })
        .build(tauri::generate_context!())
        .expect("error while building JARVIS application")
        .run(|_app_handle, event| {
            if let tauri::RunEvent::ExitRequested { api, .. } = event {
                api.prevent_exit();
            }
        });
}
