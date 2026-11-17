//! Linux Services implementation for GLOOM.
//! Provides system stats via /proc, volume control via PipeWire/ALSA,
//! and media control via MPRIS DBus / playerctl.

use std::fs;
use std::process::Command;
use std::sync::atomic::Ordering;
use std::sync::mpsc::{channel, Sender};
use std::time::Duration;
use tauri::{AppHandle, Emitter, Manager};

use crate::state::SHUTTING_DOWN;
use crate::types::{AudioVisualizationData, SystemCommand};

#[derive(serde::Serialize, Clone)]
pub struct KeyLockPayload {
    pub key: String,
    pub enabled: bool,
}

#[derive(serde::Serialize, serde::Deserialize, Clone, Debug, PartialEq, Eq)]
pub struct FullscreenGamePayload {
    pub is_fullscreen: bool,
    pub reason: String,
}

pub fn setup_keyboard_hook(_app: AppHandle) {}

pub fn setup_key_lock_monitor(app_handle: AppHandle) {
    std::thread::spawn(move || {
        let mut last_caps = false;
        let mut last_num = false;

        while !SHUTTING_DOWN.load(Ordering::Relaxed) {
            // Check Caps Lock state via /sys/class/leds or xset
            let caps = fs::read_to_string("/sys/class/leds/input1::capslock/brightness")
                .or_else(|_| fs::read_to_string("/sys/class/leds/input0::capslock/brightness"))
                .ok()
                .and_then(|v| v.trim().parse::<u32>().ok())
                .map(|b| b > 0)
                .unwrap_or(false);

            if caps != last_caps {
                last_caps = caps;
                let _ = app_handle.emit(
                    "key-lock-changed",
                    KeyLockPayload {
                        key: "Caps Lock".to_string(),
                        enabled: caps,
                    },
                );
            }

            let num = fs::read_to_string("/sys/class/leds/input1::numlock/brightness")
                .or_else(|_| fs::read_to_string("/sys/class/leds/input0::numlock/brightness"))
                .ok()
                .and_then(|v| v.trim().parse::<u32>().ok())
                .map(|b| b > 0)
                .unwrap_or(false);

            if num != last_num {
                last_num = num;
                let _ = app_handle.emit(
                    "key-lock-changed",
                    KeyLockPayload {
                        key: "Num Lock".to_string(),
                        enabled: num,
                    },
                );
            }

            std::thread::sleep(Duration::from_millis(200));
        }
    });
}

pub fn setup_fullscreen_game_monitor(app_handle: AppHandle) {
    std::thread::spawn(move || {
        while !SHUTTING_DOWN.load(Ordering::Relaxed) {
            std::thread::sleep(Duration::from_secs(2));
            let _ = app_handle.emit(
                "fullscreen-game-changed",
                FullscreenGamePayload {
                    is_fullscreen: false,
                    reason: "none".to_string(),
                },
            );
        }
    });
}

pub fn setup_taskbar_hook() {}

pub fn setup_audio_visualization(app: AppHandle) {
    // Emits organic frequency wave data for the Apple phosphor green equalizer
    std::thread::spawn(move || {
        let mut phase: f64 = 0.0;
        while !SHUTTING_DOWN.load(Ordering::Relaxed) {
            std::thread::sleep(Duration::from_millis(50));
            phase += 0.18;
            let mut freqs = Vec::with_capacity(32);
            for i in 0..32 {
                let fi = i as f64;
                let val = ((phase * 1.8 + fi * 0.8).sin() * 0.35 + (phase * 2.7 - fi * 0.5).cos() * 0.25 + 0.45).clamp(0.15, 0.95);
                freqs.push(val as f32);
            }
            let payload = AudioVisualizationData { frequencies: freqs };
            let _ = app.emit("audio-visualization", &payload);
            let _ = app.emit("audio-visualization-data", &payload);
        }
    });
}

pub fn setup_settings_watcher(_app: AppHandle) {}

pub fn setup_brightness_worker() {}

pub fn setup_mouse_hook(_app: AppHandle) {}

pub fn setup_display_change_monitor(_app: AppHandle) {}

pub fn setup_window_change_hook(_app: AppHandle) {}

pub fn setup_thumbnail_capture(_app: AppHandle) {}

pub fn trigger_app_scan() {}

pub fn watch_webview_processes(_app: &AppHandle) {}

pub fn sync_overlays(app: &AppHandle) {
    if crate::state::OVERLAY_IN_SPLASH.load(Ordering::Relaxed) {
        return;
    }
    if let Some(ov_win) = app.get_webview_window("overlay") {
        if let Some(monitor) = crate::utils::get_best_monitor(app, &ov_win) {
            let size = monitor.size();
            let pos = monitor.position();
            let _ = ov_win.set_position(tauri::PhysicalPosition::new(pos.x, pos.y));
            let _ = ov_win.set_size(tauri::PhysicalSize::new(size.width, size.height));
        }
    }
}

pub fn setup_system_worker(_app: AppHandle) -> Sender<SystemCommand> {
    let (tx, rx) = channel::<SystemCommand>();

    std::thread::spawn(move || {
        while let Ok(cmd) = rx.recv() {
            if SHUTTING_DOWN.load(Ordering::Relaxed) {
                break;
            }
            match cmd {
                SystemCommand::SetVolume(vol) => {
                    // Try WirePlumber (PipeWire) first, then amixer (ALSA)
                    let pct = (vol * 100.0).round().clamp(0.0, 100.0) as u32;
                    let _ = Command::new("wpctl")
                        .args(["set-volume", "@DEFAULT_AUDIO_SINK@", &format!("{:.2}", vol)])
                        .status()
                        .or_else(|_| {
                            Command::new("amixer")
                                .args(["sset", "Master", &format!("{}%", pct)])
                                .status()
                        });
                }
                SystemCommand::MediaPlayPause => {
                    let _ = Command::new("playerctl")
                        .arg("play-pause")
                        .status()
                        .or_else(|_| {
                            Command::new("dbus-send")
                                .args([
                                    "--type=method_call",
                                    "--dest=org.mpris.MediaPlayer2.spotify",
                                    "/org/mpris/MediaPlayer2",
                                    "org.mpris.MediaPlayer2.Player.PlayPause",
                                ])
                                .status()
                        });
                }
                SystemCommand::MediaNext => {
                    let _ = Command::new("playerctl")
                        .arg("next")
                        .status()
                        .or_else(|_| {
                            Command::new("dbus-send")
                                .args([
                                    "--type=method_call",
                                    "--dest=org.mpris.MediaPlayer2.spotify",
                                    "/org/mpris/MediaPlayer2",
                                    "org.mpris.MediaPlayer2.Player.Next",
                                ])
                                .status()
                        });
                }
                SystemCommand::MediaPrevious => {
                    let _ = Command::new("playerctl")
                        .arg("previous")
                        .status()
                        .or_else(|_| {
                            Command::new("dbus-send")
                                .args([
                                    "--type=method_call",
                                    "--dest=org.mpris.MediaPlayer2.spotify",
                                    "/org/mpris/MediaPlayer2",
                                    "org.mpris.MediaPlayer2.Player.Previous",
                                ])
                                .status()
                        });
                }
                SystemCommand::MediaSeek(pos_ms) => {
                    let pos_micro = pos_ms * 1000;
                    let _ = Command::new("playerctl")
                        .args(["position", &format!("{}", pos_micro as f64 / 1_000_000.0)])
                        .status();
                }
                SystemCommand::ToggleVisibility(_) => {}
            }
        }
    });

    tx
}

pub fn get_clipboard_items() -> Vec<crate::types::ClipboardItem> {
    vec![]
}

pub fn clear_clipboard_items() {}

pub fn setup_download_watcher(_app: AppHandle) {}


