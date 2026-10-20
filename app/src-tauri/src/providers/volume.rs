use std::time::Duration;
use serde::{Deserialize, Serialize};
use tauri::{AppHandle, Emitter};

pub const VOLUME_EVENT: &str = "jarvis://volume-update";

#[derive(Debug, Clone, Serialize, Deserialize, Default)]
#[serde(rename_all = "camelCase")]
pub struct VolumeState {
    pub volume: f32, // 0.0 to 1.0
    pub is_muted: bool,
}

#[cfg(windows)]
pub fn start_volume_service(app: AppHandle) {
    std::thread::spawn(move || {
        use windows::Win32::System::Com::{CoInitializeEx, COINIT_MULTITHREADED};

        unsafe {
            let _ = CoInitializeEx(None, COINIT_MULTITHREADED);
        }

        let mut last_vol: f32 = -1.0;
        let mut last_muted: bool = false;

        loop {
            let (vol, muted) = get_current_volume().unwrap_or((0.5, false));

            if (vol - last_vol).abs() > 0.01 || muted != last_muted {
                last_vol = vol;
                last_muted = muted;
                let state = VolumeState {
                    volume: vol,
                    is_muted: muted,
                };
                let _ = app.emit(VOLUME_EVENT, &state);
            }

            std::thread::sleep(Duration::from_millis(300));
        }
    });
}

#[cfg(not(windows))]
pub fn start_volume_service(_app: AppHandle) {}

#[cfg(windows)]
pub fn set_master_volume(level: f32) -> Result<(), String> {
    use windows::Win32::System::Com::{CoCreateInstance, CoInitializeEx, CLSCTX_ALL, COINIT_MULTITHREADED};
    use windows::Win32::Media::Audio::Endpoints::IAudioEndpointVolume;
    use windows::Win32::Media::Audio::{MMDeviceEnumerator, IMMDeviceEnumerator, eRender, eConsole};

    unsafe {
        let _ = CoInitializeEx(None, COINIT_MULTITHREADED);
        let enumerator: IMMDeviceEnumerator = CoCreateInstance(&MMDeviceEnumerator, None, CLSCTX_ALL)
            .map_err(|e| e.to_string())?;

        let device = enumerator.GetDefaultAudioEndpoint(eRender, eConsole)
            .map_err(|e| e.to_string())?;

        let endpoint_vol: IAudioEndpointVolume = device.Activate(CLSCTX_ALL, None)
            .map_err(|e| e.to_string())?;

        endpoint_vol.SetMasterVolumeLevelScalar(level.clamp(0.0, 1.0), std::ptr::null())
            .map_err(|e| e.to_string())?;

        Ok(())
    }
}

#[cfg(not(windows))]
pub fn set_master_volume(_level: f32) -> Result<(), String> {
    Ok(())
}

#[cfg(windows)]
fn get_current_volume() -> Result<(f32, bool), String> {
    use windows::Win32::System::Com::{CoCreateInstance, CLSCTX_ALL};
    use windows::Win32::Media::Audio::Endpoints::IAudioEndpointVolume;
    use windows::Win32::Media::Audio::{MMDeviceEnumerator, IMMDeviceEnumerator, eRender, eConsole};

    unsafe {
        let enumerator: IMMDeviceEnumerator = CoCreateInstance(&MMDeviceEnumerator, None, CLSCTX_ALL)
            .map_err(|e| e.to_string())?;

        let device = enumerator.GetDefaultAudioEndpoint(eRender, eConsole)
            .map_err(|e| e.to_string())?;

        let endpoint_vol: IAudioEndpointVolume = device.Activate(CLSCTX_ALL, None)
            .map_err(|e| e.to_string())?;

        let level = endpoint_vol.GetMasterVolumeLevelScalar().map_err(|e| e.to_string())?;
        let muted = endpoint_vol.GetMute().map_err(|e| e.to_string())?.as_bool();

        Ok((level, muted))
    }
}
