use std::sync::Mutex;
use std::time::Duration;
use serde::{Deserialize, Serialize};
use tauri::{AppHandle, Emitter};

pub const CLIPBOARD_EVENT: &str = "jarvis://clipboard-update";

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ClipboardItem {
    pub id: u64,
    pub text: String,
    pub preview: String,
    pub timestamp_ms: u64,
}

static ITEMS: Mutex<Vec<ClipboardItem>> = Mutex::new(Vec::new());
static LAST_EXTERNAL_TEXT: Mutex<Option<String>> = Mutex::new(None);

#[cfg(windows)]
pub fn start_clipboard_service(app: AppHandle) {
    std::thread::spawn(move || {
        use windows::Win32::System::DataExchange::{
            CloseClipboard, GetClipboardData, GetClipboardSequenceNumber, OpenClipboard,
        };
        use windows::Win32::System::Memory::{GlobalLock, GlobalUnlock};
        use tauri::Manager;

        let mut last_seq = 0u32;
        let mut id_counter = 1u64;

        loop {
            let seq = unsafe { GetClipboardSequenceNumber() };
            if seq != 0 && seq != last_seq {
                last_seq = seq;

                // Query configuration for opt-in clipboard permissions
                let (history_enabled, sync_enabled) = if let Some(cfg_store) = app.try_state::<crate::config::ConfigStore>() {
                    let cfg = cfg_store.get();
                    (cfg.clipboard_history_enabled, cfg.bridge_clipboard_sync_enabled)
                } else {
                    (true, false)
                };

                if history_enabled || sync_enabled {
                    // Try opening clipboard with bounded backoff
                    let mut opened = false;
                    for backoff_ms in [5, 15, 30] {
                        if unsafe { OpenClipboard(None).is_ok() } {
                            opened = true;
                            break;
                        }
                        std::thread::sleep(Duration::from_millis(backoff_ms));
                    }

                    if opened {
                        // Check CF_UNICODETEXT (13)
                        if let Ok(handle) = unsafe { GetClipboardData(13) } {
                            if !handle.is_invalid() {
                                let h_global = windows::Win32::Foundation::HGLOBAL(handle.0);
                                let ptr = unsafe { GlobalLock(h_global) } as *const u16;
                                if !ptr.is_null() {
                                    // Bounded read up to 262,144 UTF-16 code units (512 KB), no arbitrary 2,000 truncation
                                    const MAX_CHARS: usize = 262_144;
                                    let mut len = 0;
                                    while len < MAX_CHARS && unsafe { *ptr.add(len) } != 0 {
                                        len += 1;
                                    }
                                    // Protect against splitting Unicode surrogate pairs
                                    if len == MAX_CHARS {
                                        let last = unsafe { *ptr.add(len - 1) };
                                        if (0xD800..=0xDBFF).contains(&last) {
                                            len -= 1;
                                        }
                                    }

                                    let slice = unsafe { std::slice::from_raw_parts(ptr, len) };
                                    let text = String::from_utf16_lossy(slice).trim().to_string();

                                    // Obey native memory rules: unlock locked clipboard global handle
                                    unsafe {
                                        let _ = GlobalUnlock(h_global);
                                    }

                                    if !text.is_empty() {
                                        let preview = preview_text(&text);

                                        let item = ClipboardItem {
                                            id: id_counter,
                                            text,
                                            preview,
                                            timestamp_ms: std::time::SystemTime::now()
                                                .duration_since(std::time::UNIX_EPOCH)
                                                .unwrap_or_default()
                                                .as_millis() as u64,
                                        };
                                        id_counter += 1;

                                        let is_from_external = {
                                            let mut ext = LAST_EXTERNAL_TEXT.lock().unwrap();
                                            if ext.as_deref() == Some(&item.text) {
                                                *ext = None;
                                                true
                                            } else {
                                                false
                                            }
                                        };

                                        if history_enabled {
                                            let mut lock = ITEMS.lock().unwrap();
                                            if lock.first().map(|i| &i.text) != Some(&item.text) {
                                                lock.insert(0, item.clone());
                                                if lock.len() > 20 {
                                                    lock.pop();
                                                }
                                                let items = lock.clone();
                                                let _ = app.emit(CLIPBOARD_EVENT, &items);
                                            }
                                        }

                                        // Only broadcast if user explicitly enabled bridge clipboard sync and not from external
                                        if sync_enabled && !is_from_external {
                                            crate::providers::livebridge::broadcast_clipboard_to_mobile(&item.text);
                                        }
                                    }
                                }
                            }
                        }
                        unsafe {
                            let _ = CloseClipboard();
                        }
                    }
                }
            }

            std::thread::sleep(Duration::from_millis(400));
        }
    });
}

#[cfg(not(windows))]
pub fn start_clipboard_service(_app: AppHandle) {}

#[cfg(windows)]
pub fn set_clipboard_text(text: &str) -> bool {
    use windows::Win32::System::DataExchange::{CloseClipboard, EmptyClipboard, OpenClipboard, SetClipboardData};
    use windows::Win32::System::Memory::{GlobalAlloc, GlobalLock, GlobalUnlock, GMEM_MOVEABLE};

    #[link(name = "kernel32")]
    extern "system" {
        fn GlobalFree(hmem: windows::Win32::Foundation::HGLOBAL) -> windows::Win32::Foundation::HGLOBAL;
    }

    unsafe {
        let mut opened = false;
        for backoff_ms in [5, 15, 30, 60, 120] {
            if OpenClipboard(None).is_ok() {
                opened = true;
                break;
            }
            std::thread::sleep(Duration::from_millis(backoff_ms));
        }

        if !opened {
            return false;
        }

        let _ = EmptyClipboard();

        let utf16: Vec<u16> = text.encode_utf16().chain(std::iter::once(0)).collect();
        let bytes_len = utf16.len() * 2;

        let mut success = false;
        if let Ok(h_mem) = GlobalAlloc(GMEM_MOVEABLE, bytes_len) {
            let ptr = GlobalLock(h_mem) as *mut u16;
            if !ptr.is_null() {
                std::ptr::copy_nonoverlapping(utf16.as_ptr(), ptr, utf16.len());
                let _ = GlobalUnlock(h_mem);
                // Windows takes ownership of h_mem only on successful SetClipboardData
                if SetClipboardData(13, Some(windows::Win32::Foundation::HANDLE(h_mem.0))).is_ok() {
                    success = true;
                } else {
                    let _ = GlobalFree(h_mem);
                }
            } else {
                let _ = GlobalFree(h_mem);
            }
        }
        let _ = CloseClipboard();
        success
    }
}

#[cfg(not(windows))]
pub fn set_clipboard_text(_text: &str) -> bool {
    false
}

pub fn get_clipboard_items() -> Vec<ClipboardItem> {
    ITEMS.lock().unwrap().clone()
}

fn preview_text(text: &str) -> String {
    if text.chars().count() > 60 {
        let truncated: String = text.chars().take(57).collect();
        format!("{}...", truncated)
    } else {
        text.to_string()
    }
}

#[cfg(test)]
mod tests {
    use super::preview_text;

    #[test]
    fn preview_short_text_is_unchanged() {
        assert_eq!(preview_text("hello"), "hello");
    }

    #[test]
    fn preview_truncates_multibyte_text_without_panicking() {
        let text = "é".repeat(100);
        let preview = preview_text(&text);
        assert_eq!(preview.chars().count(), 60);
        assert!(preview.ends_with("..."));
    }
}

pub fn set_clipboard_text_from_external(text: &str) -> bool {
    {
        let mut ext = LAST_EXTERNAL_TEXT.lock().unwrap();
        *ext = Some(text.to_string());
    }
    set_clipboard_text(text)
}
