use std::collections::{HashMap, HashSet, VecDeque};
use std::io::{Read, Write};
use std::net::{TcpListener, UdpSocket};
use std::path::{Path, PathBuf};
use std::sync::{Arc, Mutex};
use std::time::{Duration, SystemTime, UNIX_EPOCH};
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use tauri::{AppHandle, Emitter};
use crate::protocol::{ProtocolEngine, ConnectionStatusPayload, DevicePresencePayload};

pub const BRIDGE_DEVICE_EVENT: &str = "jarvis://bridge-devices";
pub const BRIDGE_ACTIVITY_EVENT: &str = "jarvis://bridge-activity";
pub const BRIDGE_TRANSFERS_EVENT: &str = "jarvis://bridge-transfers";
pub const BRIDGE_TRANSFER_LEGACY_EVENT: &str = "jarvis://bridge-transfer";
pub const BRIDGE_STATUS_EVENT: &str = "jarvis://bridge-status";
pub const BRIDGE_PAIRING_EVENT: &str = "jarvis://bridge-pairing-request";

pub const MAX_JSON_PAYLOAD_SIZE: usize = 1024 * 1024; // 1 MB limit for control/clipboard/telemetry
pub const MAX_FILE_TRANSFER_SIZE: u64 = 2 * 1024 * 1024 * 1024; // 2 GB limit for file transfers

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct BridgeDevice {
    pub id: String,
    pub name: String,
    pub device_type: String, // "android" | "ios" | "harmony"
    pub ip: String,
    pub port: u16,
    pub battery_percent: u8,
    pub is_charging: bool,
    pub wifi_ssid: String,
    pub status: String, // "connected" | "syncing" | "idle"
    pub last_seen_ms: u64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct DevicePermissions {
    pub status: bool,
    pub clipboard: bool,
    pub file_transfer: bool,
    pub remote_control: bool,
}

impl Default for DevicePermissions {
    fn default() -> Self {
        Self {
            status: true,
            clipboard: true,
            file_transfer: true,
            remote_control: false, // Opt-in required for remote control
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct PairedDeviceRecord {
    pub device_id: String,
    pub device_name: String,
    pub auth_token: String,
    #[serde(default)]
    pub ip: String,
    #[serde(default)]
    pub peer_fingerprint: String,
    pub permissions: DevicePermissions,
    pub paired_at_ms: u64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct PairingChallenge {
    pub challenge_id: String,
    pub pin: String,
    pub client_device_id: String,
    pub client_device_name: String,
    pub expires_at_ms: u64,
    #[serde(default)]
    pub attempts: u32,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct BridgeActivity {
    pub id: String,
    pub app_name: String,
    pub title: String,
    pub subtitle: String,
    pub icon: String,
    pub activity_type: String, // "timer" | "delivery" | "call" | "otp" | "media"
    pub progress: Option<f32>,
    pub timestamp_ms: u64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct BridgeTransfer {
    pub id: String,
    pub device_name: String,
    pub file_name: String,
    pub file_size: u64,
    pub bytes_transferred: u64,
    pub is_upload: bool,
    pub progress: f32,
    pub status: String, // "transferring" | "completed" | "failed"
    pub sha256_checksum: Option<String>,
}

static DEVICES: Mutex<Vec<BridgeDevice>> = Mutex::new(Vec::new());
static TRANSFERS: Mutex<Vec<BridgeTransfer>> = Mutex::new(Vec::new());
static ACTIVITIES: Mutex<Vec<BridgeActivity>> = Mutex::new(Vec::new());
static PROTOCOL_ENGINE: Mutex<Option<ProtocolEngine>> = Mutex::new(None);

static PAIRED_DEVICES: Mutex<Option<HashMap<String, PairedDeviceRecord>>> = Mutex::new(None);
static ACTIVE_PAIRING_CHALLENGES: Mutex<Option<HashMap<String, PairingChallenge>>> = Mutex::new(None);

static SEEN_NONCES: Mutex<Option<HashSet<String>>> = Mutex::new(None);
static NONCE_QUEUE: Mutex<Option<VecDeque<(String, u64)>>> = Mutex::new(None);

pub fn with_paired_devices<F, R>(f: F) -> R
where
    F: FnOnce(&mut HashMap<String, PairedDeviceRecord>) -> R,
{
    let mut lock = PAIRED_DEVICES.lock().unwrap();
    let map = lock.get_or_insert_with(HashMap::new);
    f(map)
}

pub fn get_active_pairing_challenge() -> Option<PairingChallenge> {
    let now = current_time_ms();
    with_challenges(|challenges| {
        challenges.retain(|_, c| c.expires_at_ms > now);
        challenges
            .values()
            .max_by_key(|c| c.expires_at_ms)
            .cloned()
    })
}

pub fn with_challenges<F, R>(f: F) -> R
where
    F: FnOnce(&mut HashMap<String, PairingChallenge>) -> R,
{
    let mut lock = ACTIVE_PAIRING_CHALLENGES.lock().unwrap();
    let map = lock.get_or_insert_with(HashMap::new);
    f(map)
}

pub fn current_time_ms() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or(Duration::from_secs(0))
        .as_millis() as u64
}

fn generate_random_bytes(len: usize) -> Vec<u8> {
    let mut buf = vec![0u8; len];
    if getrandom::getrandom(&mut buf).is_err() {
        let now = current_time_ms();
        for (i, b) in buf.iter_mut().enumerate() {
            *b = ((now.wrapping_add(i as u64) ^ (now >> (i % 8))) & 0xFF) as u8;
        }
    }
    buf
}

pub fn generate_secure_token() -> String {
    let bytes = generate_random_bytes(32);
    let mut s = String::with_capacity(64);
    for b in bytes {
        use std::fmt::Write;
        let _ = write!(&mut s, "{:02x}", b);
    }
    s
}

pub fn generate_pairing_pin() -> String {
    let bytes = generate_random_bytes(4);
    let num = u32::from_be_bytes([bytes[0], bytes[1], bytes[2], bytes[3]]) % 1_000_000;
    format!("{:06}", num)
}

pub fn generate_nonce() -> String {
    let bytes = generate_random_bytes(16);
    let mut s = String::with_capacity(32);
    for b in bytes {
        use std::fmt::Write;
        let _ = write!(&mut s, "{:02x}", b);
    }
    s
}

fn paired_devices_storage_path() -> PathBuf {
    let base = crate::config::dirs_fallback().join("JARVIS");
    let _ = std::fs::create_dir_all(&base);
    base.join("paired_devices.json")
}

pub fn load_paired_devices() {
    let path = paired_devices_storage_path();
    if let Ok(data) = std::fs::read_to_string(&path) {
        if let Ok(records) = serde_json::from_str::<Vec<PairedDeviceRecord>>(&data) {
            with_paired_devices(|map| {
                map.clear();
                for r in records {
                    map.insert(r.auth_token.clone(), r);
                }
            });
        }
    }
}

pub fn save_paired_devices() {
    let path = paired_devices_storage_path();
    let records: Vec<PairedDeviceRecord> = with_paired_devices(|map| {
        map.values().cloned().collect()
    });
    if let Ok(json) = serde_json::to_string_pretty(&records) {
        let tmp = path.with_extension("tmp");
        if std::fs::write(&tmp, json).is_ok() {
            let _ = std::fs::rename(&tmp, &path);
        }
    }
}

fn master_bridge_secret() -> Option<String> {
    std::env::var("JARVIS_BRIDGE_SECRET")
        .ok()
        .map(|s| s.trim().to_string())
        .filter(|s| !s.is_empty())
}

fn paired_token_for_device_id(device_id: &str) -> Option<String> {
    with_paired_devices(|map| {
        map.values()
            .find(|record| record.device_id == device_id)
            .map(|record| record.auth_token.clone())
    })
}

pub fn is_device_paired(device_id: &str, ip: &str) -> bool {
    if master_bridge_secret().is_some() {
        return true;
    }
    with_paired_devices(|map| {
        map.values()
            .any(|record| record.device_id == device_id && (record.ip.is_empty() || record.ip == ip))
    })
}

pub fn get_bridge_token_for_device(device: &BridgeDevice) -> String {
    if let Some(secret) = master_bridge_secret() {
        return secret;
    }
    if !is_device_paired(&device.id, &device.ip) {
        return String::new();
    }
    paired_token_for_device_id(&device.id).unwrap_or_default()
}

pub fn get_bridge_token_for_ip(ip: &str) -> String {
    if let Some(secret) = master_bridge_secret() {
        return secret;
    }
    // Resolve the device id first so the two mutexes are never held together.
    let device_id = DEVICES
        .lock()
        .unwrap()
        .iter()
        .find(|device| device.ip == ip)
        .map(|device| device.id.clone());
    match device_id {
        Some(id) if is_device_paired(&id, ip) => paired_token_for_device_id(&id).unwrap_or_default(),
        _ => String::new(),
    }
}

fn peer_fingerprint_for_ip(ip: &str) -> Option<String> {
    with_paired_devices(|map| {
        map.values()
            .find(|record| record.ip == ip && !record.peer_fingerprint.is_empty())
            .map(|record| record.peer_fingerprint.clone())
    })
}

/// Opens an encrypted, certificate-pinned connection to a paired companion.
/// A device that has not completed PIN pairing has no pinned fingerprint and is
/// refused rather than being downgraded to cleartext.
fn connect_mobile_tls(
    ip: &str,
    connect_timeout: Duration,
) -> Result<rustls::StreamOwned<rustls::ClientConnection, std::net::TcpStream>, String> {
    use std::net::ToSocketAddrs;

    let fingerprint = peer_fingerprint_for_ip(ip).ok_or_else(|| {
        "Companion device has no pinned TLS identity; re-pair required".to_string()
    })?;
    let config = crate::providers::bridge_tls::pinned_client_config(&fingerprint)?;
    let addr = format!("{}:53391", ip);
    let sock = addr
        .to_socket_addrs()
        .map_err(|e| format!("Invalid companion address: {}", e))?
        .next()
        .ok_or_else(|| "Could not resolve companion address".to_string())?;
    let tcp = std::net::TcpStream::connect_timeout(&sock, connect_timeout)
        .map_err(|e| format!("Cannot connect to companion: {}", e))?;
    let _ = tcp.set_read_timeout(Some(Duration::from_secs(30)));
    let _ = tcp.set_write_timeout(Some(Duration::from_secs(30)));

    let server_name = rustls::pki_types::ServerName::try_from("jarvis-bridge")
        .map_err(|e| format!("Invalid TLS server name: {}", e))?
        .to_owned();
    let conn = rustls::ClientConnection::new(config, server_name)
        .map_err(|e| format!("Cannot start TLS session: {}", e))?;
    Ok(rustls::StreamOwned::new(conn, tcp))
}

pub fn check_and_record_nonce(nonce: &str, timestamp_ms: u64) -> Result<(), &'static str> {
    if nonce.trim().len() < 8 {
        return Err("Missing or malformed X-Jarvis-Nonce");
    }

    let now = current_time_ms();
    let diff = if now >= timestamp_ms { now - timestamp_ms } else { timestamp_ms - now };
    if diff > 60_000 {
        return Err("Request timestamp expired or skewed beyond acceptable window of 60 seconds");
    }

    let mut nonces_lock = SEEN_NONCES.lock().unwrap();
    let mut queue_lock = NONCE_QUEUE.lock().unwrap();
    let nonces = nonces_lock.get_or_insert_with(HashSet::new);
    let queue = queue_lock.get_or_insert_with(VecDeque::new);

    // Evict expired nonces (older than 65s) or if queue exceeds 10,000
    while let Some((_, ts)) = queue.front() {
        if now.saturating_sub(*ts) > 65_000 || queue.len() > 10_000 {
            if let Some((old_nonce, _)) = queue.pop_front() {
                nonces.remove(&old_nonce);
            }
        } else {
            break;
        }
    }

    if nonces.contains(nonce) {
        return Err("Replay protection: Message nonce already processed");
    }

    nonces.insert(nonce.to_string());
    queue.push_back((nonce.to_string(), timestamp_ms));
    Ok(())
}

pub fn init_protocol_engine() {
    let mut eng = PROTOCOL_ENGINE.lock().unwrap();
    if eng.is_none() {
        *eng = Some(ProtocolEngine::new(
            "jarvis-desktop-primary".to_string(),
            "JARVIS Windows Desktop".to_string(),
        ));
    }
}

pub fn get_connection_status() -> ConnectionStatusPayload {
    init_protocol_engine();
    let eng = PROTOCOL_ENGINE.lock().unwrap();
    eng.as_ref().unwrap().get_status()
}

#[cfg(windows)]
pub fn handle_remote_mouse_action(action: &str, dx: f32, dy: f32, button: &str) {
    use windows::Win32::UI::Input::KeyboardAndMouse::{
        mouse_event, MOUSEEVENTF_LEFTDOWN, MOUSEEVENTF_LEFTUP, MOUSEEVENTF_MOVE,
        MOUSEEVENTF_RIGHTDOWN, MOUSEEVENTF_RIGHTUP, MOUSEEVENTF_MIDDLEDOWN, MOUSEEVENTF_MIDDLEUP,
        MOUSEEVENTF_WHEEL,
    };

    unsafe {
        match action {
            "move" => {
                let sensitivity = 1.75f32;
                let move_x = (dx * sensitivity).round() as i32;
                let move_y = (dy * sensitivity).round() as i32;
                if move_x != 0 || move_y != 0 {
                    mouse_event(MOUSEEVENTF_MOVE, move_x, move_y, 0, 0);
                }
            }
            "click" => {
                match button {
                    "right" => {
                        mouse_event(MOUSEEVENTF_RIGHTDOWN, 0, 0, 0, 0);
                        mouse_event(MOUSEEVENTF_RIGHTUP, 0, 0, 0, 0);
                    }
                    "middle" => {
                        mouse_event(MOUSEEVENTF_MIDDLEDOWN, 0, 0, 0, 0);
                        mouse_event(MOUSEEVENTF_MIDDLEUP, 0, 0, 0, 0);
                    }
                    _ => {
                        mouse_event(MOUSEEVENTF_LEFTDOWN, 0, 0, 0, 0);
                        mouse_event(MOUSEEVENTF_LEFTUP, 0, 0, 0, 0);
                    }
                }
            }
            "double_click" => {
                mouse_event(MOUSEEVENTF_LEFTDOWN, 0, 0, 0, 0);
                mouse_event(MOUSEEVENTF_LEFTUP, 0, 0, 0, 0);
                std::thread::sleep(Duration::from_millis(40));
                mouse_event(MOUSEEVENTF_LEFTDOWN, 0, 0, 0, 0);
                mouse_event(MOUSEEVENTF_LEFTUP, 0, 0, 0, 0);
            }
            "down" => {
                if button == "right" {
                    mouse_event(MOUSEEVENTF_RIGHTDOWN, 0, 0, 0, 0);
                } else {
                    mouse_event(MOUSEEVENTF_LEFTDOWN, 0, 0, 0, 0);
                }
            }
            "up" => {
                if button == "right" {
                    mouse_event(MOUSEEVENTF_RIGHTUP, 0, 0, 0, 0);
                } else {
                    mouse_event(MOUSEEVENTF_LEFTUP, 0, 0, 0, 0);
                }
            }
            "scroll" => {
                let wheel_delta = (dy * 24.0).round() as i32;
                if wheel_delta != 0 {
                    mouse_event(MOUSEEVENTF_WHEEL, 0, 0, wheel_delta, 0);
                }
            }
            _ => {}
        }
    }
}

#[cfg(not(windows))]
pub fn handle_remote_mouse_action(_action: &str, _dx: f32, _dy: f32, _button: &str) {}

pub fn get_paired_devices() -> Vec<BridgeDevice> {
    let now = current_time_ms();
    let mut devs = DEVICES.lock().unwrap();
    // Filter out stale devices that haven't sent a beacon in > 45 seconds
    devs.retain(|d| now.saturating_sub(d.last_seen_ms) < 45_000);
    devs.clone()
}

pub fn sanitize_filename(raw: &str) -> String {
    let normalized = raw.replace('\\', "/");
    let base = Path::new(&normalized)
        .file_name()
        .and_then(|n| n.to_str())
        .unwrap_or("unnamed_transfer.bin");
    let safe: String = base
        .chars()
        .filter(|c| c.is_alphanumeric() || " ._-()[]".contains(*c))
        .collect();
    let trimmed = safe.trim();
    if trimmed.is_empty() || trimmed.starts_with('.') {
        "unnamed_transfer.bin".to_string()
    } else {
        let stem = Path::new(trimmed)
            .file_stem()
            .and_then(|s| s.to_str())
            .unwrap_or("")
            .to_ascii_uppercase();
        let reserved = [
            "CON", "PRN", "AUX", "NUL", "COM1", "COM2", "COM3", "COM4", "COM5", "COM6",
            "COM7", "COM8", "COM9", "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7",
            "LPT8", "LPT9",
        ];
        if reserved.contains(&stem.as_str()) {
            format!("file_{}", trimmed)
        } else {
            trimmed.to_string()
        }
    }
}

pub fn get_safe_destination_path(download_dir: &Path, raw_name: &str) -> (PathBuf, String, bool) {
    let clean = sanitize_filename(raw_name);
    let mut file_path = download_dir.join(&clean);
    let mut final_name = clean.clone();

    // Dangerous executable extensions require renaming with .downloaded safety suffix
    let lower = clean.to_ascii_lowercase();
    let dangerous_exts = [".exe", ".bat", ".cmd", ".lnk", ".ps1", ".vbs", ".sh", ".com", ".scr", ".msi"];
    let is_executable = dangerous_exts.iter().any(|ext| lower.ends_with(ext));

    if is_executable {
        final_name = format!("{}.downloaded", clean);
        file_path = download_dir.join(&final_name);
    }

    // Collision avoidance: append (1), (2), etc.
    if file_path.exists() {
        let stem = Path::new(&final_name)
            .file_stem()
            .and_then(|s| s.to_str())
            .unwrap_or("file");
        let ext = Path::new(&final_name)
            .extension()
            .and_then(|e| e.to_str())
            .map(|e| format!(".{}", e))
            .unwrap_or_default();

        let mut counter = 1;
        loop {
            let candidate_name = format!("{} ({}){}", stem, counter, ext);
            let candidate_path = download_dir.join(&candidate_name);
            if !candidate_path.exists() {
                file_path = candidate_path;
                final_name = candidate_name;
                break;
            }
            counter += 1;
        }
    }

    (file_path, final_name, is_executable)
}

pub fn get_jarvis_download_dir() -> PathBuf {
    let base = std::env::var("USERPROFILE")
        .map(|p| PathBuf::from(p).join("Downloads").join("JARVIS"))
        .unwrap_or_else(|_| std::env::temp_dir().join("JARVIS_Downloads"));
    let _ = std::fs::create_dir_all(&base);
    base
}

pub fn open_transfer_folder() {
    let path = get_jarvis_download_dir();
    #[cfg(windows)]
    let _ = std::process::Command::new("explorer.exe")
        .arg(path.to_str().unwrap_or("."))
        .spawn();
}

pub fn get_transfers() -> Vec<BridgeTransfer> {
    TRANSFERS.lock().unwrap().clone()
}

pub fn get_activities() -> Vec<BridgeActivity> {
    ACTIVITIES.lock().unwrap().clone()
}

pub fn add_transfer(transfer: BridgeTransfer) {
    let mut list = TRANSFERS.lock().unwrap();
    list.retain(|t| t.id != transfer.id);
    list.insert(0, transfer);
    if list.len() > 50 {
        list.truncate(50);
    }
}

pub fn cancel_transfer(transfer_id: &str) -> bool {
    let mut list = TRANSFERS.lock().unwrap();
    if let Some(t) = list.iter_mut().find(|x| x.id == transfer_id) {
        t.status = "failed".to_string();
        return true;
    }
    false
}

fn emit_transfers_snapshot(app: &AppHandle, transfers: &[BridgeTransfer]) {
    let _ = app.emit(BRIDGE_TRANSFERS_EVENT, transfers);
    let _ = app.emit(BRIDGE_TRANSFER_LEGACY_EVENT, transfers);
}

fn emit_transfers(app: &AppHandle) {
    // `emit_transfers` must never be called while a TRANSFERS guard is held:
    // get_transfers() locks the same non-reentrant mutex.
    let transfers = get_transfers();
    emit_transfers_snapshot(app, &transfers);
}

pub fn stream_file_to_device(
    app: AppHandle,
    device_name: String,
    file_path: &str,
    target_ip: Option<String>,
) -> Result<(), String> {
    let p = Path::new(file_path);
    if !p.exists() {
        return Err(format!("File does not exist: {}", file_path));
    }
    let mut file = std::fs::File::open(p).map_err(|e| e.to_string())?;
    let file_size = file.metadata().map(|m| m.len()).unwrap_or(0);
    let safe_name = p
        .file_name()
        .and_then(|n| n.to_str())
        .map(sanitize_filename)
        .unwrap_or_else(|| "beamed_file.bin".to_string());

    let ip = match target_ip.filter(|s| !s.is_empty()) {
        Some(explicit_ip) => explicit_ip,
        None => {
            let devs = get_paired_devices();
            let connected = devs.iter().find(|d| d.status == "connected" && !d.ip.is_empty());
            match connected {
                Some(d) => d.ip.clone(),
                None => return Err("No active paired device connected. Ensure mobile app is running on the same network.".to_string()),
            }
        }
    };

    let token = get_bridge_token_for_ip(&ip);
    if token.is_empty() {
        return Err("Device not paired: Authentication token missing. Pair with device first.".to_string());
    }

    let transfer_id = format!("xfer-{}", current_time_ms());
    let transfer = BridgeTransfer {
        id: transfer_id.clone(),
        device_name: device_name.clone(),
        file_name: safe_name.clone(),
        file_size,
        bytes_transferred: 0,
        is_upload: true,
        progress: 0.05,
        status: "transferring".to_string(),
        sha256_checksum: None,
    };
    add_transfer(transfer);
    emit_transfers(&app);

    let app_clone = app.clone();
    let transfer_id_clone = transfer_id.clone();
    let file_name_clone = safe_name.clone();

    std::thread::spawn(move || {
        let addr = format!("{}:53391", ip);
        let timestamp = current_time_ms();
        let nonce = generate_nonce();

        let req_headers = format!(
            "POST /api/beam/stream HTTP/1.1\r\n\
             Host: {}\r\n\
             X-File-Name: {}\r\n\
             Content-Length: {}\r\n\
             Content-Type: application/octet-stream\r\n\
             X-Jarvis-Auth-Token: {}\r\n\
             X-Jarvis-Timestamp: {}\r\n\
             X-Jarvis-Nonce: {}\r\n\
             Connection: close\r\n\r\n",
            addr, file_name_clone, file_size, token, timestamp, nonce
        );

        let mut success = false;
        let mut computed_hash = String::new();

        if let Ok(mut stream) = connect_mobile_tls(&ip, Duration::from_secs(8)) {
            if stream.write_all(req_headers.as_bytes()).is_ok() {
                        let mut buffer = [0u8; 65536];
                        let mut sent_bytes = 0u64;
                        let mut last_progress_emit = 0u64;
                        let mut hasher = Sha256::new();

                        loop {
                            match file.read(&mut buffer) {
                                Ok(0) => {
                                    success = sent_bytes == file_size;
                                    break;
                                }
                                Ok(n) => {
                                    hasher.update(&buffer[..n]);
                                    if stream.write_all(&buffer[..n]).is_err() {
                                        break;
                                    }
                                    sent_bytes += n as u64;

                                    if sent_bytes.saturating_sub(last_progress_emit) > 262144 || sent_bytes == file_size {
                                        last_progress_emit = sent_bytes;
                                        let mut list = TRANSFERS.lock().unwrap();
                                        if let Some(t) = list.iter_mut().find(|x| x.id == transfer_id_clone) {
                                            t.bytes_transferred = sent_bytes;
                                            t.progress = if file_size > 0 { sent_bytes as f32 / file_size as f32 } else { 1.0 };
                                        }
                                        emit_transfers_snapshot(&app_clone, &list);
                                    }
                                }
                                Err(_) => break,
                            }
                        }

                        computed_hash = format!("{:x}", hasher.finalize());

                        if success {
                            let mut resp = [0u8; 1024];
                            if let Ok(n) = stream.read(&mut resp) {
                                let resp_str = String::from_utf8_lossy(&resp[..n]);
                                success = resp_str.contains("200 OK") || resp_str.contains("\"received\":true");
                            }
                        }
            }
        }

        let mut list = TRANSFERS.lock().unwrap();
        if let Some(t) = list.iter_mut().find(|x| x.id == transfer_id_clone) {
            if success {
                t.bytes_transferred = file_size;
                t.progress = 1.0;
                t.status = "completed".to_string();
                t.sha256_checksum = Some(computed_hash);
            } else {
                t.status = "failed".to_string();
            }
        }
        emit_transfers_snapshot(&app_clone, &list);
    });

    Ok(())
}

pub fn beam_file_to_device(
    app: AppHandle,
    device_name: String,
    file_name: String,
    _file_size: u64,
    data_base64: String,
    target_ip: Option<String>,
) -> Result<(), String> {
    if data_base64.is_empty() {
        let p = Path::new(&file_name);
        if p.exists() {
            return stream_file_to_device(app, device_name, &file_name, target_ip);
        }
        return Err("No file content or valid file path provided for transfer".to_string());
    }

    use base64::Engine;
    let bytes = base64::engine::general_purpose::STANDARD
        .decode(&data_base64)
        .map_err(|e| format!("Invalid base64 payload: {}", e))?;

    let staging_dir = std::env::temp_dir().join("jarvis_staging");
    let _ = std::fs::create_dir_all(&staging_dir);
    let safe_name = sanitize_filename(&file_name);
    let staging_file = staging_dir.join(&safe_name);
    std::fs::write(&staging_file, &bytes).map_err(|e| format!("Failed to write staging file: {}", e))?;

    let path_str = staging_file.to_string_lossy().to_string();
    let result = stream_file_to_device(app, device_name, &path_str, target_ip);
    let _ = std::fs::remove_file(&staging_file);
    result
}

pub fn beam_file_from_path(
    app: AppHandle,
    file_path: &str,
    target_ip: Option<String>,
) -> Result<(), String> {
    let p = Path::new(file_path);
    if !p.exists() {
        return Err(format!("File does not exist: {}", file_path));
    }
    // Disallow sending system sensitive files
    let canon = p.canonicalize().map_err(|e| format!("Invalid path: {}", e))?;
    let path_str = canon.to_string_lossy().to_ascii_lowercase();
    if path_str.contains("windows\\system32") || path_str.contains("etc\\shadow") {
        return Err("Access to system directory files is prohibited".to_string());
    }

    let dev_name = {
        let devs = get_paired_devices();
        devs.iter()
            .find(|d| d.status == "connected")
            .map(|d| d.name.clone())
            .unwrap_or_else(|| "Companion Device".to_string())
    };

    stream_file_to_device(app, dev_name, file_path, target_ip)
}

pub fn broadcast_clipboard_to_mobile(text: &str) {
    let devices = get_paired_devices();
    if devices.is_empty() {
        return;
    }
    let text_owned = text.to_string();

    std::thread::spawn(move || {
        for dev in devices {
            if dev.status == "connected" && !dev.ip.is_empty() {
                let token = get_bridge_token_for_device(&dev);
                if token.is_empty() {
                    continue;
                }
                let addr = format!("{}:{}", dev.ip, dev.port);
                if let Ok(mut stream) = connect_mobile_tls(&dev.ip, Duration::from_millis(2000)) {
                    let timestamp = current_time_ms();
                    let nonce = generate_nonce();
                    let body = serde_json::json!({
                        "text": text_owned,
                        "originDeviceId": "jarvis-desktop-primary",
                        "timestampMs": timestamp
                    }).to_string();

                    let req = format!(
                        "POST /api/clipboard HTTP/1.1\r\nHost: {}\r\nContent-Type: application/json\r\nContent-Length: {}\r\nX-Jarvis-Auth-Token: {}\r\nX-Jarvis-Timestamp: {}\r\nX-Jarvis-Nonce: {}\r\nConnection: close\r\n\r\n{}",
                        addr,
                        body.len(),
                        token,
                        timestamp,
                        nonce,
                        body
                    );
                    let _ = stream.write_all(req.as_bytes());
                }
            }
        }
    });
}

pub fn send_control_to_device(
    action: String,
    x: Option<f32>,
    y: Option<f32>,
    delta_x: Option<f32>,
    delta_y: Option<f32>,
    target_ip: Option<String>,
) -> Result<(), String> {
    let ip = match target_ip.filter(|s| !s.is_empty()) {
        Some(explicit) => explicit,
        None => {
            let devs = get_paired_devices();
            let conn = devs.iter().find(|d| d.status == "connected" && !d.ip.is_empty());
            match conn {
                Some(d) => d.ip.clone(),
                None => return Err("No active companion device found for control command".to_string()),
            }
        }
    };

    let token = get_bridge_token_for_ip(&ip);
    if token.is_empty() {
        return Err("Authentication token missing. Pair device before sending controls.".to_string());
    }

    let addr = format!("{}:53391", ip);
    let timestamp = current_time_ms();
    let nonce = generate_nonce();

    let body = serde_json::json!({
        "action": action,
        "x": x,
        "y": y,
        "deltaX": delta_x,
        "deltaY": delta_y,
        "originDeviceId": "jarvis-desktop-primary",
        "timestampMs": timestamp
    }).to_string();

    let req = format!(
        "POST /api/control HTTP/1.1\r\nHost: {}\r\nContent-Type: application/json\r\nContent-Length: {}\r\nX-Jarvis-Auth-Token: {}\r\nX-Jarvis-Timestamp: {}\r\nX-Jarvis-Nonce: {}\r\nConnection: close\r\n\r\n{}",
        addr,
        body.len(),
        token,
        timestamp,
        nonce,
        body
    );

    let control_ip = ip.clone();
    std::thread::spawn(move || {
        if let Ok(mut stream) = connect_mobile_tls(&control_ip, Duration::from_millis(1500)) {
            let _ = stream.write_all(req.as_bytes());
        }
    });

    Ok(())
}

fn write_json_response<W: Write>(stream: &mut W, json_body: &str) {
    let resp = format!(
        "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{}",
        json_body.len(),
        json_body
    );
    let _ = stream.write_all(resp.as_bytes());
    let _ = stream.flush();
}

fn write_error_response<W: Write>(stream: &mut W, code: u16, status: &str, message: &str) {
    let body = serde_json::json!({
        "error": message,
        "statusCode": code,
        "status": status
    }).to_string();
    let resp = format!(
        "HTTP/1.1 {} {}\r\nContent-Type: application/json\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{}",
        code,
        status,
        body.len(),
        body
    );
    let _ = stream.write_all(resp.as_bytes());
    let _ = stream.flush();
}

fn get_header_value<'a>(headers: &'a str, name: &str) -> Option<&'a str> {
    for line in headers.lines() {
        if let Some((k, v)) = line.split_once(':') {
            if k.trim().eq_ignore_ascii_case(name) {
                return Some(v.trim());
            }
        }
    }
    None
}

fn is_authorized(headers: &str, peer_ip: &str) -> Result<PairedDeviceRecord, String> {
    let token = get_header_value(headers, "X-Jarvis-Auth-Token")
        .or_else(|| {
            get_header_value(headers, "Authorization")
                .and_then(|auth| auth.strip_prefix("Bearer ").map(|b| b.trim()))
        })
        .unwrap_or("");

    if token.is_empty() {
        return Err("Missing X-Jarvis-Auth-Token or Authorization Bearer token".to_string());
    }

    // Check environment secret override if configured
    if let Ok(env_secret) = std::env::var("JARVIS_BRIDGE_SECRET") {
        if !env_secret.trim().is_empty() && token == env_secret.trim() {
            return Ok(PairedDeviceRecord {
                device_id: "env-secret-client".to_string(),
                device_name: "Master Bridge Admin".to_string(),
                auth_token: env_secret,
                ip: peer_ip.to_string(),
                peer_fingerprint: String::new(),
                permissions: DevicePermissions {
                    status: true,
                    clipboard: true,
                    file_transfer: true,
                    remote_control: true,
                },
                paired_at_ms: current_time_ms(),
            });
        }
    }

    with_paired_devices(|map| {
        if let Some(record) = map.get(token) {
            // A token is bound to the address that completed pairing. Without this
            // check a spoofed UDP beacon could claim a paired device id and receive
            // its token and clipboard traffic at an attacker-controlled IP.
            if !record.ip.is_empty() && record.ip != peer_ip {
                return Err("Authentication token is bound to a different device address; re-pair required".to_string());
            }
            Ok(record.clone())
        } else {
            Err("Invalid or revoked authentication token".to_string())
        }
    })
}

pub fn start_livebridge_service(app: AppHandle) {
    load_paired_devices();
    init_protocol_engine();

    let app_handle = Arc::new(app);

    // 1. Background UDP Beacon Broadcast & Discovery
    std::thread::spawn(move || {
        let socket = match UdpSocket::bind("0.0.0.0:0") {
            Ok(s) => s,
            Err(e) => {
                log::warn!("LiveBridge: Could not bind UDP discovery socket: {}", e);
                return;
            }
        };
        let _ = socket.set_broadcast(true);

        let beacon_msg = serde_json::json!({
            "type": "jarvis_beacon",
            "name": "JARVIS Desktop Island",
            "port": 53391,
            "version": "1.0.0",
            "tlsFingerprint": crate::providers::bridge_tls::local_fingerprint()
        }).to_string();

        loop {
            let _ = socket.send_to(beacon_msg.as_bytes(), "255.255.255.255:53390");
            let _ = socket.send_to(beacon_msg.as_bytes(), "255.255.255.255:53391");
            std::thread::sleep(Duration::from_secs(4));
        }
    });

    // 1b. Listen for incoming UDP beacons from mobile devices
    let app_udp_rx = app_handle.clone();
    std::thread::spawn(move || {
        if let Ok(rx_sock) = UdpSocket::bind("0.0.0.0:53390") {
            let mut buf = [0u8; 2048];
            while let Ok((amt, src)) = rx_sock.recv_from(&mut buf) {
                if let Ok(msg) = std::str::from_utf8(&buf[..amt]) {
                    if let Ok(json) = serde_json::from_str::<serde_json::Value>(msg) {
                        if json.get("type").and_then(|t| t.as_str()) == Some("jarvis_mobile_beacon") {
                            let ip = src.ip().to_string();
                            let dev_id = json.get("deviceId").and_then(|i| i.as_str()).unwrap_or("unknown-mobile");
                            let dev_name = json.get("name").and_then(|n| n.as_str()).unwrap_or("Mobile Device");
                            let battery = json.get("batteryPercent").and_then(|b| b.as_u64()).unwrap_or(100) as u8;
                            let charging = json.get("isCharging").and_then(|c| c.as_bool()).unwrap_or(false);
                            let ssid = json.get("wifiSsid").and_then(|s| s.as_str()).unwrap_or("Wi-Fi");
                            // Discovery alone never grants trust: a device is only
                            // "connected" once it has completed PIN pairing from this IP.
                            let paired = is_device_paired(dev_id, &ip);

                            let mut devs = DEVICES.lock().unwrap();
                            devs.retain(|d| d.ip != ip);
                            devs.insert(0, BridgeDevice {
                                id: dev_id.to_string(),
                                name: dev_name.to_string(),
                                device_type: "android".to_string(),
                                ip: ip.clone(),
                                port: 53391,
                                battery_percent: battery,
                                is_charging: charging,
                                wifi_ssid: ssid.to_string(),
                                status: if paired { "connected".to_string() } else { "discovered".to_string() },
                                last_seen_ms: current_time_ms(),
                            });
                            let _ = app_udp_rx.emit(BRIDGE_DEVICE_EVENT, devs.clone());

                            // Update protocol engine presence
                            if let Some(ref eng) = *PROTOCOL_ENGINE.lock().unwrap() {
                                eng.update_presence(&DevicePresencePayload {
                                    device_id: dev_id.to_string(),
                                    device_name: dev_name.to_string(),
                                    device_type: "android".to_string(),
                                    battery_percent: battery,
                                    is_charging: charging,
                                    wifi_ssid: ssid.to_string(),
                                    ip_address: ip,
                                    port: 53391,
                                    protocol_version: "1.0.0".to_string(),
                                }, Some(0));
                            }
                        }
                    }
                }
            }
        }
    });

    // 2. Background TCP HTTP/REST API Server for Mobile Connections
    let app_tcp = app_handle.clone();
    std::thread::spawn(move || {
        let listener = match TcpListener::bind("0.0.0.0:53391") {
            Ok(l) => l,
            Err(e) => {
                log::error!("LiveBridge TCP port 53391 is already bound or unavailable ({}) - check for duplicate JARVIS instance.", e);
                return;
            }
        };

        let tls_config = match crate::providers::bridge_tls::server_config() {
            Ok(config) => config,
            Err(e) => {
                log::error!("LiveBridge TLS identity unavailable, refusing to serve cleartext: {}", e);
                return;
            }
        };

        for stream in listener.incoming() {
            if let Ok(tcp_stream) = stream {
                let _ = tcp_stream.set_read_timeout(Some(Duration::from_secs(10)));
                let _ = tcp_stream.set_write_timeout(Some(Duration::from_secs(10)));
                let peer_ip = tcp_stream
                    .peer_addr()
                    .map(|addr| addr.ip().to_string())
                    .unwrap_or_default();

                let app = app_tcp.clone();
                let config = tls_config.clone();
                std::thread::spawn(move || {
                    let connection = match rustls::ServerConnection::new(config) {
                        Ok(connection) => connection,
                        Err(_) => return,
                    };
                    let mut stream = rustls::StreamOwned::new(connection, tcp_stream);

                    let mut head_data = Vec::new();
                    let mut chunk = [0u8; 4096];
                    let mut body_start = None;

                    // Read header section
                    while head_data.len() < 16384 {
                        match stream.read(&mut chunk) {
                            Ok(0) => break,
                            Ok(n) => {
                                head_data.extend_from_slice(&chunk[..n]);
                                if let Some(idx) = head_data.windows(4).position(|w| w == b"\r\n\r\n") {
                                    body_start = Some(idx + 4);
                                    break;
                                }
                            }
                            Err(_) => break,
                        }
                    }

                    let body_idx = match body_start {
                        Some(idx) => idx,
                        None => {
                            write_error_response(&mut stream, 400, "Bad Request", "Malformed HTTP request header");
                            return;
                        }
                    };

                    let header_str = String::from_utf8_lossy(&head_data[..body_idx]).to_string();
                    let req_line = header_str.lines().next().unwrap_or("").to_string();

                    let content_len = get_header_value(&header_str, "content-length")
                        .and_then(|v| v.parse::<usize>().ok())
                        .unwrap_or(0);

                    // 1. Read-only status endpoint (strictly unauthenticated & read-only)
                    if req_line.starts_with("GET /api/status") {
                        let body = serde_json::json!({
                            "status": "ok",
                            "service": "JARVIS LiveBridge",
                            "version": "1.0.0",
                            "tlsFingerprint": crate::providers::bridge_tls::local_fingerprint()
                        }).to_string();
                        write_json_response(&mut stream, &body);
                        return;
                    }

                    // 2. Explicit Secure Pairing Endpoints
                    // 2a. Pairing Request
                    if req_line.starts_with("POST /api/pair/request") {
                        if content_len > MAX_JSON_PAYLOAD_SIZE {
                            write_error_response(&mut stream, 413, "Payload Too Large", "Pairing payload exceeds allowed size boundary of 1 MB");
                            return;
                        }
                        let mut body_bytes = head_data[body_idx..].to_vec();
                        let mut remaining = content_len.saturating_sub(body_bytes.len());
                        while remaining > 0 {
                            let to_read = remaining.min(chunk.len());
                            match stream.read(&mut chunk[..to_read]) {
                                Ok(0) => break,
                                Ok(n) => {
                                    body_bytes.extend_from_slice(&chunk[..n]);
                                    remaining = remaining.saturating_sub(n);
                                }
                                Err(_) => break,
                            }
                        }

                        let challenge_id = format!("pair-{}", current_time_ms());
                        let pin = generate_pairing_pin();
                        let (dev_id, dev_name) = if let Ok(json) = serde_json::from_slice::<serde_json::Value>(&body_bytes) {
                            let id = json.get("clientDeviceId").and_then(|v| v.as_str()).unwrap_or("companion-device").to_string();
                            let name = json.get("clientDeviceName").and_then(|v| v.as_str()).unwrap_or("Companion Device").to_string();
                            (id, name)
                        } else {
                            ("companion-device".to_string(), "Companion Device".to_string())
                        };

                        let challenge = PairingChallenge {
                            challenge_id: challenge_id.clone(),
                            pin: pin.clone(),
                            client_device_id: dev_id,
                            client_device_name: dev_name,
                            expires_at_ms: current_time_ms() + 120_000,
                            attempts: 0,
                        };

                        with_challenges(|challenges| {
                            challenges.retain(|_, c| c.expires_at_ms > current_time_ms());
                            if challenges.len() >= 32 {
                                if let Some(oldest) = challenges
                                    .iter()
                                    .min_by_key(|(_, c)| c.expires_at_ms)
                                    .map(|(id, _)| id.clone())
                                {
                                    challenges.remove(&oldest);
                                }
                            }
                            challenges.insert(challenge_id.clone(), challenge.clone());
                        });

                        // Surface pairing challenge to UI
                        let _ = app.emit(BRIDGE_PAIRING_EVENT, &challenge);

                        let resp = serde_json::json!({
                            "status": "pending_approval",
                            "challengeId": challenge_id,
                            "expiresInSeconds": 120
                        }).to_string();
                        write_json_response(&mut stream, &resp);
                        return;
                    }

                    // 2b. Pairing Confirmation with PIN
                    if req_line.starts_with("POST /api/pair/confirm") {
                        if content_len > MAX_JSON_PAYLOAD_SIZE {
                            write_error_response(&mut stream, 413, "Payload Too Large", "Pairing payload exceeds allowed size boundary of 1 MB");
                            return;
                        }
                        let mut body_bytes = head_data[body_idx..].to_vec();
                        let mut remaining = content_len.saturating_sub(body_bytes.len());
                        while remaining > 0 {
                            let to_read = remaining.min(chunk.len());
                            match stream.read(&mut chunk[..to_read]) {
                                Ok(0) => break,
                                Ok(n) => {
                                    body_bytes.extend_from_slice(&chunk[..n]);
                                    remaining = remaining.saturating_sub(n);
                                }
                                Err(_) => break,
                            }
                        }

                        if let Ok(json) = serde_json::from_slice::<serde_json::Value>(&body_bytes) {
                            let challenge_id = json.get("challengeId").and_then(|v| v.as_str()).unwrap_or("");
                            let submitted_pin = json.get("pin").and_then(|v| v.as_str()).unwrap_or("");

                            let matched = with_challenges(|challenges| {
                                let mut success = false;
                                let mut remove = false;
                                if let Some(challenge) = challenges.get_mut(challenge_id) {
                                    if current_time_ms() > challenge.expires_at_ms {
                                        remove = true;
                                    } else if challenge.pin == submitted_pin {
                                        success = true;
                                    } else {
                                        challenge.attempts += 1;
                                        // A 6-digit PIN must not be brute-forceable.
                                        if challenge.attempts >= 5 {
                                            remove = true;
                                        }
                                    }
                                }
                                if success {
                                    challenges.remove(challenge_id)
                                } else {
                                    if remove {
                                        challenges.remove(challenge_id);
                                    }
                                    None
                                }
                            });

                            if let Some(challenge) = matched {
                                let new_token = generate_secure_token();
                                let (client_cert_pem, client_key_pem, client_fingerprint) =
                                    match crate::providers::bridge_tls::issue_client_identity() {
                                        Ok(identity) => identity,
                                        Err(e) => {
                                            write_error_response(&mut stream, 500, "Internal Server Error", &e);
                                            return;
                                        }
                                    };
                                let record = PairedDeviceRecord {
                                    device_id: challenge.client_device_id.clone(),
                                    device_name: challenge.client_device_name.clone(),
                                    auth_token: new_token.clone(),
                                    ip: peer_ip.clone(),
                                    peer_fingerprint: client_fingerprint.clone(),
                                    permissions: DevicePermissions::default(),
                                    paired_at_ms: current_time_ms(),
                                };

                                with_paired_devices(|map| {
                                    map.insert(new_token.clone(), record);
                                });
                                save_paired_devices();

                                let resp = serde_json::json!({
                                    "status": "paired",
                                    "authToken": new_token,
                                    "deviceId": "jarvis-desktop-primary",
                                    "protocolVersion": "1.0.0",
                                    "serverTlsFingerprint": crate::providers::bridge_tls::local_fingerprint(),
                                    "clientCertPem": client_cert_pem,
                                    "clientKeyPem": client_key_pem
                                }).to_string();
                                write_json_response(&mut stream, &resp);
                                return;
                            }
                        }

                        write_error_response(&mut stream, 401, "Unauthorized", "Invalid or expired pairing PIN challenge");
                        return;
                    }

                    // Rejection of legacy unauthenticated /api/pair
                    if req_line.starts_with("POST /api/pair") {
                        write_error_response(
                            &mut stream,
                            400,
                            "Bad Request",
                            "Unauthenticated pairing is prohibited. Initiate pairing via /api/pair/request with PIN confirmation.",
                        );
                        return;
                    }

                    // 3. Replay Protection on all protected requests
                    let ts = get_header_value(&header_str, "X-Jarvis-Timestamp")
                        .and_then(|t| t.parse::<u64>().ok())
                        .unwrap_or(0);
                    let nonce = get_header_value(&header_str, "X-Jarvis-Nonce").unwrap_or("");

                    if let Err(e) = check_and_record_nonce(nonce, ts) {
                        write_error_response(&mut stream, 401, "Unauthorized", e);
                        return;
                    }

                    // 4. Authenticate Request & Resolve Scoped Device Permissions
                    let paired_device = match is_authorized(&header_str, &peer_ip) {
                        Ok(dev) => dev,
                        Err(msg) => {
                            write_error_response(&mut stream, 401, "Unauthorized", &msg);
                            return;
                        }
                    };

                    // Enforce payload size limit on JSON endpoints. Streaming file
                    // bodies are checked against MAX_FILE_TRANSFER_SIZE instead.
                    if content_len > MAX_JSON_PAYLOAD_SIZE && !req_line.starts_with("POST /api/beam/stream") {
                        write_error_response(&mut stream, 413, "Payload Too Large", "Payload exceeds allowed size boundary of 1 MB");
                        return;
                    }

                    // 5. Remote Mouse / Keyboard Control (Requires explicit remote_control permission)
                    if req_line.starts_with("POST /api/mouse") || req_line.starts_with("POST /api/control") {
                        if !paired_device.permissions.remote_control {
                            write_error_response(
                                &mut stream,
                                403,
                                "Forbidden",
                                "Remote control is not authorized for this device. Enable remote control permission in JARVIS settings.",
                            );
                            return;
                        }

                        let mut body_bytes = head_data[body_idx..].to_vec();
                        let mut remaining = content_len.saturating_sub(body_bytes.len());
                        while remaining > 0 {
                            let to_read = remaining.min(chunk.len());
                            match stream.read(&mut chunk[..to_read]) {
                                Ok(0) => break,
                                Ok(n) => {
                                    body_bytes.extend_from_slice(&chunk[..n]);
                                    remaining = remaining.saturating_sub(n);
                                }
                                Err(_) => break,
                            }
                        }

                        if let Ok(json) = serde_json::from_slice::<serde_json::Value>(&body_bytes) {
                            let action = json.get("action").and_then(|v| v.as_str()).unwrap_or("move");
                            let dx = json.get("dx").and_then(|v| v.as_f64()).unwrap_or(0.0) as f32;
                            let dy = json.get("dy").and_then(|v| v.as_f64()).unwrap_or(0.0) as f32;
                            let button = json.get("button").and_then(|v| v.as_str()).unwrap_or("left");

                            handle_remote_mouse_action(action, dx, dy, button);
                        }

                        write_json_response(&mut stream, "{\"status\":\"ok\"}");
                        return;
                    }

                    // 6. Streaming Binary Receiver (/api/beam/stream)
                    if req_line.starts_with("POST /api/beam/stream") {
                        if !paired_device.permissions.file_transfer {
                            write_error_response(&mut stream, 403, "Forbidden", "File transfer permission not granted");
                            return;
                        }

                        let file_size = content_len as u64;
                        if file_size > MAX_FILE_TRANSFER_SIZE {
                            write_error_response(&mut stream, 413, "Payload Too Large", "File exceeds 2 GB streaming boundary");
                            return;
                        }

                        let raw_name = get_header_value(&header_str, "X-File-Name").unwrap_or("Incoming_File.bin");
                        let download_dir = get_jarvis_download_dir();
                        let (final_path, safe_name, is_executable) = get_safe_destination_path(&download_dir, raw_name);

                        let transfer_id = format!("rx-stream-{}", current_time_ms());
                        let transfer = BridgeTransfer {
                            id: transfer_id.clone(),
                            device_name: paired_device.device_name.clone(),
                            file_name: safe_name.clone(),
                            file_size,
                            bytes_transferred: 0,
                            is_upload: false,
                            progress: 0.05,
                            status: "transferring".to_string(),
                            sha256_checksum: None,
                        };
                        add_transfer(transfer);
                        emit_transfers(&app);

                        let tmp_path = final_path.with_extension(format!("tmp.{}", current_time_ms()));
                        let mut tmp_file = match std::fs::File::create(&tmp_path) {
                            Ok(f) => f,
                            Err(e) => {
                                write_error_response(&mut stream, 500, "Internal Server Error", &format!("Cannot create destination staging file: {}", e));
                                return;
                            }
                        };

                        let mut hasher = Sha256::new();
                        let mut total_written = 0u64;

                        // Write initial bytes captured in header buffer
                        let initial_body = &head_data[body_idx..];
                        if !initial_body.is_empty() {
                            if tmp_file.write_all(initial_body).is_err() {
                                let _ = std::fs::remove_file(&tmp_path);
                                write_error_response(&mut stream, 500, "Internal Server Error", "Write error");
                                return;
                            }
                            hasher.update(initial_body);
                            total_written += initial_body.len() as u64;
                        }

                        let mut stream_buffer = [0u8; 65536];
                        let mut last_progress_emit = 0u64;
                        let mut success = true;

                        while total_written < file_size {
                            let to_read = ((file_size - total_written) as usize).min(stream_buffer.len());
                            match stream.read(&mut stream_buffer[..to_read]) {
                                Ok(0) => {
                                    if total_written < file_size {
                                        success = false;
                                    }
                                    break;
                                }
                                Ok(n) => {
                                    if tmp_file.write_all(&stream_buffer[..n]).is_err() {
                                        success = false;
                                        break;
                                    }
                                    hasher.update(&stream_buffer[..n]);
                                    total_written += n as u64;

                                    if total_written.saturating_sub(last_progress_emit) > 262144 || total_written == file_size {
                                        last_progress_emit = total_written;
                                        let mut list = TRANSFERS.lock().unwrap();
                                        if let Some(t) = list.iter_mut().find(|x| x.id == transfer_id) {
                                            t.bytes_transferred = total_written;
                                            t.progress = if file_size > 0 { total_written as f32 / file_size as f32 } else { 1.0 };
                                        }
                                        emit_transfers_snapshot(&app, &list);
                                    }
                                }
                                Err(_) => {
                                    success = false;
                                    break;
                                }
                            }
                        }

                        let _ = tmp_file.flush();
                        drop(tmp_file);

                        if success && total_written == file_size {
                            let computed_hash = format!("{:x}", hasher.finalize());
                            let expected_hash = get_header_value(&header_str, "X-Jarvis-Checksum-Sha256").map(|s| s.to_ascii_lowercase());

                            if let Some(expected) = expected_hash {
                                if !expected.is_empty() && expected != computed_hash {
                                    let _ = std::fs::remove_file(&tmp_path);
                                    write_error_response(&mut stream, 400, "Bad Request", "Checksum verification failed: file content is corrupted or tampered");
                                    let mut list = TRANSFERS.lock().unwrap();
                                    if let Some(t) = list.iter_mut().find(|x| x.id == transfer_id) {
                                        t.status = "failed".to_string();
                                    }
                                    emit_transfers_snapshot(&app, &list);
                                    return;
                                }
                            }

                            if std::fs::rename(&tmp_path, &final_path).is_ok() {
                                let mut list = TRANSFERS.lock().unwrap();
                                if let Some(t) = list.iter_mut().find(|x| x.id == transfer_id) {
                                    t.bytes_transferred = total_written;
                                    t.progress = 1.0;
                                    t.status = "completed".to_string();
                                    t.sha256_checksum = Some(computed_hash);
                                }
                                emit_transfers_snapshot(&app, &list);

                                let warning = if is_executable { ", \"warning\": \"Executable file detected - renamed with .downloaded suffix for security\"" } else { "" };
                                write_json_response(
                                    &mut stream,
                                    &format!("{{\"received\":true,\"status\":\"ok\",\"fileName\":\"{}\"{}}}", safe_name, warning),
                                );
                            } else {
                                let _ = std::fs::remove_file(&tmp_path);
                                write_error_response(&mut stream, 500, "Internal Server Error", "Failed to commit transferred file");
                            }
                        } else {
                            let _ = std::fs::remove_file(&tmp_path);
                            let mut list = TRANSFERS.lock().unwrap();
                            if let Some(t) = list.iter_mut().find(|x| x.id == transfer_id) {
                                t.status = "failed".to_string();
                            }
                            emit_transfers_snapshot(&app, &list);
                            write_error_response(&mut stream, 500, "Internal Server Error", "Stream incomplete or connection severed");
                        }
                        return;
                    }

                    // Read remaining JSON body data
                    let mut body_bytes = head_data[body_idx..].to_vec();
                    let mut remaining = content_len.saturating_sub(body_bytes.len());
                    while remaining > 0 {
                        let to_read = remaining.min(chunk.len());
                        match stream.read(&mut chunk[..to_read]) {
                            Ok(0) => break,
                            Ok(n) => {
                                body_bytes.extend_from_slice(&chunk[..n]);
                                remaining = remaining.saturating_sub(n);
                            }
                            Err(_) => break,
                        }
                    }

                    let body_str = String::from_utf8_lossy(&body_bytes);

                    if req_line.starts_with("POST /api/telemetry") {
                        if let Ok(json) = serde_json::from_str::<serde_json::Value>(&body_str) {
                            let dev_id = json.get("deviceId").and_then(|v| v.as_str()).unwrap_or(&paired_device.device_id);
                            let dev_name = json.get("deviceName").and_then(|v| v.as_str()).unwrap_or(&paired_device.device_name);
                            let battery = json.get("batteryPercent").and_then(|v| v.as_u64()).unwrap_or(100) as u8;
                            let charging = json.get("isCharging").and_then(|v| v.as_bool()).unwrap_or(false);
                            let ssid = json.get("wifiSsid").and_then(|v| v.as_str()).unwrap_or("Wi-Fi");

                            let real_ip = if !peer_ip.is_empty() { peer_ip.clone() } else { "127.0.0.1".to_string() };
                            let mut devs = DEVICES.lock().unwrap();
                            devs.retain(|d| d.id != dev_id);
                            devs.insert(0, BridgeDevice {
                                id: dev_id.to_string(),
                                name: dev_name.to_string(),
                                device_type: "android".to_string(),
                                ip: real_ip,
                                port: 53391,
                                battery_percent: battery,
                                is_charging: charging,
                                wifi_ssid: ssid.to_string(),
                                status: "connected".to_string(),
                                last_seen_ms: current_time_ms(),
                            });
                        }
                        write_json_response(&mut stream, "{\"acknowledged\":true}");
                        let _ = app.emit(BRIDGE_DEVICE_EVENT, get_paired_devices());
                    } else if req_line.starts_with("POST /api/clipboard") {
                        if paired_device.permissions.clipboard {
                            if let Ok(json) = serde_json::from_str::<serde_json::Value>(&body_str) {
                                let origin = json.get("originDeviceId").and_then(|o| o.as_str()).unwrap_or("");
                                if origin != "jarvis-desktop-primary" {
                                    if let Some(txt) = json.get("text").and_then(|t| t.as_str()) {
                                        crate::providers::clipboard::set_clipboard_text_from_external(txt);
                                    }
                                }
                            }
                            write_json_response(&mut stream, "{\"copied\":true}");
                        } else {
                            write_error_response(&mut stream, 403, "Forbidden", "Clipboard sharing not permitted");
                        }
                    } else if req_line.starts_with("POST /api/transfer/cancel") {
                        if let Ok(json) = serde_json::from_str::<serde_json::Value>(&body_str) {
                            if let Some(xfer_id) = json.get("transferId").and_then(|v| v.as_str()) {
                                cancel_transfer(xfer_id);
                                emit_transfers(&app);
                            }
                        }
                        write_json_response(&mut stream, "{\"cancelled\":true}");
                    } else {
                        write_json_response(&mut stream, "{\"service\":\"JARVIS LiveBridge\",\"version\":\"1.0.0\"}");
                    }
                });
            }
        }
    });
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_filename_sanitization() {
        assert_eq!(sanitize_filename("../../etc/passwd"), "passwd");
        assert_eq!(sanitize_filename("..\\..\\windows\\system32\\calc.exe"), "calc.exe");
        assert_eq!(sanitize_filename("valid_report.pdf"), "valid_report.pdf");
        assert_eq!(sanitize_filename("CON.txt"), "file_CON.txt");
        assert_eq!(sanitize_filename("NUL"), "file_NUL");
    }

    #[test]
    fn test_quarantine_dangerous_extensions() {
        let downloads = get_jarvis_download_dir();
        let safe_dest = get_safe_destination_path(&downloads, "malware.exe");
        assert!(safe_dest.0.to_string_lossy().ends_with(".exe.downloaded"));
        assert!(safe_dest.2);

        let safe_bat = get_safe_destination_path(&downloads, "script.bat");
        assert!(safe_bat.0.to_string_lossy().ends_with(".bat.downloaded"));
        assert!(safe_bat.2);

        let normal_pdf = get_safe_destination_path(&downloads, "document.pdf");
        assert!(normal_pdf.0.to_string_lossy().ends_with("document.pdf"));
        assert!(!normal_pdf.2);
    }

    #[test]
    fn test_legacy_device_record_without_ip_deserializes() {
        let legacy = r#"{
            "deviceId": "abc",
            "deviceName": "Pixel",
            "authToken": "secret",
            "permissions": {
                "status": true,
                "clipboard": true,
                "fileTransfer": true,
                "remoteControl": false
            },
            "pairedAtMs": 1
        }"#;
        let record: PairedDeviceRecord = serde_json::from_str(legacy).expect("legacy record should load");
        assert_eq!(record.ip, "");
    }

    #[test]
    fn test_replay_protection_nonce_cache() {
        let nonce = "unique-test-nonce-123";
        let now = current_time_ms();
        assert!(check_and_record_nonce(nonce, now).is_ok(), "First use of nonce must be accepted");
        assert!(check_and_record_nonce(nonce, now).is_err(), "Duplicate replay of nonce must be rejected");
    }

    #[test]
    fn test_timestamp_skew_validation() {
        let now = current_time_ms();
        // Request from 10 seconds ago is valid
        let recent = now - 10_000;
        let diff = (now as i64 - recent as i64).abs();
        assert!(diff <= 60_000);

        // Request from 5 minutes ago is invalid (stale replay)
        let stale = now - 300_000;
        let stale_diff = (now as i64 - stale as i64).abs();
        assert!(stale_diff > 60_000);

        // Request from 5 minutes in future is invalid (clock tampering)
        let future = now + 300_000;
        let future_diff = (now as i64 - future as i64).abs();
        assert!(future_diff > 60_000);
    }
}
