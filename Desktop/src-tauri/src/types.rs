use serde::{Deserialize, Serialize};

#[derive(Deserialize, Debug)]
#[serde(rename = "WmiMonitorBrightness")]
#[serde(rename_all = "PascalCase")]
pub struct WmiMonitorBrightness {
    pub current_brightness: u8,
}

/// Raised by Windows whenever a display's brightness changes, by any means.
#[derive(Deserialize, Debug)]
#[serde(rename = "WmiMonitorBrightnessEvent")]
#[serde(rename_all = "PascalCase")]
pub struct WmiMonitorBrightnessEvent {
    pub brightness: u8,
}

#[derive(Clone, Serialize, Debug, PartialEq)]
pub struct AudioVisualizationData {
    pub frequencies: Vec<f32>,
}

#[derive(Serialize, Deserialize, Clone, Debug, PartialEq)]
pub struct MediaInfo {
    pub title: String,
    pub artist: String,
    pub is_playing: bool,
    pub has_media: bool,
    pub artwork: Option<Vec<String>>,
    #[serde(default)]
    pub position_ms: i64,
    #[serde(default)]
    pub duration_ms: i64,
    #[serde(default)]
    pub seek_enabled: bool,
    #[serde(default)]
    pub position_updated_at: u64,
}

pub enum SystemCommand {
    SetVolume(f32),
    MediaPlayPause,
    MediaNext,
    MediaPrevious,
    MediaSeek(i64),
    ToggleVisibility(bool),
}

#[derive(Clone, Copy, Deserialize, Serialize, Debug, PartialEq, Eq)]
pub struct IntRect {
    pub x: i32,
    pub y: i32,
    pub width: i32,
    pub height: i32,
}

#[derive(Serialize, Deserialize, Clone, Debug, PartialEq)]
pub struct AppInfo {
    pub name: String,
    pub path: String,
    pub icon: Option<String>,
    pub is_running: bool,
    pub hwnd: Option<isize>,
    pub executable: Option<String>,
    pub all_hwnds: Option<Vec<(isize, String)>>, // (hwnd, title)
    #[serde(default)]
    pub is_minimized: Option<bool>,
}

#[derive(Clone, Serialize, Debug, PartialEq)]
pub struct BrightnessChangeEvent {
    pub brightness: u32,
}

#[derive(Clone, Serialize, Debug, PartialEq)]
pub struct VolumeChangeEvent {
    pub volume: f32,
    pub is_muted: bool,
}

#[derive(Clone, Serialize, Debug, PartialEq)]
pub struct WifiStatus {
    pub enabled: bool,
    pub connected: bool,
    /// The connected network's name, for the controls tile.
    pub ssid: Option<String>,
}

#[derive(Clone, Serialize, Debug, PartialEq)]
pub struct AudioSessionInfo {
    pub pid: u32,
    pub name: String,
    pub process_path: Option<String>,
    pub volume: f32,
    pub is_muted: bool,
}

#[derive(Serialize, Deserialize, Clone, Debug, PartialEq)]
pub struct ClipboardItem {
    pub id: String,
    pub text: String,
    pub timestamp: u64,
    pub char_count: usize,
}

#[derive(Serialize, Deserialize, Clone, Debug, PartialEq)]
pub struct LiveActivityItem {
    pub id: String,
    #[serde(rename = "type")]
    pub activity_type: String,
    pub title: String,
    pub progress: Option<f32>,
    pub detail: String,
}

#[derive(Serialize, Deserialize, Clone, Debug, PartialEq, Eq)]
pub struct LyricLineDto {
    pub time_ms: i64,
    pub text: String,
}

#[derive(Serialize, Deserialize, Clone, Debug, PartialEq)]
pub struct LyricsResult {
    pub id: Option<i64>,
    pub track_name: Option<String>,
    pub artist_name: Option<String>,
    pub synced_lyrics: Option<String>,
    pub plain_lyrics: Option<String>,
    pub lines: Vec<LyricLineDto>,
    pub is_synced: bool,
}


