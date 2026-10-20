use serde::{Deserialize, Serialize};

pub const PROTOCOL_VERSION: &str = "1.0.0";

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "snake_case")]
pub enum MessageType {
    // Device & Connectivity
    DeviceDiscovery,
    DevicePresence,
    DevicePairRequest,
    DevicePairResponse,
    DeviceUnpair,
    ConnectionStatus,

    // Core Sharing
    ClipboardUpdated,
    FileTransferRequest,
    FileTransferProgress,
    FileTransferCompleted,
    FileTransferFailed,

    // Live Activities & Notifications
    NotificationReceived,
    NotificationAction,
    MediaStateChanged,

    // AI & Automation
    AiTaskStarted,
    AiTaskProgress,
    AiTaskCompleted,
    AiTaskFailed,
    AiApprovalRequired,
    AiApprovalResponse,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "snake_case")]
pub enum ConnectionState {
    Disconnected,
    Discovering,
    PairingRequested,
    Connected,
    Reconnecting,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct JarvisMessage<T = serde_json::Value> {
    pub protocol_version: String,
    pub id: String,
    pub message_type: MessageType,
    pub origin_device_id: String,
    pub target_device_id: Option<String>,
    pub timestamp_ms: u64,
    pub payload: T,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct DevicePresencePayload {
    pub device_id: String,
    pub device_name: String,
    pub device_type: String, // "windows" | "android" | "ios"
    pub battery_percent: u8,
    pub is_charging: bool,
    pub wifi_ssid: String,
    pub ip_address: String,
    pub port: u16,
    pub protocol_version: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct PairingPayload {
    pub device_id: String,
    pub device_name: String,
    pub public_key: String,
    pub pairing_code: String,
    pub accepted: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ClipboardPayload {
    pub text: String,
    pub mime_type: String,
    pub timestamp_ms: u64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ConnectionStatusPayload {
    pub state: ConnectionState,
    pub paired_device_id: Option<String>,
    pub paired_device_name: Option<String>,
    pub ping_ms: u32,
    pub last_heartbeat_ms: u64,
}
