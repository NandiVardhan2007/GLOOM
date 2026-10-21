use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Mutex;
use std::time::Duration;
use serde::{Deserialize, Serialize};
use tauri::{AppHandle, Emitter, Manager, WebviewWindow};

pub const ISLAND_LABEL: &str = "island";
pub const HOVER_EVENT: &str = "jarvis://hover";

#[derive(Debug, Clone, Copy, Serialize, Deserialize, Default)]
pub struct HitRect {
    pub x: f64,
    pub y: f64,
    pub width: f64,
    pub height: f64,
}

static HIT_RECTS: Mutex<Vec<HitRect>> = Mutex::new(Vec::new());
static HOVERING: AtomicBool = AtomicBool::new(false);

pub fn update_hit_rects(rects: Vec<HitRect>) {
    let mut lock = HIT_RECTS.lock().unwrap();
    *lock = rects;
}

#[cfg(windows)]
pub fn make_overlay(win: &WebviewWindow) {
    use windows::Win32::Foundation::HWND;
    use windows::Win32::UI::WindowsAndMessaging::{
        GetWindowLongW, SetWindowLongW, SetWindowPos, GWL_EXSTYLE, HWND_TOPMOST,
        SWP_NOACTIVATE, SWP_NOMOVE, SWP_NOSIZE, SWP_SHOWWINDOW, WS_EX_LAYERED,
        WS_EX_NOACTIVATE, WS_EX_TOOLWINDOW,
    };

    if let Ok(hwnd) = win.hwnd() {
        let hwnd = HWND(hwnd.0);
        unsafe {
            let current_style = GetWindowLongW(hwnd, GWL_EXSTYLE) as u32;
            let overlay_style = current_style | WS_EX_TOOLWINDOW.0 | WS_EX_NOACTIVATE.0 | WS_EX_LAYERED.0;
            SetWindowLongW(hwnd, GWL_EXSTYLE, overlay_style as i32);
            let _ = SetWindowPos(
                hwnd,
                Some(HWND_TOPMOST),
                0,
                0,
                0,
                0,
                SWP_NOMOVE | SWP_NOSIZE | SWP_NOACTIVATE | SWP_SHOWWINDOW,
            );
        }
    }
}

#[cfg(not(windows))]
pub fn make_overlay(_win: &WebviewWindow) {}

pub fn start_pointer_watcher(app: AppHandle) {
    std::thread::spawn(move || {
        let mut ignoring: Option<bool> = None;
        let mut hovering = false;

        loop {
            let Some(win) = app.get_webview_window(ISLAND_LABEL) else {
                std::thread::sleep(Duration::from_millis(200));
                continue;
            };

            let (cursor_x, cursor_y) = get_cursor_pos();
            let (win_x, win_y, scale) = if let Ok(pos) = win.outer_position() {
                let scale = win.scale_factor().unwrap_or(1.0);
                (pos.x as f64 / scale, pos.y as f64 / scale, scale)
            } else {
                (0.0, 0.0, 1.0)
            };

            let rel_x = (cursor_x as f64 / scale) - win_x;
            let rel_y = (cursor_y as f64 / scale) - win_y;

            // Check if cursor is inside any registered interactive rect
            let rects = HIT_RECTS.lock().unwrap().clone();
            const PADDING: f64 = 8.0;

            let mut inside = false;
            for r in &rects {
                if rel_x >= (r.x - PADDING)
                    && rel_x <= (r.x + r.width + PADDING)
                    && rel_y >= (r.y - PADDING)
                    && rel_y <= (r.y + r.height + PADDING)
                {
                    inside = true;
                    break;
                }
            }

            let ignore = !inside;
            if ignoring != Some(ignore) {
                if win.set_ignore_cursor_events(ignore).is_ok() {
                    ignoring = Some(ignore);
                }
            }

            if inside != hovering {
                hovering = inside;
                HOVERING.store(inside, Ordering::Relaxed);
                let _ = app.emit(HOVER_EVENT, inside);
            }

            // Dual-rate sampling: 60Hz when near top (y < 120), 8Hz when idle/far away
            let sleep_ms = if rel_y < 120.0 || inside { 16 } else { 125 };
            std::thread::sleep(Duration::from_millis(sleep_ms));
        }
    });
}

#[cfg(windows)]
fn get_cursor_pos() -> (i32, i32) {
    use windows::Win32::Foundation::POINT;
    use windows::Win32::UI::WindowsAndMessaging::GetCursorPos;

    let mut pt = POINT::default();
    unsafe {
        let _ = GetCursorPos(&mut pt);
    }
    (pt.x, pt.y)
}

#[cfg(not(windows))]
fn get_cursor_pos() -> (i32, i32) {
    (0, 0)
}

#[tauri::command]
pub fn jarvis_focus_window(app: AppHandle) {
    if let Some(win) = app.get_webview_window(ISLAND_LABEL) {
        #[cfg(windows)]
        {
            use windows::Win32::Foundation::HWND;
            use windows::Win32::UI::WindowsAndMessaging::{
                GetWindowLongW, SetForegroundWindow, SetWindowLongW, SetWindowPos, GWL_EXSTYLE,
                SWP_FRAMECHANGED, SWP_NOMOVE, SWP_NOSIZE, SWP_NOZORDER, WS_EX_NOACTIVATE,
            };
            if let Ok(hwnd) = win.hwnd() {
                let hwnd = HWND(hwnd.0);
                unsafe {
                    let ex = GetWindowLongW(hwnd, GWL_EXSTYLE);
                    if (ex & (WS_EX_NOACTIVATE.0 as i32)) != 0 {
                        SetWindowLongW(hwnd, GWL_EXSTYLE, ex & !(WS_EX_NOACTIVATE.0 as i32));
                        let _ = SetWindowPos(
                            hwnd,
                            None,
                            0,
                            0,
                            0,
                            0,
                            SWP_NOMOVE | SWP_NOSIZE | SWP_NOZORDER | SWP_FRAMECHANGED,
                        );
                    }
                    let _ = SetForegroundWindow(hwnd);
                }
            }
        }
        let _ = win.set_focus();
    }
}

#[tauri::command]
pub fn jarvis_unfocus_window(app: AppHandle) {
    if let Some(win) = app.get_webview_window(ISLAND_LABEL) {
        #[cfg(windows)]
        {
            use windows::Win32::Foundation::HWND;
            use windows::Win32::UI::WindowsAndMessaging::{
                GetWindowLongW, SetWindowLongW, SetWindowPos, GWL_EXSTYLE,
                SWP_FRAMECHANGED, SWP_NOMOVE, SWP_NOSIZE, SWP_NOZORDER, WS_EX_NOACTIVATE,
            };
            if let Ok(hwnd) = win.hwnd() {
                let hwnd = HWND(hwnd.0);
                unsafe {
                    let ex = GetWindowLongW(hwnd, GWL_EXSTYLE);
                    if (ex & (WS_EX_NOACTIVATE.0 as i32)) == 0 {
                        SetWindowLongW(hwnd, GWL_EXSTYLE, ex | (WS_EX_NOACTIVATE.0 as i32));
                        let _ = SetWindowPos(
                            hwnd,
                            None,
                            0,
                            0,
                            0,
                            0,
                            SWP_NOMOVE | SWP_NOSIZE | SWP_NOZORDER | SWP_FRAMECHANGED,
                        );
                    }
                }
            }
        }
    }
}
