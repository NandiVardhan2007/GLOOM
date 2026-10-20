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
        use windows::Win32::System::Memory::GlobalLock;

        let mut last_seq = 0u32;
        let mut id_counter = 1u64;

        loop {
            let seq = unsafe { GetClipboardSequenceNumber() };
            if seq != 0 && seq != last_seq {
                last_seq = seq;

                if unsafe { OpenClipboard(None).is_ok() } {
                    // Check CF_UNICODETEXT (13)
                    if let Ok(handle) = unsafe { GetClipboardData(13) } {
                        if !handle.is_invalid() {
                            let ptr = unsafe { GlobalLock(windows::Win32::Foundation::HGLOBAL(handle.0)) } as *const u16;
                            if !ptr.is_null() {
                                let mut len = 0;
                                while unsafe { *ptr.add(len) } != 0 && len < 2000 {
                                    len += 1;
                                }
                                let slice = unsafe { std::slice::from_raw_parts(ptr, len) };
                                let text = String::from_utf16_lossy(slice).trim().to_string();

                                if !text.is_empty() {
                                    let preview = if text.len() > 60 {
                                        format!("{}...", &text[..57])
                                    } else {
                                        text.clone()
                                    };

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

                                    let mut lock = ITEMS.lock().unwrap();
                                    // Avoid duplicate top item
                                    if lock.first().map(|i| &i.text) != Some(&item.text) {
                                        lock.insert(0, item.clone());
                                        if lock.len() > 20 {
                                            lock.pop();
                                        }
                                        let items = lock.clone();
                                        let _ = app.emit(CLIPBOARD_EVENT, &items);

                                        if !is_from_external {
                                            crate::providers::livebridge::broadcast_clipboard_to_mobile(&item.text);
                                        }
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

    unsafe {
        if OpenClipboard(None).is_err() {
            return false;
        }
        let _ = EmptyClipboard();

        let utf16: Vec<u16> = text.encode_utf16().chain(std::iter::once(0)).collect();
        let bytes_len = utf16.len() * 2;

        if let Ok(h_mem) = GlobalAlloc(GMEM_MOVEABLE, bytes_len) {
            let ptr = GlobalLock(h_mem) as *mut u16;
            if !ptr.is_null() {
                std::ptr::copy_nonoverlapping(utf16.as_ptr(), ptr, utf16.len());
                let _ = GlobalUnlock(h_mem);
                let _ = SetClipboardData(13, Some(windows::Win32::Foundation::HANDLE(h_mem.0)));
            }
        }
        let _ = CloseClipboard();
    }
    true
}

#[cfg(not(windows))]
pub fn set_clipboard_text(_text: &str) -> bool {
    false
}

pub fn get_clipboard_items() -> Vec<ClipboardItem> {
    ITEMS.lock().unwrap().clone()
}

pub fn set_clipboard_text_from_external(text: &str) -> bool {
    {
        let mut ext = LAST_EXTERNAL_TEXT.lock().unwrap();
        *ext = Some(text.to_string());
    }
    set_clipboard_text(text)
}
