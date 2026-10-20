use std::fs;
use std::path::PathBuf;
use std::sync::Mutex;
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(default, rename_all = "camelCase")]
pub struct AppConfig {
    // Geometry & Display
    pub mode: String,             // "island", "notch", "bar"
    pub top_offset: i32,          // pixels from top
    pub island_width: i32,        // default 210
    pub island_height: i32,       // default 34
    pub expanded_width: i32,      // default 740
    pub expanded_height: i32,     // default 250
    pub island_scale: f32,        // default 1.0 (0.7 - 1.5)
    pub pill_edge_gap: i32,       // default 12px
    pub clock_format: String,     // "12h" or "24h"
    pub selected_monitor: String, // "primary"
    pub screen_position: String,  // "top", "left", "right"

    // Behavior & Gestures
    pub autostart: bool,
    pub hover_to_show: bool,
    pub expand_on_hover: bool,
    pub avoid_fullscreen: bool,
    pub hide_behind_maximized: bool,
    pub pin_mode: bool,
    pub lock_drag: bool,

    // Appearance & Style
    pub theme: String,            // "obsidian", "dark", "glass", "solid"
    pub accent_color: String,     // "#00d2ff"
    pub bg_color: String,         // "#121214"
    pub idle_color: String,       // "#0a0a0c"
    pub corner_shape: String,     // "rounded", "squared"
    pub panel_style: String,      // "glass", "dark_glass", "solid"
    pub card_style: String,       // "light", "dark", "darker"
    pub glow_intensity: String,   // "off", "subtle", "strong"
    pub cycle_colors: bool,
    pub custom_bg_url: String,
    pub bg_animation: String,

    // Widgets visibility
    pub show_media_widget: bool,
    pub show_audio_waveform: bool,
    pub show_volume_mixer: bool,
    pub show_agent_usage: bool,
    pub show_agent_notifications: bool,
    pub show_drop_shelf: bool,
    pub show_clipboard_history: bool,
    pub show_downloads: bool,
    pub show_hardware_widget: bool,
    pub show_network_widget: bool,
    pub show_weather_widget: bool,
    pub show_pomodoro: bool,
    pub show_quick_tools: bool,
    pub volume_hud_enabled: bool,
    pub clipboard_history_enabled: bool,
    pub reduced_motion: bool,

    // Notchy / AI Companion
    pub notchy_compact_indicator: bool,
    pub notchy_quiet_mode: bool,
    pub notchy_glow_strength: String, // "off", "subtle", "full"
    pub notchy_completion_duration: i32, // 5, 8, 12
    pub notchy_preferred_editor: String, // "auto", "antigravity", "code", "cursor", "explorer"
    pub notchy_model: String, // "neko", "robot", "orb"
}

impl Default for AppConfig {
    fn default() -> Self {
        Self {
            mode: "island".to_string(),
            top_offset: 12,
            island_width: 210,
            island_height: 34,
            expanded_width: 740,
            expanded_height: 250,
            island_scale: 1.0,
            pill_edge_gap: 12,
            clock_format: "12h".to_string(),
            selected_monitor: "primary".to_string(),
            screen_position: "top".to_string(),

            autostart: false,
            hover_to_show: false,
            expand_on_hover: true,
            avoid_fullscreen: true,
            hide_behind_maximized: true,
            pin_mode: false,
            lock_drag: false,

            theme: "obsidian".to_string(),
            accent_color: "#00d2ff".to_string(),
            bg_color: "#121214".to_string(),
            idle_color: "#0a0a0c".to_string(),
            corner_shape: "rounded".to_string(),
            panel_style: "dark_glass".to_string(),
            card_style: "dark".to_string(),
            glow_intensity: "subtle".to_string(),
            cycle_colors: false,
            custom_bg_url: "".to_string(),
            bg_animation: "glow".to_string(),

            show_media_widget: true,
            show_audio_waveform: true,
            show_volume_mixer: true,
            show_agent_usage: true,
            show_agent_notifications: true,
            show_drop_shelf: true,
            show_clipboard_history: true,
            show_downloads: true,
            show_hardware_widget: true,
            show_network_widget: true,
            show_weather_widget: true,
            show_pomodoro: true,
            show_quick_tools: true,
            volume_hud_enabled: true,
            clipboard_history_enabled: true,
            reduced_motion: false,

            notchy_compact_indicator: true,
            notchy_quiet_mode: false,
            notchy_glow_strength: "subtle".to_string(),
            notchy_completion_duration: 5,
            notchy_preferred_editor: "auto".to_string(),
            notchy_model: "neko".to_string(),
        }
    }
}

pub struct ConfigStore {
    path: PathBuf,
    pub config: Mutex<AppConfig>,
}

impl ConfigStore {
    pub fn new() -> Self {
        let app_dir = dirs_fallback().join("JARVIS");
        let _ = fs::create_dir_all(&app_dir);
        let path = app_dir.join("config.json");

        let mut config: AppConfig = if let Ok(data) = fs::read_to_string(&path) {
            serde_json::from_str(&data).unwrap_or_default()
        } else {
            AppConfig::default()
        };

        if config.expanded_width < 740 {
            config.expanded_width = 740;
        }
        if config.expanded_height < 250 {
            config.expanded_height = 250;
        }

        Self {
            path,
            config: Mutex::new(config),
        }
    }

    pub fn get(&self) -> AppConfig {
        self.config.lock().unwrap().clone()
    }

    pub fn set(&self, new_config: AppConfig) {
        let mut cfg = self.config.lock().unwrap();
        *cfg = new_config.clone();
        if let Ok(json) = serde_json::to_string_pretty(&new_config) {
            let _ = fs::write(&self.path, json);
        }
    }
}

fn dirs_fallback() -> PathBuf {
    if let Ok(appdata) = std::env::var("APPDATA") {
        PathBuf::from(appdata)
    } else {
        PathBuf::from(".")
    }
}
