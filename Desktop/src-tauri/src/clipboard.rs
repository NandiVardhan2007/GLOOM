use serde::{Deserialize, Serialize};
use std::sync::Mutex;
use std::time::{SystemTime, UNIX_EPOCH};
use tauri::{AppHandle, Emitter, Manager};

pub const MAX_CLIPBOARD_ITEMS: usize = 30;
const HISTORY_FILE: &str = "clipboard-history.json";

static CLIPBOARD_HISTORY: Mutex<Vec<ClipboardItem>> = Mutex::new(Vec::new());

#[derive(Clone, Serialize, Deserialize, Debug, PartialEq)]
pub struct ClipboardItem {
    pub id: String,
    pub text: String,
    pub timestamp: u64,
    pub char_count: usize,
}

fn history_path(app: &AppHandle) -> Option<std::path::PathBuf> {
    app.path()
        .app_config_dir()
        .ok()
        .map(|dir| dir.join(HISTORY_FILE))
}

pub fn load_history(app: &AppHandle) -> Vec<ClipboardItem> {
    let items: Vec<ClipboardItem> = history_path(app)
        .and_then(|p| std::fs::read_to_string(p).ok())
        .and_then(|content| serde_json::from_str(&content).ok())
        .unwrap_or_default();

    if let Ok(mut guard) = CLIPBOARD_HISTORY.lock() {
        *guard = items.clone();
    }
    items
}

fn save_history(app: &AppHandle, items: &[ClipboardItem]) {
    if let Some(path) = history_path(app) {
        if let Some(parent) = path.parent() {
            let _ = std::fs::create_dir_all(parent);
        }
        if let Ok(content) = serde_json::to_string(items) {
            let _ = std::fs::write(path, content);
        }
    }
}

pub fn push_clip_internal(history: &mut Vec<ClipboardItem>, text: String) -> bool {
    let trimmed = text.trim();
    if trimmed.is_empty() {
        return false;
    }

    // Do nothing if identical to the most recent clip
    if let Some(first) = history.first() {
        if first.text == text {
            return false;
        }
    }

    // If it exists earlier in history, remove the old instance so it moves to top
    history.retain(|item| item.text != text);

    let now = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_secs())
        .unwrap_or(0);

    let char_count = text.chars().count();
    let id = format!("{}_{}", now, char_count);

    history.insert(
        0,
        ClipboardItem {
            id,
            text,
            timestamp: now,
            char_count,
        },
    );

    if history.len() > MAX_CLIPBOARD_ITEMS {
        history.truncate(MAX_CLIPBOARD_ITEMS);
    }

    true
}

pub fn add_clip(app: &AppHandle, text: String) {
    let mut history = match CLIPBOARD_HISTORY.lock() {
        Ok(guard) => guard,
        Err(_) => return,
    };

    if !push_clip_internal(&mut history, text) {
        return;
    }

    let items_clone = history.clone();
    drop(history);

    save_history(app, &items_clone);
    let _ = app.emit("clipboard-history-updated", &items_clone);
}

#[cfg(windows)]
pub fn read_win32_clipboard_text() -> Option<String> {
    use windows::Win32::Foundation::HGLOBAL;
    use windows::Win32::System::DataExchange::{
        CloseClipboard, GetClipboardData, IsClipboardFormatAvailable, OpenClipboard,
    };
    use windows::Win32::System::Memory::{GlobalLock, GlobalUnlock};

    const CF_UNICODETEXT: u32 = 13;

    unsafe {
        let mut opened = false;
        for _ in 0..5 {
            if OpenClipboard(None).is_ok() {
                opened = true;
                break;
            }
            std::thread::sleep(std::time::Duration::from_millis(10));
        }

        if !opened {
            return None;
        }

        let result = (|| {
            if IsClipboardFormatAvailable(CF_UNICODETEXT).is_err() {
                return None;
            }

            let handle = GetClipboardData(CF_UNICODETEXT).ok()?;
            if handle.is_invalid() {
                return None;
            }

            let hglobal = HGLOBAL(handle.0);
            let ptr = GlobalLock(hglobal);
            if ptr.is_null() {
                return None;
            }

            let u16_ptr = ptr as *const u16;
            let mut len = 0;
            while *u16_ptr.add(len) != 0 {
                len += 1;
                if len > 500_000 {
                    break;
                }
            }

            let slice = std::slice::from_raw_parts(u16_ptr, len);
            let text = String::from_utf16_lossy(slice);

            let _ = GlobalUnlock(hglobal);
            Some(text)
        })();

        let _ = CloseClipboard();
        result
    }
}

#[cfg(not(windows))]
pub fn read_win32_clipboard_text() -> Option<String> {
    None
}

pub fn start_clipboard_monitor(app_handle: AppHandle) {
    #[cfg(windows)]
    {
        use windows::Win32::System::DataExchange::GetClipboardSequenceNumber;

        load_history(&app_handle);

        std::thread::spawn(move || {
            let mut last_seq: u32 = unsafe { GetClipboardSequenceNumber() };

            if let Some(initial_text) = read_win32_clipboard_text() {
                add_clip(&app_handle, initial_text);
            }

            loop {
                std::thread::sleep(std::time::Duration::from_millis(300));

                if crate::state::SHUTTING_DOWN.load(std::sync::atomic::Ordering::Relaxed) {
                    break;
                }

                let current_seq = unsafe { GetClipboardSequenceNumber() };
                if current_seq != last_seq {
                    last_seq = current_seq;

                    if let Some(text) = read_win32_clipboard_text() {
                        add_clip(&app_handle, text);
                    }
                }
            }
        });
    }

    #[cfg(not(windows))]
    {
        let _ = app_handle;
    }
}

#[tauri::command]
pub fn get_clipboard_history(app: AppHandle) -> Vec<ClipboardItem> {
    if let Ok(guard) = CLIPBOARD_HISTORY.lock() {
        if !guard.is_empty() {
            return guard.clone();
        }
    }
    load_history(&app)
}

#[tauri::command]
pub fn clear_clipboard_history(app: AppHandle) -> Result<(), String> {
    if let Ok(mut guard) = CLIPBOARD_HISTORY.lock() {
        guard.clear();
    }
    save_history(&app, &[]);
    let _ = app.emit("clipboard-history-updated", Vec::<ClipboardItem>::new());
    Ok(())
}

#[tauri::command]
pub fn request_notch_keyboard_focus(app: AppHandle, focus: bool) -> Result<(), String> {
    if let Some(window) = app.get_webview_window("main") {
        if focus {
            let _ = window.set_focus();
        } else {
            #[cfg(windows)]
            {
                use windows::Win32::UI::Input::KeyboardAndMouse::SetFocus;
                unsafe {
                    let _ = SetFocus(None);
                }
            }
        }
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_clipboard_item_serialization() {
        let item = ClipboardItem {
            id: "123_4".to_string(),
            text: "Hello GLOOM".to_string(),
            timestamp: 1700000000,
            char_count: 11,
        };

        let json = serde_json::to_string(&item).expect("serialize");
        assert!(json.contains("\"char_count\":11"));
        assert!(json.contains("\"text\":\"Hello GLOOM\""));

        let deserialized: ClipboardItem = serde_json::from_str(&json).expect("deserialize");
        assert_eq!(item, deserialized);
    }

    #[test]
    fn test_push_clip_internal_deduplication_and_limit() {
        let mut history = Vec::new();

        assert!(push_clip_internal(&mut history, "First Clip".to_string()));
        assert_eq!(history.len(), 1);
        assert_eq!(history[0].text, "First Clip");

        // Duplicate consecutive insertion should be ignored
        assert!(!push_clip_internal(&mut history, "First Clip".to_string()));
        assert_eq!(history.len(), 1);

        // Blank or whitespace-only clips should be rejected
        assert!(!push_clip_internal(&mut history, "   ".to_string()));
        assert_eq!(history.len(), 1);

        // Second unique clip
        assert!(push_clip_internal(&mut history, "Second Clip".to_string()));
        assert_eq!(history.len(), 2);
        assert_eq!(history[0].text, "Second Clip");
        assert_eq!(history[1].text, "First Clip");

        // Re-copying First Clip moves it to the top
        assert!(push_clip_internal(&mut history, "First Clip".to_string()));
        assert_eq!(history.len(), 2);
        assert_eq!(history[0].text, "First Clip");
        assert_eq!(history[1].text, "Second Clip");

        // Fill past MAX_CLIPBOARD_ITEMS
        for i in 0..MAX_CLIPBOARD_ITEMS + 5 {
            push_clip_internal(&mut history, format!("Clip #{i}"));
        }
        assert_eq!(history.len(), MAX_CLIPBOARD_ITEMS);
    }
}
