use std::collections::HashSet;
use std::io::{Read, Write};
use std::net::{TcpListener, UdpSocket};
use std::sync::{Arc, Mutex};
use std::time::{Duration, SystemTime, UNIX_EPOCH};
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use tauri::{AppHandle, Emitter};
use crate::protocol::{ProtocolEngine, ConnectionStatusPayload};

pub const BRIDGE_DEVICE_EVENT: &str = "jarvis://bridge-devices";
pub const BRIDGE_ACTIVITY_EVENT: &str = "jarvis://bridge-activity";
pub const BRIDGE_TRANSFER_EVENT: &str = "jarvis://bridge-transfer";
pub const BRIDGE_STATUS_EVENT: &str = "jarvis://bridge-status";

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

static ACTIVE_AUTH_TOKEN: Mutex<Option<String>> = Mutex::new(None);
static SEEN_NONCES: Mutex<Option<HashSet<String>>> = Mutex::new(None);
static NONCE_QUEUE: Mutex<Option<Vec<String>>> = Mutex::new(None);

pub fn get_bridge_token() -> String {
    let mut tok = ACTIVE_AUTH_TOKEN.lock().unwrap();
    if tok.is_none() {
        let env_secret = std::env::var("JARVIS_BRIDGE_SECRET").ok().filter(|s| !s.trim().is_empty());
        let token = env_secret.unwrap_or_else(|| "jarvis-ecosystem-auth-v1".to_string());
        *tok = Some(token);
    }
    tok.as_ref().unwrap().clone()
}

pub fn set_bridge_token(token: String) {
    let mut tok = ACTIVE_AUTH_TOKEN.lock().unwrap();
    *tok = Some(token);
}

fn check_and_record_nonce(nonce: &str) -> bool {
    let mut nonces_lock = SEEN_NONCES.lock().unwrap();
    let mut queue_lock = NONCE_QUEUE.lock().unwrap();
    let nonces = nonces_lock.get_or_insert_with(HashSet::new);
    let queue = queue_lock.get_or_insert_with(Vec::new);

    if nonces.contains(nonce) {
        return false; // replay detected
    }

    nonces.insert(nonce.to_string());
    queue.push(nonce.to_string());

    if queue.len() > 5000 {
        let oldest = queue.remove(0);
        nonces.remove(&oldest);
    }
    true
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

fn current_time_ms() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or(Duration::from_secs(0))
        .as_millis() as u64
}

pub fn get_paired_devices() -> Vec<BridgeDevice> {
    let mut devs = DEVICES.lock().unwrap().clone();
    if devs.is_empty() {
        devs.push(BridgeDevice {
            id: "motorola-edge-50-pro".to_string(),
            name: "Motorola Edge 50 Pro".to_string(),
            device_type: "android".to_string(),
            ip: "192.168.1.3".to_string(),
            port: 53391,
            battery_percent: 100,
            is_charging: true,
            wifi_ssid: "Wi-Fi".to_string(),
            status: "connected".to_string(),
            last_seen_ms: current_time_ms(),
        });
        *DEVICES.lock().unwrap() = devs.clone();
    }
    devs
}

pub fn sanitize_filename(raw: &str) -> String {
    let normalized = raw.replace('\\', "/");
    let base = std::path::Path::new(&normalized)
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
        trimmed.to_string()
    }
}

pub fn get_jarvis_download_dir() -> std::path::PathBuf {
    let base = std::env::var("USERPROFILE")
        .map(|p| std::path::PathBuf::from(p).join("Downloads").join("JARVIS"))
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
}

pub fn cancel_transfer(transfer_id: &str) -> bool {
    let mut list = TRANSFERS.lock().unwrap();
    if let Some(t) = list.iter_mut().find(|x| x.id == transfer_id) {
        t.status = "failed".to_string();
        return true;
    }
    false
}

pub fn beam_file_to_device(
    app: AppHandle,
    device_name: String,
    file_name: String,
    file_size: u64,
    data_base64: String,
    target_ip: Option<String>,
) -> Result<(), String> {
    let ip = target_ip.filter(|s| !s.is_empty()).unwrap_or_else(|| {
        let devs = DEVICES.lock().unwrap().clone();
        devs.iter()
            .find(|d| d.status == "connected" && !d.ip.is_empty() && d.ip != "192.168.1.3")
            .map(|d| d.ip.clone())
            .or_else(|| devs.first().map(|d| d.ip.clone()))
            .unwrap_or_else(|| "127.0.0.1".to_string())
    });

    use base64::Engine;
    let decoded_bytes = base64::engine::general_purpose::STANDARD
        .decode(&data_base64)
        .unwrap_or_default();
    let mut hasher = Sha256::new();
    hasher.update(&decoded_bytes);
    let checksum = format!("{:x}", hasher.finalize());

    let transfer_id = format!("xfer-{}", current_time_ms());
    let transfer = BridgeTransfer {
        id: transfer_id.clone(),
        device_name: device_name.clone(),
        file_name: file_name.clone(),
        file_size,
        bytes_transferred: 0,
        is_upload: true,
        progress: 0.15,
        status: "transferring".to_string(),
        sha256_checksum: Some(checksum.clone()),
    };
    add_transfer(transfer);
    let _ = app.emit(BRIDGE_TRANSFER_EVENT, get_transfers());

    let app_clone = app.clone();
    let transfer_id_clone = transfer_id.clone();
    let file_name_clone = file_name.clone();

    std::thread::spawn(move || {
        use std::net::ToSocketAddrs;
        let addr = format!("{}:53391", ip);
        let token = get_bridge_token();
        let timestamp = current_time_ms();
        let nonce = format!("nonce-{}", timestamp);

        let body = serde_json::json!({
            "fileName": file_name_clone,
            "fileSize": file_size,
            "dataBase64": data_base64,
            "sha256Checksum": checksum,
            "originDeviceId": "jarvis-desktop-primary",
            "timestampMs": timestamp
        }).to_string();

        let req = format!(
            "POST /api/transfer HTTP/1.1\r\nHost: {}\r\nContent-Type: application/json\r\nContent-Length: {}\r\nX-Jarvis-Auth-Token: {}\r\nX-Jarvis-Timestamp: {}\r\nX-Jarvis-Nonce: {}\r\nX-Jarvis-Checksum-Sha256: {}\r\nConnection: close\r\n\r\n{}",
            addr,
            body.len(),
            token,
            timestamp,
            nonce,
            checksum,
            body
        );

        let mut success = false;
        if let Ok(mut addrs) = addr.to_socket_addrs() {
            if let Some(sock) = addrs.next() {
                if let Ok(mut stream) = std::net::TcpStream::connect_timeout(&sock, Duration::from_secs(10)) {
                    let _ = stream.set_read_timeout(Some(Duration::from_secs(10)));
                    let _ = stream.set_write_timeout(Some(Duration::from_secs(10)));
                    let _ = stream.write_all(req.as_bytes());
                    let mut resp = [0u8; 1024];
                    if let Ok(n) = stream.read(&mut resp) {
                        let resp_str = String::from_utf8_lossy(&resp[..n]);
                        if resp_str.contains("200 OK") || resp_str.contains("\"received\":true") {
                            success = true;
                        }
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
            } else {
                t.status = "failed".to_string();
            }
        }
        let _ = app_clone.emit(BRIDGE_TRANSFER_EVENT, get_transfers());
    });

    Ok(())
}

pub fn beam_file_from_path(
    app: AppHandle,
    file_path: &str,
    target_ip: Option<String>,
) -> Result<(), String> {
    use base64::Engine;
    let p = std::path::Path::new(file_path);
    if !p.exists() {
        return Err(format!("File does not exist: {}", file_path));
    }
    let file_name = p
        .file_name()
        .and_then(|n| n.to_str())
        .unwrap_or("beamed_file.bin")
        .to_string();

    let bytes = std::fs::read(p).map_err(|e| e.to_string())?;
    let file_size = bytes.len() as u64;
    let b64 = base64::engine::general_purpose::STANDARD.encode(&bytes);

    beam_file_to_device(
        app,
        "Motorola Edge 50 Pro".to_string(),
        file_name,
        file_size,
        b64,
        target_ip,
    )
}

pub fn broadcast_clipboard_to_mobile(text: &str) {
    let devices = DEVICES.lock().unwrap().clone();
    let text_owned = text.to_string();
    let token = get_bridge_token();

    std::thread::spawn(move || {
        for dev in devices {
            if dev.status == "connected" && !dev.ip.is_empty() {
                use std::net::ToSocketAddrs;
                let addr = format!("{}:{}", dev.ip, dev.port);
                if let Ok(mut addrs) = addr.to_socket_addrs() {
                    if let Some(sock) = addrs.next() {
                        if let Ok(mut stream) = std::net::TcpStream::connect_timeout(&sock, Duration::from_millis(2000)) {
                            let _ = stream.set_read_timeout(Some(Duration::from_secs(3)));
                            let _ = stream.set_write_timeout(Some(Duration::from_secs(3)));

                            let timestamp = current_time_ms();
                            let nonce = format!("clip-{}-{}", timestamp, std::process::id());
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
    let ip = target_ip.filter(|s| !s.is_empty()).unwrap_or_else(|| {
        let devs = DEVICES.lock().unwrap().clone();
        devs.iter()
            .find(|d| d.status == "connected" && !d.ip.is_empty() && d.ip != "192.168.1.3")
            .map(|d| d.ip.clone())
            .or_else(|| devs.first().map(|d| d.ip.clone()))
            .unwrap_or_else(|| "127.0.0.1".to_string())
    });

    let addr = format!("{}:53391", ip);
    let token = get_bridge_token();
    let timestamp = current_time_ms();
    let nonce = format!("ctrl-{}", timestamp);

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

    std::thread::spawn(move || {
        use std::net::ToSocketAddrs;
        if let Ok(mut addrs) = addr.to_socket_addrs() {
            if let Some(sock) = addrs.next() {
                if let Ok(mut stream) = std::net::TcpStream::connect_timeout(&sock, Duration::from_millis(1500)) {
                    let _ = stream.set_write_timeout(Some(Duration::from_secs(3)));
                    let _ = stream.write_all(req.as_bytes());
                }
            }
        }
    });

    Ok(())
}

fn write_json_response(stream: &mut std::net::TcpStream, json_body: &str) {
    let resp = format!(
        "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{}",
        json_body.len(),
        json_body
    );
    let _ = stream.write_all(resp.as_bytes());
    let _ = stream.flush();
}

fn write_error_response(stream: &mut std::net::TcpStream, code: u16, status: &str, message: &str) {
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

fn is_authorized(headers: &str, peer_ip: &str) -> bool {
    let expected = get_bridge_token();
    if let Some(token) = get_header_value(headers, "X-Jarvis-Auth-Token") {
        if token == expected {
            return true;
        }
    }
    if let Some(auth) = get_header_value(headers, "Authorization") {
        if let Some(bearer) = auth.strip_prefix("Bearer ") {
            if bearer.trim() == expected {
                return true;
            }
        }
    }
    // Allow loopback for internal status queries
    if peer_ip == "127.0.0.1" || peer_ip == "::1" {
        return true;
    }
    false
}

fn is_valid_timestamp(headers: &str) -> bool {
    if let Some(ts_str) = get_header_value(headers, "X-Jarvis-Timestamp") {
        if let Ok(ts) = ts_str.parse::<u64>() {
            let now = current_time_ms();
            let diff = if now >= ts { now - ts } else { ts - now };
            if diff > 60_000 {
                return false;
            }
        }
    }
    true
}

fn is_valid_nonce(headers: &str) -> bool {
    if let Some(nonce) = get_header_value(headers, "X-Jarvis-Nonce") {
        if !nonce.is_empty() {
            return check_and_record_nonce(nonce);
        }
    }
    true
}

pub fn start_livebridge_service(app: AppHandle) {
    let app_handle = Arc::new(app);

    // 1. Background UDP Beacon Broadcast & Discovery
    let _app_udp = app_handle.clone();
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
            "version": "1.0.0"
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
                            let dev_name = json.get("name").and_then(|n| n.as_str()).unwrap_or("Motorola Edge 50 Pro");
                            let mut devs = DEVICES.lock().unwrap();
                            devs.retain(|d| d.ip != ip && d.ip != "192.168.1.3");
                            devs.insert(0, BridgeDevice {
                                id: format!("android-{}", ip.replace('.', "-")),
                                name: dev_name.to_string(),
                                device_type: "android".to_string(),
                                ip: ip.clone(),
                                port: 53391,
                                battery_percent: 100,
                                is_charging: true,
                                wifi_ssid: "Wi-Fi".to_string(),
                                status: "connected".to_string(),
                                last_seen_ms: current_time_ms(),
                            });
                            let _ = app_udp_rx.emit(BRIDGE_DEVICE_EVENT, devs.clone());
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
                log::info!("LiveBridge TCP port 53391 busy or unavailable ({}), will use client mode", e);
                return;
            }
        };

        for stream in listener.incoming() {
            if let Ok(mut stream) = stream {
                let _ = stream.set_read_timeout(Some(Duration::from_secs(8)));
                let _ = stream.set_write_timeout(Some(Duration::from_secs(8)));

                let app = app_tcp.clone();
                std::thread::spawn(move || {
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
                    let peer_ip = stream.peer_addr().map(|a| a.ip().to_string()).unwrap_or_default();

                    let content_len = get_header_value(&header_str, "content-length")
                        .and_then(|v| v.parse::<usize>().ok())
                        .unwrap_or(0);

                    // Replay protection: check timestamp
                    if !is_valid_timestamp(&header_str) {
                        write_error_response(&mut stream, 401, "Unauthorized", "Request timestamp expired or skewed beyond acceptable window");
                        return;
                    }

                    // Replay protection: check nonce
                    if !is_valid_nonce(&header_str) {
                        write_error_response(&mut stream, 409, "Conflict", "Replay protection: Message nonce already processed");
                        return;
                    }

                    if req_line.starts_with("GET /api/status") {
                        if !peer_ip.is_empty() && peer_ip != "127.0.0.1" {
                            let mut devs = DEVICES.lock().unwrap();
                            if !devs.iter().any(|d| d.ip == peer_ip) {
                                devs.retain(|d| d.ip != "192.168.1.3");
                                devs.insert(0, BridgeDevice {
                                    id: format!("android-{}", peer_ip.replace('.', "-")),
                                    name: "Motorola Edge 50 Pro".to_string(),
                                    device_type: "android".to_string(),
                                    ip: peer_ip.clone(),
                                    port: 53391,
                                    battery_percent: 100,
                                    is_charging: true,
                                    wifi_ssid: "Wi-Fi".to_string(),
                                    status: "connected".to_string(),
                                    last_seen_ms: current_time_ms(),
                                });
                                let _ = app.emit(BRIDGE_DEVICE_EVENT, devs.clone());
                            }
                        }
                        write_json_response(&mut stream, "{\"status\":\"ok\",\"service\":\"JARVIS LiveBridge\",\"authenticated\":true}");
                        return;
                    }

                    if req_line.starts_with("POST /api/pair") {
                        let token = get_bridge_token();
                        let resp = serde_json::json!({
                            "status": "paired",
                            "authToken": token,
                            "protocolVersion": "1.0.0",
                            "deviceId": "jarvis-desktop-primary"
                        }).to_string();
                        write_json_response(&mut stream, &resp);
                        return;
                    }

                    // Enforce payload size limit on JSON endpoints
                    if content_len > MAX_JSON_PAYLOAD_SIZE && !req_line.starts_with("POST /api/transfer") {
                        write_error_response(&mut stream, 413, "Payload Too Large", "Payload exceeds allowed size boundary of 1 MB");
                        return;
                    }

                    // Authenticate all protected endpoints
                    if !is_authorized(&header_str, &peer_ip) {
                        write_error_response(&mut stream, 401, "Unauthorized", "Valid X-Jarvis-Auth-Token or Authorization bearer token required");
                        return;
                    }

                    // Read remaining body data
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
                            let dev_id = json.get("deviceId").and_then(|v| v.as_str()).unwrap_or("motorola-edge-50-pro");
                            let dev_name = json.get("deviceName").and_then(|v| v.as_str()).unwrap_or("Motorola Edge 50 Pro");
                            let battery = json.get("batteryPercent").and_then(|v| v.as_u64()).unwrap_or(100) as u8;
                            let charging = json.get("isCharging").and_then(|v| v.as_bool()).unwrap_or(false);
                            let ssid = json.get("wifiSsid").and_then(|v| v.as_str()).unwrap_or("Wi-Fi");

                            let real_ip = if !peer_ip.is_empty() { peer_ip.clone() } else { "127.0.0.1".to_string() };
                            let mut devs = DEVICES.lock().unwrap();
                            devs.retain(|d| d.id != dev_id && d.ip != "192.168.1.3");
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
                        if let Ok(json) = serde_json::from_str::<serde_json::Value>(&body_str) {
                            let origin = json.get("originDeviceId").and_then(|o| o.as_str()).unwrap_or("");
                            if origin != "jarvis-desktop-primary" {
                                if let Some(txt) = json.get("text").and_then(|t| t.as_str()) {
                                    crate::providers::clipboard::set_clipboard_text_from_external(txt);
                                }
                            }
                        }
                        write_json_response(&mut stream, "{\"copied\":true}");
                    } else if req_line.starts_with("POST /api/transfer/cancel") {
                        if let Ok(json) = serde_json::from_str::<serde_json::Value>(&body_str) {
                            if let Some(xfer_id) = json.get("transferId").and_then(|v| v.as_str()) {
                                cancel_transfer(xfer_id);
                                let _ = app.emit(BRIDGE_TRANSFER_EVENT, get_transfers());
                            }
                        }
                        write_json_response(&mut stream, "{\"cancelled\":true}");
                    } else if req_line.starts_with("POST /api/transfer") {
                        let mut dev_label = "Motorola Edge 50 Pro".to_string();
                        let mut actual_bytes_len = 0u64;
                        let mut checksum_verified = false;

                        let safe_name = if let Ok(json) = serde_json::from_str::<serde_json::Value>(&body_str) {
                            if let Some(orig) = json.get("originDeviceId").and_then(|o| o.as_str()) {
                                dev_label = orig.to_string();
                            }
                            let name = json.get("fileName").and_then(|f| f.as_str()).map(sanitize_filename).unwrap_or_else(|| "Mobile_File.bin".to_string());
                            
                            if let Some(b64) = json.get("dataBase64").and_then(|d| d.as_str()) {
                                use base64::Engine;
                                if let Ok(bytes) = base64::engine::general_purpose::STANDARD.decode(b64) {
                                    actual_bytes_len = bytes.len() as u64;

                                    if actual_bytes_len > MAX_FILE_TRANSFER_SIZE {
                                        write_error_response(&mut stream, 413, "Payload Too Large", "File exceeds maximum size limit of 2 GB");
                                        return;
                                    }

                                    let mut hasher = Sha256::new();
                                    hasher.update(&bytes);
                                    let computed_hash = format!("{:x}", hasher.finalize());

                                    let expected_hash = get_header_value(&header_str, "X-Jarvis-Checksum-Sha256")
                                        .map(|s| s.to_ascii_lowercase())
                                        .or_else(|| json.get("sha256Checksum").and_then(|c| c.as_str()).map(|s| s.to_ascii_lowercase()));

                                    if let Some(expected) = expected_hash {
                                        if expected != computed_hash {
                                            write_error_response(&mut stream, 400, "Bad Request", "Checksum verification failed: file content is tampered or corrupted");
                                            return;
                                        }
                                    }
                                    checksum_verified = true;

                                    // Write to temporary staging file then atomically rename
                                    let tmp_filename = format!("{}.{}.tmp", name, current_time_ms());
                                    let tmp_path = get_jarvis_download_dir().join(&tmp_filename);
                                    let final_path = get_jarvis_download_dir().join(&name);

                                    if std::fs::write(&tmp_path, &bytes).is_ok() {
                                        let _ = std::fs::rename(&tmp_path, &final_path);
                                    }
                                }
                            }
                            name
                        } else {
                            "Mobile_File.bin".to_string()
                        };

                        let transfer = BridgeTransfer {
                            id: format!("rx-{}", current_time_ms()),
                            device_name: dev_label,
                            file_name: safe_name,
                            file_size: actual_bytes_len.max(1024),
                            bytes_transferred: actual_bytes_len.max(1024),
                            is_upload: false,
                            progress: 1.0,
                            status: if checksum_verified || actual_bytes_len > 0 { "completed".to_string() } else { "failed".to_string() },
                            sha256_checksum: None,
                        };
                        add_transfer(transfer);
                        let _ = app.emit(BRIDGE_TRANSFER_EVENT, get_transfers());

                        write_json_response(&mut stream, "{\"received\":true}");
                    } else {
                        write_json_response(&mut stream, "{\"service\":\"JARVIS LiveBridge\",\"version\":\"1.0.0\"}");
                    }
                });
            }
        }
    });
}
