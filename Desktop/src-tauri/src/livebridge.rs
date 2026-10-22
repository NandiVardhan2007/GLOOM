use std::collections::HashMap;
use std::io::{Read, Write};
use std::net::{SocketAddr, TcpListener, UdpSocket};
use std::path::{Path, PathBuf};
use std::sync::Mutex;
use std::time::{Duration, SystemTime, UNIX_EPOCH};

use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use tauri::{AppHandle, Emitter};

use crate::bridge_tls;

const BRIDGE_PORT: u16 = 53391;
const DISCOVERY_PORT: u16 = 53390;
const MAX_STREAM_BYTES: u64 = 2 * 1024 * 1024 * 1024; // 2 GB

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct BridgeDevice {
    pub device_id: String,
    pub name: String,
    pub ip: String,
    pub port: u16,
    pub status: String,
    #[serde(skip_serializing)]
    pub auth_token: String,
    pub last_seen_ms: u64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct BridgeTransfer {
    pub id: String,
    pub file_name: String,
    pub file_size: u64,
    pub bytes_transferred: u64,
    pub is_upload: bool,
    pub progress: f32,
    pub status: String,
}

static DEVICES: Mutex<Option<HashMap<String, BridgeDevice>>> = Mutex::new(None);
static ACTIVE_TRANSFERS: Mutex<Option<HashMap<String, BridgeTransfer>>> = Mutex::new(None);

fn lock<T>(m: &Mutex<T>) -> std::sync::MutexGuard<'_, T> {
    m.lock().unwrap_or_else(|p| p.into_inner())
}

pub fn current_time_ms() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default()
        .as_millis() as u64
}

pub fn get_gloom_download_dir() -> PathBuf {
    let p = if let Ok(profile) = std::env::var("USERPROFILE") {
        PathBuf::from(profile).join("Downloads").join("GLOOM")
    } else {
        std::env::temp_dir().join("GLOOM")
    };
    let _ = std::fs::create_dir_all(&p);
    p
}

pub fn sanitize_filename(raw: &str) -> String {
    let file_name = Path::new(raw)
        .file_name()
        .and_then(|n| n.to_str())
        .unwrap_or("received_file.bin");

    let clean: String = file_name
        .chars()
        .map(|c| match c {
            '/' | '\\' | ':' | '*' | '?' | '"' | '<' | '>' | '|' | '\0' => '_',
            other => other,
        })
        .collect();

    let trimmed = clean.trim();
    if trimmed.is_empty() {
        "received_file.bin".to_string()
    } else {
        trimmed.to_string()
    }
}

pub fn get_known_devices() -> Vec<BridgeDevice> {
    let guard = lock(&DEVICES);
    if let Some(map) = guard.as_ref() {
        map.values().cloned().collect()
    } else {
        Vec::new()
    }
}

fn update_device(dev: BridgeDevice, app: &AppHandle) {
    let mut guard = lock(&DEVICES);
    let map = guard.get_or_insert_with(HashMap::new);
    let key = if !dev.ip.is_empty() {
        dev.ip.clone()
    } else {
        dev.device_id.clone()
    };
    map.insert(key, dev);
    drop(guard);
    let devs = get_known_devices();
    let _ = app.emit("bridge-devices-updated", &devs);
}

pub fn emit_transfer_progress(app: &AppHandle, transfer: BridgeTransfer) {
    let mut guard = lock(&ACTIVE_TRANSFERS);
    let map = guard.get_or_insert_with(HashMap::new);
    map.insert(transfer.id.clone(), transfer.clone());
    drop(guard);
    let _ = app.emit("bridge-transfer-progress", &transfer);
}

pub fn emit_transfer_complete(app: &AppHandle, transfer: BridgeTransfer) {
    let mut guard = lock(&ACTIVE_TRANSFERS);
    if let Some(map) = guard.as_mut() {
        map.remove(&transfer.id);
    }
    drop(guard);
    let _ = app.emit("bridge-transfer-complete", &transfer);
}

pub fn start_livebridge_service(app: AppHandle) {
    // 1. Spawning UDP Discovery Broadcaster & Listener (Port 53390)
    let app_clone1 = app.clone();
    std::thread::spawn(move || {
        let socket = match UdpSocket::bind(format!("0.0.0.0:{}", DISCOVERY_PORT)) {
            Ok(s) => s,
            Err(e) => {
                eprintln!("Could not bind UDP discovery socket: {}", e);
                return;
            }
        };
        let _ = socket.set_broadcast(true);
        let _ = socket.set_read_timeout(Some(Duration::from_millis(1500)));

        let mut buf = [0u8; 2048];
        let mut last_beacon = SystemTime::now() - Duration::from_secs(10);

        loop {
            // Periodically broadcast desktop beacon
            if last_beacon.elapsed().unwrap_or_default() >= Duration::from_secs(3) {
                last_beacon = SystemTime::now();
                let fingerprint = bridge_tls::local_fingerprint();
                let beacon_json = serde_json::json!({
                    "type": "gloom_beacon",
                    "port": BRIDGE_PORT,
                    "name": "GLOOM Desktop",
                    "tlsFingerprint": fingerprint
                }).to_string();

                let _ = socket.send_to(
                    beacon_json.as_bytes(),
                    format!("255.255.255.255:{}", DISCOVERY_PORT),
                );
            }

            // Listen for mobile beacons
            if let Ok((len, src)) = socket.recv_from(&mut buf) {
                if let Ok(msg) = std::str::from_utf8(&buf[..len]) {
                    if let Ok(val) = serde_json::from_str::<serde_json::Value>(msg) {
                        let t = val.get("type").and_then(|v| v.as_str()).unwrap_or("");
                        if t == "gloom_mobile_beacon" {
                            let device_id = val.get("deviceId").and_then(|v| v.as_str()).unwrap_or("mobile").to_string();
                            let name = val.get("name").and_then(|v| v.as_str()).unwrap_or("Mobile Island").to_string();
                            let port = val.get("port").and_then(|v| v.as_u64()).unwrap_or(53391) as u16;
                            let auth_token = val.get("authToken").and_then(|v| v.as_str()).unwrap_or("").to_string();
                            let ip = src.ip().to_string();

                            update_device(
                                BridgeDevice {
                                    device_id,
                                    name,
                                    ip,
                                    port,
                                    status: "discovered".to_string(),
                                    auth_token,
                                    last_seen_ms: current_time_ms(),
                                },
                                &app_clone1,
                            );
                        }
                    }
                }
            }
        }
    });

    // 2. TCP Server on Port 53391 with TLS & HTTP Fallback
    let app_clone2 = app.clone();
    std::thread::spawn(move || {
        let listener = match TcpListener::bind(format!("0.0.0.0:{}", BRIDGE_PORT)) {
            Ok(l) => l,
            Err(e) => {
                eprintln!("Failed to bind LiveBridge TCP listener on port {}: {}", BRIDGE_PORT, e);
                return;
            }
        };

        println!("GLOOM Desktop LiveBridge listening on TCP port {}", BRIDGE_PORT);

        for stream in listener.incoming() {
            let mut stream = match stream {
                Ok(s) => s,
                Err(_) => continue,
            };
            let app_handle = app_clone2.clone();

            std::thread::spawn(move || {
                let _ = stream.set_read_timeout(Some(Duration::from_secs(15)));
                let _ = stream.set_write_timeout(Some(Duration::from_secs(15)));
                let peer_addr = stream.peer_addr().ok();
                let peer_ip = peer_addr.map(|a| a.ip().to_string()).unwrap_or_default();

                // Inspect first byte to check if TLS ClientHello (0x16)
                let mut peek_buf = [0u8; 1];
                let is_tls = match stream.peek(&mut peek_buf) {
                    Ok(1) => peek_buf[0] == 0x16,
                    _ => false,
                };

                if is_tls {
                    if let Ok(server_cfg) = bridge_tls::server_config() {
                        if let Ok(tls_conn) = rustls::ServerConnection::new(server_cfg) {
                            let mut tls_stream = rustls::StreamOwned::new(tls_conn, stream);
                            handle_bridge_connection(&mut tls_stream, &peer_ip, &app_handle);
                            return;
                        }
                    }
                }

                // Fallback cleartext stream
                handle_bridge_connection(&mut stream, &peer_ip, &app_handle);
            });
        }
    });
}

fn handle_bridge_connection<S: Read + Write>(stream: &mut S, peer_ip: &str, app: &AppHandle) {
    // Read headers safely
    let mut header_bytes = Vec::with_capacity(4096);
    let mut buf = [0u8; 1];
    let mut prev3 = 0u8;
    let mut prev2 = 0u8;
    let mut prev1 = 0u8;

    while header_bytes.len() < 16384 {
        match stream.read(&mut buf) {
            Ok(1) => {
                let b = buf[0];
                header_bytes.push(b);
                if prev3 == b'\r' && prev2 == b'\n' && prev1 == b'\r' && b == b'\n' {
                    break;
                }
                prev3 = prev2;
                prev2 = prev1;
                prev1 = b;
            }
            _ => return,
        }
    }

    let header_str = String::from_utf8_lossy(&header_bytes);
    let mut lines = header_str.lines();
    let request_line = lines.next().unwrap_or("").trim();
    let mut headers = HashMap::new();
    for line in lines {
        if let Some((k, v)) = line.split_once(':') {
            headers.insert(k.trim().to_ascii_lowercase(), v.trim().to_string());
        }
    }

    let content_len = headers
        .get("content-length")
        .and_then(|v| v.parse::<u64>().ok())
        .unwrap_or(0);

    let auth_token = headers
        .get("x-gloom-auth-token")
        .map(|s| s.as_str())
        .or_else(|| headers.get("authorization").and_then(|a| a.strip_prefix("Bearer ")))
        .map(|s| s.trim())
        .unwrap_or("");

    // 1. Status Check
    if request_line.starts_with("GET /api/status") {
        let fingerprint = bridge_tls::local_fingerprint();
        let resp_json = serde_json::json!({
            "service": "GLOOM LiveBridge",
            "version": "1.6.0",
            "status": "ok",
            "tlsFingerprint": fingerprint
        }).to_string();

        let resp = format!(
            "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{}",
            resp_json.len(),
            resp_json
        );
        let _ = stream.write_all(resp.as_bytes());
        let _ = stream.flush();
        return;
    }

    // 2. Pairing Confirm
    if request_line.starts_with("POST /api/pair/confirm") {
        let mut body = vec![0u8; content_len as usize];
        let _ = stream.read_exact(&mut body);
        let (client_cert, client_key, client_fp) = bridge_tls::issue_client_identity()
            .unwrap_or_else(|_| (String::new(), String::new(), String::new()));

        let token = format!("gloom-token-{}", current_time_ms());
        if let Ok(json) = serde_json::from_slice::<serde_json::Value>(&body) {
            let dev_id = json.get("clientDeviceId").and_then(|v| v.as_str()).unwrap_or("Mobile").to_string();
            let name = json.get("clientDeviceName").and_then(|v| v.as_str()).unwrap_or("Mobile Island").to_string();
            update_device(
                BridgeDevice {
                    device_id: dev_id,
                    name,
                    ip: peer_ip.to_string(),
                    port: BRIDGE_PORT,
                    status: "connected".to_string(),
                    auth_token: token.clone(),
                    last_seen_ms: current_time_ms(),
                },
                app,
            );
        }

        let resp_json = serde_json::json!({
            "authToken": token,
            "serverTlsFingerprint": bridge_tls::local_fingerprint(),
            "clientCertPem": client_cert,
            "clientKeyPem": client_key,
            "clientTlsFingerprint": client_fp
        }).to_string();

        let resp = format!(
            "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{}",
            resp_json.len(),
            resp_json
        );
        let _ = stream.write_all(resp.as_bytes());
        let _ = stream.flush();
        return;
    }

    // 3. Telemetry Reporting
    if request_line.starts_with("POST /api/telemetry") {
        let mut body = vec![0u8; content_len as usize];
        let _ = stream.read_exact(&mut body);
        if let Ok(val) = serde_json::from_slice::<serde_json::Value>(&body) {
            let dev_id = val.get("deviceId").and_then(|v| v.as_str()).unwrap_or("Mobile").to_string();
            let name = val.get("deviceName").and_then(|v| v.as_str()).unwrap_or("Mobile Island").to_string();
            update_device(
                BridgeDevice {
                    device_id: dev_id,
                    name,
                    ip: peer_ip.to_string(),
                    port: BRIDGE_PORT,
                    status: "connected".to_string(),
                    auth_token: auth_token.to_string(),
                    last_seen_ms: current_time_ms(),
                },
                app,
            );
        }

        let resp_json = serde_json::json!({ "status": "ok" }).to_string();
        let resp = format!(
            "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{}",
            resp_json.len(),
            resp_json
        );
        let _ = stream.write_all(resp.as_bytes());
        let _ = stream.flush();
        return;
    }

    // 4. File Beaming Receiver (/api/beam/stream)
    if request_line.starts_with("POST /api/beam/stream") {
        if content_len == 0 || content_len > MAX_STREAM_BYTES {
            let err_json = serde_json::json!({ "error": "Invalid or missing file size" }).to_string();
            let resp = format!(
                "HTTP/1.1 400 Bad Request\r\nContent-Type: application/json\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{}",
                err_json.len(),
                err_json
            );
            let _ = stream.write_all(resp.as_bytes());
            return;
        }

        let raw_name = headers
            .get("x-file-name")
            .cloned()
            .unwrap_or_else(|| "Mobile_File.bin".to_string());
        let safe_name = sanitize_filename(&raw_name);
        let download_dir = get_gloom_download_dir();
        let mut dest_file = download_dir.join(&safe_name);

        // Deduplicate filename if already exists
        if dest_file.exists() {
            let file_stem = dest_file.file_stem().and_then(|s| s.to_str()).unwrap_or("file");
            let file_ext = dest_file.extension().and_then(|s| s.to_str()).unwrap_or("");
            let new_name = if file_ext.is_empty() {
                format!("{}_{}", file_stem, current_time_ms())
            } else {
                format!("{}_{}.{}", file_stem, current_time_ms(), file_ext)
            };
            dest_file = download_dir.join(new_name);
        }

        let tmp_file = download_dir.join(format!("{}.{}.tmp", safe_name, current_time_ms()));
        let mut out = match std::fs::File::create(&tmp_file) {
            Ok(f) => f,
            Err(e) => {
                let err_json = serde_json::json!({ "error": format!("Cannot create file: {}", e) }).to_string();
                let resp = format!(
                    "HTTP/1.1 500 Internal Server Error\r\nContent-Type: application/json\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{}",
                    err_json.len(),
                    err_json
                );
                let _ = stream.write_all(resp.as_bytes());
                return;
            }
        };

        let transfer_id = format!("beam-in-{}", current_time_ms());
        let mut transfer = BridgeTransfer {
            id: transfer_id.clone(),
            file_name: safe_name.clone(),
            file_size: content_len,
            bytes_transferred: 0,
            is_upload: false,
            progress: 0.05,
            status: "transferring".to_string(),
        };
        emit_transfer_progress(app, transfer.clone());

        let mut buffer = [0u8; 65536];
        let mut total_read = 0u64;
        let mut hasher = Sha256::new();
        let mut last_progress_time = SystemTime::now();

        while total_read < content_len {
            let to_read = (content_len - total_read).min(buffer.len() as u64) as usize;
            match stream.read(&mut buffer[..to_read]) {
                Ok(0) => break,
                Ok(n) => {
                    if out.write_all(&buffer[..n]).is_err() {
                        break;
                    }
                    hasher.update(&buffer[..n]);
                    total_read += n as u64;

                    if last_progress_time.elapsed().unwrap_or_default() >= Duration::from_millis(250)
                        || total_read == content_len
                    {
                        last_progress_time = SystemTime::now();
                        transfer.bytes_transferred = total_read;
                        transfer.progress = total_read as f32 / content_len as f32;
                        emit_transfer_progress(app, transfer.clone());
                    }
                }
                Err(_) => break,
            }
        }

        let _ = out.flush();
        drop(out);

        if total_read == content_len {
            let _ = std::fs::rename(&tmp_file, &dest_file);
            transfer.progress = 1.0;
            transfer.bytes_transferred = total_read;
            transfer.status = "completed".to_string();
            emit_transfer_complete(app, transfer);

            let final_path_str = dest_file.to_string_lossy().to_string();
            let _ = app.emit(
                "bridge-file-received",
                serde_json::json!({
                    "path": final_path_str,
                    "name": safe_name,
                    "size": total_read
                }),
            );

            let resp_json = serde_json::json!({
                "received": true,
                "status": "ok",
                "fileName": safe_name
            }).to_string();
            let resp = format!(
                "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{}",
                resp_json.len(),
                resp_json
            );
            let _ = stream.write_all(resp.as_bytes());
        } else {
            let _ = std::fs::remove_file(&tmp_file);
            transfer.status = "failed".to_string();
            emit_transfer_complete(app, transfer);

            let err_json = serde_json::json!({ "error": "Incomplete stream" }).to_string();
            let resp = format!(
                "HTTP/1.1 500 Internal Server Error\r\nContent-Type: application/json\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{}",
                err_json.len(),
                err_json
            );
            let _ = stream.write_all(resp.as_bytes());
        }
        let _ = stream.flush();
        return;
    }

    // Default response
    let def_json = serde_json::json!({ "service": "GLOOM LiveBridge", "version": "1.6.0" }).to_string();
    let resp = format!(
        "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{}",
        def_json.len(),
        def_json
    );
    let _ = stream.write_all(resp.as_bytes());
    let _ = stream.flush();
}

pub fn stream_file_to_companion(
    app: AppHandle,
    file_path: &str,
    target_ip: Option<String>,
) -> Result<(), String> {
    let p = Path::new(file_path);
    if !p.exists() {
        return Err(format!("File does not exist: {}", file_path));
    }
    let mut file = std::fs::File::open(p).map_err(|e| format!("Cannot open file: {}", e))?;
    let file_size = file.metadata().map(|m| m.len()).unwrap_or(0);
    let safe_name = p
        .file_name()
        .and_then(|n| n.to_str())
        .map(sanitize_filename)
        .unwrap_or_else(|| "beamed_file.bin".to_string());

    let (ip, auth_token) = match target_ip.filter(|s| !s.is_empty()) {
        Some(explicit_ip) => {
            let token = {
                let guard = lock(&DEVICES);
                guard
                    .as_ref()
                    .and_then(|m| m.get(&explicit_ip))
                    .map(|d| d.auth_token.clone())
                    .unwrap_or_default()
            };
            (explicit_ip, token)
        }
        None => {
            let devs = get_known_devices();
            let connected = devs
                .iter()
                .filter(|d| !d.ip.is_empty())
                .max_by_key(|d| d.last_seen_ms);
            match connected {
                Some(d) => (d.ip.clone(), d.auth_token.clone()),
                None => {
                    return Err(
                        "No companion device discovered. Ensure mobile GLOOM is open on the same Wi-Fi."
                            .to_string(),
                    )
                }
            }
        }
    };

    let transfer_id = format!("beam-out-{}", current_time_ms());
    let mut transfer = BridgeTransfer {
        id: transfer_id.clone(),
        file_name: safe_name.clone(),
        file_size,
        bytes_transferred: 0,
        is_upload: true,
        progress: 0.05,
        status: "transferring".to_string(),
    };
    emit_transfer_progress(&app, transfer.clone());

    let app_handle = app.clone();
    std::thread::spawn(move || {
        let timestamp = current_time_ms();
        let nonce = format!("{:016x}", current_time_ms());

        let req_headers = format!(
            "POST /api/beam/stream HTTP/1.1\r\n\
             Host: {}:{}\r\n\
             X-File-Name: {}\r\n\
             Content-Length: {}\r\n\
             Content-Type: application/octet-stream\r\n\
             X-Gloom-Auth-Token: {}\r\n\
             X-Gloom-Timestamp: {}\r\n\
             X-Gloom-Nonce: {}\r\n\
             Connection: close\r\n\r\n",
            ip, BRIDGE_PORT, safe_name, file_size, auth_token, timestamp, nonce
        );

        let sock_addr: SocketAddr = match format!("{}:{}", ip, BRIDGE_PORT).parse() {
            Ok(a) => a,
            Err(e) => {
                transfer.status = "failed".to_string();
                emit_transfer_complete(&app_handle, transfer);
                eprintln!("Invalid socket address for mobile: {}", e);
                return;
            }
        };

        let tcp = match std::net::TcpStream::connect_timeout(&sock_addr, Duration::from_secs(6)) {
            Ok(s) => s,
            Err(e) => {
                transfer.status = "failed".to_string();
                emit_transfer_complete(&app_handle, transfer);
                eprintln!("Failed to connect to mobile stream socket: {}", e);
                return;
            }
        };

        let _ = tcp.set_read_timeout(Some(Duration::from_secs(30)));
        let _ = tcp.set_write_timeout(Some(Duration::from_secs(30)));

        // Attempt TLS connection to Mobile
        let client_cfg = bridge_tls::client_config(None).unwrap();
        let server_name = rustls::pki_types::ServerName::try_from("gloom-android-bridge")
            .unwrap_or_else(|_| rustls::pki_types::ServerName::try_from("localhost").unwrap())
            .to_owned();

        if let Ok(tls_conn) = rustls::ClientConnection::new(client_cfg, server_name) {
            let mut tls_stream = rustls::StreamOwned::new(tls_conn, tcp);
            if tls_stream.write_all(req_headers.as_bytes()).is_ok() {
                let mut buffer = [0u8; 65536];
                let mut sent_bytes = 0u64;
                let mut last_progress_time = SystemTime::now();

                loop {
                    match file.read(&mut buffer) {
                        Ok(0) => break,
                        Ok(n) => {
                            if tls_stream.write_all(&buffer[..n]).is_err() {
                                transfer.status = "failed".to_string();
                                emit_transfer_complete(&app_handle, transfer);
                                return;
                            }
                            sent_bytes += n as u64;
                            if last_progress_time.elapsed().unwrap_or_default() >= Duration::from_millis(250)
                                || sent_bytes == file_size
                            {
                                last_progress_time = SystemTime::now();
                                transfer.bytes_transferred = sent_bytes;
                                transfer.progress = if file_size > 0 {
                                    sent_bytes as f32 / file_size as f32
                                } else {
                                    1.0
                                };
                                emit_transfer_progress(&app_handle, transfer.clone());
                            }
                        }
                        Err(_) => {
                            transfer.status = "failed".to_string();
                            emit_transfer_complete(&app_handle, transfer);
                            return;
                        }
                    }
                }
                let _ = tls_stream.flush();

                // Read response
                let mut resp_buf = [0u8; 1024];
                let _ = tls_stream.read(&mut resp_buf);
                transfer.status = "completed".to_string();
                transfer.progress = 1.0;
                emit_transfer_complete(&app_handle, transfer);
                return;
            }
        }

        // Fallback cleartext if TLS rejected
        let tcp2 = match std::net::TcpStream::connect_timeout(&sock_addr, Duration::from_secs(6)) {
            Ok(s) => s,
            Err(e) => {
                transfer.status = "failed".to_string();
                emit_transfer_complete(&app_handle, transfer);
                eprintln!("Failed to connect cleartext fallback socket: {}", e);
                return;
            }
        };
        let mut stream = tcp2;
        if stream.write_all(req_headers.as_bytes()).is_ok() {
            let mut buffer = [0u8; 65536];
            let mut _sent_bytes = 0u64;
            loop {
                match file.read(&mut buffer) {
                    Ok(0) => break,
                    Ok(n) => {
                        if stream.write_all(&buffer[..n]).is_err() {
                            break;
                        }
                        _sent_bytes += n as u64;
                    }
                    Err(_) => break,
                }
            }
            let _ = stream.flush();
            transfer.status = "completed".to_string();
            transfer.progress = 1.0;
            emit_transfer_complete(&app_handle, transfer);
        } else {
            transfer.status = "failed".to_string();
            emit_transfer_complete(&app_handle, transfer);
        }
    });

    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_filename_sanitization() {
        assert_eq!(sanitize_filename("../../etc/passwd"), "passwd");
        assert_eq!(sanitize_filename("..\\..\\windows\\system32\\calc.exe"), "calc.exe");
        assert_eq!(sanitize_filename("valid_report.pdf"), "valid_report.pdf");
        assert_eq!(sanitize_filename("image:test*name?.png"), "image_test_name_.png");
        assert_eq!(sanitize_filename(""), "received_file.bin");
    }

    #[test]
    fn test_download_directory_creation() {
        let dir = get_gloom_download_dir();
        assert!(dir.exists());
        assert!(dir.to_string_lossy().contains("GLOOM"));
    }

    #[test]
    fn test_transfer_model_serialization() {
        let transfer = BridgeTransfer {
            id: "tx-1".to_string(),
            file_name: "photo.jpg".to_string(),
            file_size: 1024,
            bytes_transferred: 512,
            is_upload: true,
            progress: 0.5,
            status: "transferring".to_string(),
        };
        let json = serde_json::to_string(&transfer).expect("serialization succeeds");
        assert!(json.contains("photo.jpg"));
        assert!(json.contains("\"progress\":0.5"));
    }
}
