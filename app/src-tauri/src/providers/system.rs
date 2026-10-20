use std::sync::Mutex;
use std::time::Duration;
use serde::{Deserialize, Serialize};
use tauri::{AppHandle, Emitter};

pub const SYSTEM_EVENT: &str = "jarvis://system-update";

#[derive(Debug, Clone, Serialize, Deserialize, Default)]
#[serde(rename_all = "camelCase")]
pub struct SystemTelemetry {
    pub cpu_percent: f32,
    pub ram_used_mb: u64,
    pub ram_total_mb: u64,
    pub battery_percent: Option<u8>,
    pub is_charging: bool,
    pub has_battery: bool,
    pub disk_free_gb: Option<u64>,
    pub disk_total_gb: Option<u64>,
    pub net_download_kbps: Option<f32>,
    pub net_upload_kbps: Option<f32>,
}

static CURRENT_TELEMETRY: Mutex<Option<SystemTelemetry>> = Mutex::new(None);

pub fn get_telemetry() -> SystemTelemetry {
    if let Some(t) = CURRENT_TELEMETRY.lock().unwrap().as_ref() {
        return t.clone();
    }
    sample_system_telemetry(None, None, None, 0, 0, std::time::Instant::now()).0
}

#[cfg(windows)]
fn sample_system_telemetry(
    prev_idle: Option<windows::Win32::Foundation::FILETIME>,
    prev_kernel: Option<windows::Win32::Foundation::FILETIME>,
    prev_user: Option<windows::Win32::Foundation::FILETIME>,
    prev_net_in: u64,
    prev_net_out: u64,
    prev_net_time: std::time::Instant,
) -> (SystemTelemetry, windows::Win32::Foundation::FILETIME, windows::Win32::Foundation::FILETIME, windows::Win32::Foundation::FILETIME, u64, u64, std::time::Instant) {
    use windows::core::w;
    use windows::Win32::Foundation::FILETIME;
    use windows::Win32::NetworkManagement::IpHelper::{FreeMibTable, GetIfTable2, MIB_IF_TABLE2};
    use windows::Win32::Storage::FileSystem::GetDiskFreeSpaceExW;
    use windows::Win32::System::Power::{GetSystemPowerStatus, SYSTEM_POWER_STATUS};
    use windows::Win32::System::SystemInformation::{GlobalMemoryStatusEx, MEMORYSTATUSEX};
    use windows::Win32::System::Threading::GetSystemTimes;

    let mut telemetry = SystemTelemetry::default();

    // 1. RAM Memory
    unsafe {
        let mut mem = MEMORYSTATUSEX {
            dwLength: std::mem::size_of::<MEMORYSTATUSEX>() as u32,
            ..Default::default()
        };
        if GlobalMemoryStatusEx(&mut mem).is_ok() {
            let total = mem.ullTotalPhys / (1024 * 1024);
            let avail = mem.ullAvailPhys / (1024 * 1024);
            telemetry.ram_total_mb = total;
            telemetry.ram_used_mb = total.saturating_sub(avail);
        }
    }

    // 2. Battery & Power
    unsafe {
        let mut sps = SYSTEM_POWER_STATUS::default();
        if GetSystemPowerStatus(&mut sps).is_ok() {
            telemetry.has_battery = sps.BatteryFlag != 128 && sps.BatteryFlag != 255;
            telemetry.is_charging = sps.ACLineStatus == 1;
            if sps.BatteryLifePercent != 255 {
                telemetry.battery_percent = Some(sps.BatteryLifePercent.min(100));
            }
        }
    }

    // 3. CPU Percent
    let mut idle = FILETIME::default();
    let mut kernel = FILETIME::default();
    let mut user = FILETIME::default();

    unsafe {
        if GetSystemTimes(Some(&mut idle), Some(&mut kernel), Some(&mut user)).is_ok() {
            if let (Some(pi), Some(pk), Some(pu)) = (prev_idle, prev_kernel, prev_user) {
                let idle_delta = filetime_diff(pi, idle);
                let kernel_delta = filetime_diff(pk, kernel);
                let user_delta = filetime_diff(pu, user);

                let total_delta = kernel_delta + user_delta;
                if total_delta > 0 {
                    let busy_delta = total_delta.saturating_sub(idle_delta);
                    let pct = ((busy_delta as f32 / total_delta as f32) * 100.0).clamp(0.0, 100.0);
                    telemetry.cpu_percent = if pct.is_nan() { 0.0 } else { pct };
                }
            }
        }
    }

    // 4. Primary Drive (C:\) Storage
    unsafe {
        let mut free_bytes_avail = 0u64;
        let mut total_bytes = 0u64;
        let mut total_free = 0u64;
        if GetDiskFreeSpaceExW(
            w!("C:\\"),
            Some(&mut free_bytes_avail),
            Some(&mut total_bytes),
            Some(&mut total_free),
        ).is_ok() {
            telemetry.disk_free_gb = Some(free_bytes_avail / (1024 * 1024 * 1024));
            telemetry.disk_total_gb = Some(total_bytes / (1024 * 1024 * 1024));
        }
    }

    // 5. Network I/O Speed
    let mut cur_net_in = prev_net_in;
    let mut cur_net_out = prev_net_out;
    let now = std::time::Instant::now();

    unsafe {
        let mut table_ptr: *mut MIB_IF_TABLE2 = std::ptr::null_mut();
        if GetIfTable2(&mut table_ptr).is_ok() && !table_ptr.is_null() {
            let table = &*table_ptr;
            let mut total_in = 0u64;
            let mut total_out = 0u64;
            let entries = std::slice::from_raw_parts(table.Table.as_ptr(), table.NumEntries as usize);
            for entry in entries {
                if entry.Type != 24 {
                    total_in += entry.InOctets;
                    total_out += entry.OutOctets;
                }
            }
            let _ = FreeMibTable(table_ptr as *const _);

            let elapsed = now.duration_since(prev_net_time).as_secs_f32();
            if elapsed > 0.0 && prev_net_in > 0 {
                let in_bytes = total_in.saturating_sub(prev_net_in);
                let out_bytes = total_out.saturating_sub(prev_net_out);
                let down = (in_bytes as f32 / 1024.0) / elapsed;
                let up = (out_bytes as f32 / 1024.0) / elapsed;
                telemetry.net_download_kbps = Some(if down.is_finite() { down } else { 0.0 });
                telemetry.net_upload_kbps = Some(if up.is_finite() { up } else { 0.0 });
            }
            cur_net_in = total_in;
            cur_net_out = total_out;
        }
    }

    (telemetry, idle, kernel, user, cur_net_in, cur_net_out, now)
}

#[cfg(windows)]
pub fn start_system_service(app: AppHandle) {
    std::thread::spawn(move || {
        use windows::Win32::System::Threading::GetSystemTimes;

        // Warm up initial CPU and Network baseline
        let mut prev_idle = windows::Win32::Foundation::FILETIME::default();
        let mut prev_kernel = windows::Win32::Foundation::FILETIME::default();
        let mut prev_user = windows::Win32::Foundation::FILETIME::default();
        unsafe {
            let _ = GetSystemTimes(Some(&mut prev_idle), Some(&mut prev_kernel), Some(&mut prev_user));
        }

        let mut prev_net_in = 0u64;
        let mut prev_net_out = 0u64;
        let mut prev_net_time = std::time::Instant::now();

        // Immediate first sample so memory, disk, and battery are available right away
        let (first_sample, i, k, u, ni, no, nt) = sample_system_telemetry(
            Some(prev_idle),
            Some(prev_kernel),
            Some(prev_user),
            prev_net_in,
            prev_net_out,
            prev_net_time,
        );
        *CURRENT_TELEMETRY.lock().unwrap() = Some(first_sample.clone());
        let _ = app.emit(SYSTEM_EVENT, &first_sample);

        prev_idle = i;
        prev_kernel = k;
        prev_user = u;
        prev_net_in = ni;
        prev_net_out = no;
        prev_net_time = nt;

        loop {
            std::thread::sleep(Duration::from_millis(1500));

            let (sample, i, k, u, ni, no, nt) = sample_system_telemetry(
                Some(prev_idle),
                Some(prev_kernel),
                Some(prev_user),
                prev_net_in,
                prev_net_out,
                prev_net_time,
            );

            *CURRENT_TELEMETRY.lock().unwrap() = Some(sample.clone());
            let _ = app.emit(SYSTEM_EVENT, &sample);

            prev_idle = i;
            prev_kernel = k;
            prev_user = u;
            prev_net_in = ni;
            prev_net_out = no;
            prev_net_time = nt;
        }
    });
}

#[cfg(not(windows))]
pub fn start_system_service(_app: AppHandle) {}

#[cfg(windows)]
fn filetime_diff(prev: windows::Win32::Foundation::FILETIME, curr: windows::Win32::Foundation::FILETIME) -> u64 {
    let p = ((prev.dwHighDateTime as u64) << 32) | (prev.dwLowDateTime as u64);
    let c = ((curr.dwHighDateTime as u64) << 32) | (curr.dwLowDateTime as u64);
    c.saturating_sub(p)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_system_telemetry_gathering() {
        let t = get_telemetry();
        println!("Telemetry sampled: RAM total: {}MB, used: {}MB", t.ram_total_mb, t.ram_used_mb);
        assert!(t.ram_total_mb > 0);
    }
}
