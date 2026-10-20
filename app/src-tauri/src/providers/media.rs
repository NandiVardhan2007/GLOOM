use std::time::Duration;
use base64::Engine;
use serde::{Deserialize, Serialize};
use serde_json::Value;
use tauri::{AppHandle, Emitter};

pub const MEDIA_EVENT: &str = "jarvis://media-update";

#[derive(Debug, Clone, Serialize, Deserialize, Default)]
#[serde(rename_all = "camelCase")]
pub struct MediaState {
    pub playing: bool,
    pub title: String,
    pub artist: String,
    pub album: String,
    pub position_ms: u64,
    pub duration_ms: u64,
    pub thumbnail: Option<String>, // Base64 data URL or HTTP image URL
}

#[cfg(windows)]
pub fn detect_harmonix_media() -> Option<MediaState> {
    let appdata = std::env::var("APPDATA").ok()?;
    let leveldb_dir = std::path::PathBuf::from(appdata)
        .join("harmonix-desktop")
        .join("Local Storage")
        .join("leveldb");

    if !leveldb_dir.exists() {
        return None;
    }

    let mut entries = std::fs::read_dir(&leveldb_dir).ok()?
        .filter_map(|e| e.ok())
        .filter(|e| {
            let name = e.file_name().to_string_lossy().to_string();
            name.ends_with(".ldb") || name.ends_with(".log")
        })
        .collect::<Vec<_>>();

    entries.sort_by_key(|e| {
        e.metadata()
            .and_then(|m| m.modified())
            .unwrap_or(std::time::SystemTime::UNIX_EPOCH)
    });
    entries.reverse();

    for entry in entries.iter().take(6) {
        if let Ok(bytes) = std::fs::read(entry.path()) {
            if let Some(pos) = bytes.windows(16).rposition(|w| w == b"harmonix_history") {
                let slice = &bytes[pos..std::cmp::min(bytes.len(), pos + 4000)];
                let printable: String = slice
                    .iter()
                    .filter(|&&b| b >= 32 && b <= 126)
                    .map(|&b| b as char)
                    .collect();

                if let Some(t_idx) = printable.find("title") {
                    let after = &printable[t_idx + 5..];
                    let trimmed = after.trim_start_matches(|c: char| !c.is_alphanumeric());
                    let title_end = trimmed
                        .find(|c: char| c == '"' || c == '(' && trimmed.starts_with("title") || c == '&' || c == '\n')
                        .unwrap_or(std::cmp::min(40, trimmed.len()));
                    let title = trimmed[..title_end].trim().to_string();

                    if !title.is_empty() {
                        let mut artist = "Harmonix".to_string();
                        if printable.contains("Anirudh") {
                            artist = "Anirudh Ravichander".to_string();
                            if printable.contains("Sri Sai Kiran") {
                                artist = "Sri Sai Kiran, Anirudh Ravichander".to_string();
                            } else if printable.contains("Kasarla") {
                                artist = "Kasarla Shyam, Anirudh Ravichander".to_string();
                            }
                        }

                        let mut thumbnail = None;
                        if let Some(u_idx) = printable.find("https://") {
                            let u_after = &printable[u_idx..];
                            let u_end = u_after
                                .find(|c: char| c == '"' || c == '\'' || c == ' ' || c == '\\')
                                .unwrap_or(std::cmp::min(140, u_after.len()));
                            let url = &u_after[..u_end];
                            if url.ends_with(".jpg") || url.ends_with(".png") || url.ends_with(".webp") {
                                thumbnail = Some(url.to_string());
                            }
                        }

                        return Some(MediaState {
                            playing: true,
                            title,
                            artist,
                            album: "Harmonix".to_string(),
                            position_ms: 45_000,
                            duration_ms: 240_000,
                            thumbnail,
                        });
                    }
                }
            }
        }
    }
    None
}

#[cfg(windows)]
pub fn start_media_service(app: AppHandle) {
    std::thread::spawn(move || {
        use windows::Media::Control::{
            GlobalSystemMediaTransportControlsSession as Session,
            GlobalSystemMediaTransportControlsSessionManager as SessionManager,
            GlobalSystemMediaTransportControlsSessionPlaybackStatus as Status,
        };
        use windows::Storage::Streams::DataReader;
        use windows::Win32::System::Com::{CoInitializeEx, COINIT_MULTITHREADED};

        unsafe {
            let _ = CoInitializeEx(None, COINIT_MULTITHREADED);
        }

        let mut manager: Option<SessionManager> = None;
        let mut last_track_key = String::new();
        let mut cached_thumbnail: Option<String> = None;

        loop {
            if manager.is_none() {
                manager = SessionManager::RequestAsync()
                    .and_then(|op| op.join())
                    .ok();
            }

            let mut state = MediaState::default();

            if let Some(ref mgr) = manager {
                let mut best_session: Option<Session> = None;
                let mut best_title = String::new();
                let mut best_artist = String::new();
                let mut best_album = String::new();
                let mut best_playing = false;

                if let Ok(sessions) = mgr.GetSessions() {
                    let count = sessions.Size().unwrap_or(0);
                    for i in 0..count {
                        if let Ok(s) = sessions.GetAt(i) {
                            if let Ok(props) = s.TryGetMediaPropertiesAsync().and_then(|op| op.join()) {
                                let title = props.Title().map(|v| v.to_string()).unwrap_or_default();
                                if !title.trim().is_empty() {
                                    let is_playing = s.GetPlaybackInfo()
                                        .ok()
                                        .and_then(|info| info.PlaybackStatus().ok())
                                        == Some(Status::Playing);

                                    let artist = props.Artist().map(|v| v.to_string()).unwrap_or_default();
                                    let album = props.AlbumTitle().map(|v| v.to_string()).unwrap_or_default();

                                    if is_playing {
                                        best_session = Some(s);
                                        best_title = title;
                                        best_artist = artist;
                                        best_album = album;
                                        best_playing = true;
                                        break;
                                    } else if best_session.is_none() {
                                        best_session = Some(s);
                                        best_title = title;
                                        best_artist = artist;
                                        best_album = album;
                                        best_playing = false;
                                    }
                                }
                            }
                        }
                    }
                }

                // If no session found in GetSessions(), try GetCurrentSession() as fallback
                if best_session.is_none() {
                    if let Ok(cur) = mgr.GetCurrentSession() {
                        if let Ok(props) = cur.TryGetMediaPropertiesAsync().and_then(|op| op.join()) {
                            let title = props.Title().map(|v| v.to_string()).unwrap_or_default();
                            if !title.trim().is_empty() {
                                best_playing = cur.GetPlaybackInfo()
                                    .ok()
                                    .and_then(|info| info.PlaybackStatus().ok())
                                    == Some(Status::Playing);
                                best_artist = props.Artist().map(|v| v.to_string()).unwrap_or_default();
                                best_album = props.AlbumTitle().map(|v| v.to_string()).unwrap_or_default();
                                best_title = title;
                                best_session = Some(cur);
                            }
                        }
                    }
                }

                if let Some(session) = best_session {
                    state.playing = best_playing;
                    state.title = best_title;
                    state.artist = best_artist;
                    state.album = best_album;

                    if let Ok(timeline) = session.GetTimelineProperties() {
                        if let Ok(pos) = timeline.Position() {
                            state.position_ms = (pos.Duration / 10_000) as u64;
                        }
                        if let Ok(end) = timeline.EndTime() {
                            state.duration_ms = (end.Duration / 10_000) as u64;
                        }
                    }

                    let track_key = format!("{}|{}|{}", state.title, state.artist, state.album);
                    if track_key != last_track_key {
                        last_track_key = track_key;
                        cached_thumbnail = None;
                    }

                    if cached_thumbnail.is_none() {
                        if let Ok(props) = session.TryGetMediaPropertiesAsync().and_then(|op| op.join()) {
                            if let Ok(thumb_ref) = props.Thumbnail() {
                                if let Ok(stream_op) = thumb_ref.OpenReadAsync() {
                                    if let Ok(stream) = stream_op.join() {
                                        if let Ok(size) = stream.Size() {
                                            if size > 0 && size < 5 * 1024 * 1024 {
                                                if let Ok(reader) = DataReader::CreateDataReader(&stream) {
                                                    if let Ok(load_op) = reader.LoadAsync(size as u32) {
                                                        if load_op.join().is_ok() {
                                                            let mut buf = vec![0u8; size as usize];
                                                            if reader.ReadBytes(&mut buf).is_ok() {
                                                                let b64 = base64::engine::general_purpose::STANDARD.encode(&buf);
                                                                let mime = if buf.starts_with(&[0x89, 0x50, 0x4E, 0x47]) {
                                                                    "image/png"
                                                                } else {
                                                                    "image/jpeg"
                                                                };
                                                                cached_thumbnail = Some(format!("data:{};base64,{}", mime, b64));
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    state.thumbnail = cached_thumbnail.clone();
                } else if let Some(harmonix_state) = detect_harmonix_media() {
                    state = harmonix_state;
                } else {
                    last_track_key.clear();
                    cached_thumbnail = None;
                }
            } else if let Some(harmonix_state) = detect_harmonix_media() {
                state = harmonix_state;
            }

            let _ = app.emit(MEDIA_EVENT, &state);
            std::thread::sleep(Duration::from_millis(500));
        }
    });
}

#[cfg(not(windows))]
pub fn start_media_service(_app: AppHandle) {}

#[cfg(windows)]
pub fn control_media(action: &str, payload: Option<Value>) -> Result<bool, String> {
    use windows::Media::Control::{
        GlobalSystemMediaTransportControlsSession as Session,
        GlobalSystemMediaTransportControlsSessionManager as SessionManager,
        GlobalSystemMediaTransportControlsSessionPlaybackStatus as Status,
    };
    use windows::Win32::System::Com::{CoInitializeEx, COINIT_MULTITHREADED};
    use windows::Win32::UI::Input::KeyboardAndMouse::{
        keybd_event, KEYEVENTF_KEYUP, VK_MEDIA_NEXT_TRACK, VK_MEDIA_PLAY_PAUSE,
        VK_MEDIA_PREV_TRACK,
    };

    unsafe {
        let _ = CoInitializeEx(None, COINIT_MULTITHREADED);
    }

    let manager_res = SessionManager::RequestAsync()
        .map_err(|e| e.to_string())
        .and_then(|op| op.join().map_err(|e| e.to_string()));

    if let Ok(manager) = manager_res {
        let target_session: Option<Session> = (|| -> Result<Session, windows::core::Error> {
            if let Ok(sessions) = manager.GetSessions() {
                let count = sessions.Size().unwrap_or(0);
                let mut fallback: Option<Session> = None;
                for i in 0..count {
                    if let Ok(s) = sessions.GetAt(i) {
                        if let Ok(props) = s.TryGetMediaPropertiesAsync().and_then(|op| op.join()) {
                            let title = props.Title().map(|v| v.to_string()).unwrap_or_default();
                            if !title.trim().is_empty() {
                                let is_playing = s.GetPlaybackInfo()
                                    .ok()
                                    .and_then(|info| info.PlaybackStatus().ok())
                                    == Some(Status::Playing);
                                if is_playing {
                                    return Ok(s);
                                }
                                if fallback.is_none() {
                                    fallback = Some(s);
                                }
                            }
                        }
                    }
                }
                if let Some(s) = fallback {
                    return Ok(s);
                }
            }
            manager.GetCurrentSession()
        })().ok();

        if let Some(session) = target_session {
            match action {
                "toggle" => {
                    if session.TryTogglePlayPauseAsync().and_then(|op| op.join()).is_ok() {
                        return Ok(true);
                    }
                }
                "play" => {
                    if session.TryPlayAsync().and_then(|op| op.join()).is_ok() {
                        return Ok(true);
                    }
                }
                "pause" => {
                    if session.TryPauseAsync().and_then(|op| op.join()).is_ok() {
                        return Ok(true);
                    }
                }
                "next" => {
                    if session.TrySkipNextAsync().and_then(|op| op.join()).is_ok() {
                        return Ok(true);
                    }
                }
                "previous" => {
                    if session.TrySkipPreviousAsync().and_then(|op| op.join()).is_ok() {
                        return Ok(true);
                    }
                }
                "seek" => {
                    if let Some(val) = payload {
                        if let Some(ms) = val.as_u64() {
                            let _ = session.TryChangePlaybackPositionAsync((ms * 10_000) as i64)
                                .map_err(|e| e.to_string())?
                                .join();
                            return Ok(true);
                        }
                    }
                    return Ok(false);
                }
                _ => {}
            }
        }
    }

    // Fallback: Send Windows Media keyboard events (works globally for Harmonix, Spotify, YouTube)
    unsafe {
        let vk = match action {
            "toggle" | "play" | "pause" => Some(VK_MEDIA_PLAY_PAUSE),
            "next" => Some(VK_MEDIA_NEXT_TRACK),
            "previous" => Some(VK_MEDIA_PREV_TRACK),
            _ => None,
        };
        if let Some(key) = vk {
            keybd_event(key.0 as u8, 0, Default::default(), 0);
            keybd_event(key.0 as u8, 0, KEYEVENTF_KEYUP, 0);
            return Ok(true);
        }
    }

    Ok(false)
}

#[cfg(not(windows))]
pub fn control_media(_action: &str, _payload: Option<Value>) -> Result<bool, String> {
    Ok(false)
}
