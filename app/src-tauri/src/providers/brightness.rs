#[cfg(windows)]
pub fn set_display_brightness(brightness: u32) {
    let b = brightness.clamp(0, 100);

    // 1. Try external desktop monitors via DXVA2 DDC/CI
    set_physical_monitors_brightness(b);

    // 2. Try laptop internal display via WMI WmiMonitorBrightnessMethods
    std::thread::spawn(move || {
        use std::os::windows::process::CommandExt;
        const CREATE_NO_WINDOW: u32 = 0x08000000;
        let script = format!(
            "(Get-WmiObject -Namespace root/WMI -Class WmiMonitorBrightnessMethods).WmiSetBrightness(1, {})",
            b
        );
        let _ = std::process::Command::new("powershell")
            .args(["-NoProfile", "-WindowStyle", "Hidden", "-Command", &script])
            .creation_flags(CREATE_NO_WINDOW)
            .status();
    });
}

#[cfg(not(windows))]
pub fn set_display_brightness(_brightness: u32) {}

#[cfg(windows)]
fn set_physical_monitors_brightness(brightness: u32) {
    unsafe {
        use windows::core::BOOL;
        use windows::Win32::Foundation::{LPARAM, RECT};
        use windows::Win32::Graphics::Gdi::{EnumDisplayMonitors, HDC, HMONITOR};

        unsafe extern "system" fn monitor_enum_proc(
            hmonitor: HMONITOR,
            _: HDC,
            _: *mut RECT,
            lparam: LPARAM,
        ) -> BOOL {
            let brightness = lparam.0 as u32;

            #[repr(C)]
            #[derive(Clone, Copy)]
            struct PHYSICAL_MONITOR {
                h_physical_monitor: usize,
                sz_physical_monitor_description: [u16; 128],
            }

            #[link(name = "dxva2")]
            extern "system" {
                fn GetNumberOfPhysicalMonitorsFromHMONITOR(
                    hMonitor: HMONITOR,
                    pdwNumberOfPhysicalMonitors: *mut u32,
                ) -> BOOL;
                fn GetPhysicalMonitorsFromHMONITOR(
                    hMonitor: HMONITOR,
                    dwPhysicalMonitorArraySize: u32,
                    pPhysicalMonitorArray: *mut PHYSICAL_MONITOR,
                ) -> BOOL;
                fn DestroyPhysicalMonitors(
                    dwPhysicalMonitorArraySize: u32,
                    pPhysicalMonitorArray: *mut PHYSICAL_MONITOR,
                ) -> BOOL;
                fn SetMonitorBrightness(hMonitor: usize, dwNewBrightness: u32) -> BOOL;
            }

            let mut count = 0u32;
            if GetNumberOfPhysicalMonitorsFromHMONITOR(hmonitor, &mut count).as_bool() && count > 0 {
                let mut monitors = vec![std::mem::zeroed::<PHYSICAL_MONITOR>(); count as usize];
                if GetPhysicalMonitorsFromHMONITOR(hmonitor, count, monitors.as_mut_ptr()).as_bool() {
                    for mon in &monitors {
                        if mon.h_physical_monitor != 0 {
                            let _ = SetMonitorBrightness(mon.h_physical_monitor, brightness);
                        }
                    }
                    let _ = DestroyPhysicalMonitors(count, monitors.as_mut_ptr());
                }
            }
            true.into()
        }

        let _ = EnumDisplayMonitors(None, None, Some(monitor_enum_proc), LPARAM(brightness as isize));
    }
}
