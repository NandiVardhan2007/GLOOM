#[cfg(windows)]
use windows::Win32::Foundation::RECT;
#[cfg(windows)]
use windows::Win32::Graphics::Dwm::{DwmGetWindowAttribute, DWMWA_CLOAKED, DWMWA_EXTENDED_FRAME_BOUNDS};
#[cfg(windows)]
use windows::Win32::Graphics::Gdi::{GetMonitorInfoW, MonitorFromWindow, MONITORINFO, MONITOR_DEFAULTTONEAREST};
#[cfg(windows)]
use windows::Win32::UI::WindowsAndMessaging::{
    GetClassNameW, GetForegroundWindow, GetWindowLongW, GetWindowPlacement, GetWindowRect, IsIconic,
    IsWindowVisible, GWL_STYLE, SW_SHOWMAXIMIZED, WINDOWPLACEMENT, WS_CAPTION, WS_THICKFRAME,
};

#[cfg(windows)]
pub fn is_foreground_fullscreen() -> bool {
    unsafe {
        let fg = GetForegroundWindow();
        if fg.is_invalid() || !IsWindowVisible(fg).as_bool() || IsIconic(fg).as_bool() {
            return false;
        }

        // Check cloaked state
        let mut cloaked: u32 = 0;
        let _ = DwmGetWindowAttribute(
            fg,
            DWMWA_CLOAKED,
            &mut cloaked as *mut _ as *mut _,
            std::mem::size_of::<u32>() as u32,
        );
        if cloaked != 0 {
            return false;
        }

        // Check blocked classes
        let mut class_buf = [0u16; 128];
        let len = GetClassNameW(fg, &mut class_buf);
        if len > 0 {
            let class_name = String::from_utf16_lossy(&class_buf[..len as usize]);
            if matches!(
                class_name.as_str(),
                "Progman"
                    | "WorkerW"
                    | "Shell_TrayWnd"
                    | "Shell_SecondaryTrayWnd"
                    | "TaskSwitcherWnd"
                    | "Windows.UI.Core.CoreWindow"
            ) {
                return false;
            }
        }

        // Get window bounds (extended frame bounds preferred)
        let mut rect = RECT::default();
        if DwmGetWindowAttribute(
            fg,
            DWMWA_EXTENDED_FRAME_BOUNDS,
            &mut rect as *mut _ as *mut _,
            std::mem::size_of::<RECT>() as u32,
        ).is_err() {
            let _ = GetWindowRect(fg, &mut rect);
        }

        let monitor = MonitorFromWindow(fg, MONITOR_DEFAULTTONEAREST);
        if monitor.is_invalid() {
            return false;
        }

        let mut mi = MONITORINFO {
            cbSize: std::mem::size_of::<MONITORINFO>() as u32,
            ..Default::default()
        };
        if !GetMonitorInfoW(monitor, &mut mi).as_bool() {
            return false;
        }

        let mon_rect = mi.rcMonitor;
        const TOLERANCE: i32 = 8;

        let covers_mon = rect.left <= mon_rect.left + TOLERANCE
            && rect.top <= mon_rect.top + TOLERANCE
            && rect.right >= mon_rect.right - TOLERANCE
            && rect.bottom >= mon_rect.bottom - TOLERANCE;

        if !covers_mon {
            return false;
        }

        let style = GetWindowLongW(fg, GWL_STYLE);
        let has_caption = (style as u32 & WS_CAPTION.0) == WS_CAPTION.0;
        let has_thick = (style as u32 & WS_THICKFRAME.0) != 0;

        let mut wp = WINDOWPLACEMENT {
            length: std::mem::size_of::<WINDOWPLACEMENT>() as u32,
            ..Default::default()
        };
        let is_maximized = GetWindowPlacement(fg, &mut wp).is_ok()
            && wp.showCmd as i32 == SW_SHOWMAXIMIZED.0 as i32;

        if has_caption || has_thick {
            if is_maximized {
                // Maximized window with caption (not fullscreen)
                return false;
            }
        }

        true
    }
}

#[cfg(not(windows))]
pub fn is_foreground_fullscreen() -> bool {
    false
}
