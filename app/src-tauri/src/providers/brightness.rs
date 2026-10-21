#[cfg(windows)]
pub fn set_display_brightness(brightness: u32) {
    let b = brightness.clamp(0, 100);

    // 1. Try native Win32 internal display IOCTL first (zero process overhead)
    if set_internal_lcd_brightness(b as u8) {
        return;
    }

    // 2. Try external desktop monitors via DXVA2 DDC/CI
    if set_physical_monitors_brightness(b) {
        return;
    }

    // 3. Fallback to WMI only if native direct methods did not handle the display
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
fn set_internal_lcd_brightness(brightness: u8) -> bool {
    use windows::Win32::Foundation::{CloseHandle, GENERIC_READ, GENERIC_WRITE};
    use windows::Win32::Storage::FileSystem::{CreateFileW, FILE_SHARE_READ, FILE_SHARE_WRITE, OPEN_EXISTING};
    use windows::Win32::System::IO::DeviceIoControl;
    use windows::core::w;

    const IOCTL_VIDEO_SET_DISPLAY_BRIGHTNESS: u32 = 0x0023049C;

    #[repr(C)]
    struct DisplayBrightness {
        uc_display_policy: u8,
        uc_ac_brightness: u8,
        uc_dc_brightness: u8,
    }

    unsafe {
        let handle = CreateFileW(
            w!("\\\\.\\LCD"),
            GENERIC_READ.0 | GENERIC_WRITE.0,
            FILE_SHARE_READ | FILE_SHARE_WRITE,
            None,
            OPEN_EXISTING,
            windows::Win32::Storage::FileSystem::FILE_FLAGS_AND_ATTRIBUTES(0),
            None,
        );

        if let Ok(h) = handle {
            if !h.is_invalid() {
                let params = DisplayBrightness {
                    uc_display_policy: 3, // Both AC and DC
                    uc_ac_brightness: brightness,
                    uc_dc_brightness: brightness,
                };
                let mut bytes_ret = 0u32;
                let ok = DeviceIoControl(
                    h,
                    IOCTL_VIDEO_SET_DISPLAY_BRIGHTNESS,
                    Some(&params as *const _ as *const std::ffi::c_void),
                    std::mem::size_of::<DisplayBrightness>() as u32,
                    None,
                    0,
                    Some(&mut bytes_ret),
                    None,
                );
                let _ = CloseHandle(h);
                return ok.is_ok();
            }
        }
    }
    false
}

#[cfg(windows)]
fn set_physical_monitors_brightness(brightness: u32) -> bool {
    unsafe {
        use windows::core::BOOL;
        use windows::Win32::Foundation::{LPARAM, RECT};
        use windows::Win32::Graphics::Gdi::{EnumDisplayMonitors, HDC, HMONITOR};

        struct MonitorContext {
            brightness: u32,
            any_success: bool,
        }

        unsafe extern "system" fn monitor_enum_proc(
            hmonitor: HMONITOR,
            _: HDC,
            _: *mut RECT,
            lparam: LPARAM,
        ) -> BOOL {
            let ctx = &mut *(lparam.0 as *mut MonitorContext);
            let brightness = ctx.brightness;

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
                            if SetMonitorBrightness(mon.h_physical_monitor, brightness).as_bool() {
                                ctx.any_success = true;
                            }
                        }
                    }
                    let _ = DestroyPhysicalMonitors(count, monitors.as_mut_ptr());
                }
            }
            true.into()
        }

        let mut ctx = MonitorContext {
            brightness,
            any_success: false,
        };
        let _ = EnumDisplayMonitors(None, None, Some(monitor_enum_proc), LPARAM(&mut ctx as *mut _ as isize));
        ctx.any_success
    }
}
