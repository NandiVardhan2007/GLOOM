pub mod schema;

use std::collections::{HashSet, VecDeque};
use std::sync::Mutex;
use std::time::{Duration, SystemTime, UNIX_EPOCH};
pub use schema::*;

const MAX_HISTORY_IDS: usize = 1000;

pub struct ProtocolEngine {
    seen_ids: Mutex<HashSet<String>>,
    id_order: Mutex<VecDeque<String>>,
    local_device_id: String,
    local_device_name: String,
    current_status: Mutex<ConnectionStatusPayload>,
}

impl ProtocolEngine {
    pub fn new(local_device_id: String, local_device_name: String) -> Self {
        Self {
            seen_ids: Mutex::new(HashSet::new()),
            id_order: Mutex::new(VecDeque::new()),
            local_device_id,
            local_device_name,
            current_status: Mutex::new(ConnectionStatusPayload {
                state: ConnectionState::Discovering,
                paired_device_id: None,
                paired_device_name: None,
                ping_ms: 0,
                last_heartbeat_ms: Self::current_time_ms(),
            }),
        }
    }

    pub fn current_time_ms() -> u64 {
        SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap_or(Duration::from_secs(0))
            .as_millis() as u64
    }

    pub fn local_device_id(&self) -> &str {
        &self.local_device_id
    }

    pub fn local_device_name(&self) -> &str {
        &self.local_device_name
    }

    /// Validates whether a message is valid, supported, and has not been seen before.
    pub fn is_duplicate_or_self(&self, message_id: &str, origin_id: &str) -> bool {
        // Drop messages that originated from ourselves (prevents echo loops)
        if origin_id == self.local_device_id {
            return true;
        }

        let mut seen = self.seen_ids.lock().unwrap();
        if seen.contains(message_id) {
            return true;
        }

        // Add to deduplication sliding window
        seen.insert(message_id.to_string());
        let mut order = self.id_order.lock().unwrap();
        order.push_back(message_id.to_string());

        if order.len() > MAX_HISTORY_IDS {
            if let Some(oldest) = order.pop_front() {
                seen.remove(&oldest);
            }
        }

        false
    }

    /// Verifies protocol version compatibility.
    pub fn check_version(&self, version: &str) -> bool {
        // Supports 1.x.x
        version.starts_with("1.")
    }

    /// Builds a typed outgoing JARVIS protocol message.
    pub fn build_message<T: serde::Serialize>(
        &self,
        message_type: MessageType,
        target_device_id: Option<String>,
        payload: T,
    ) -> JarvisMessage<serde_json::Value> {
        let msg_id = format!(
            "msg-{}-{}",
            Self::current_time_ms(),
            std::process::id()
        );

        JarvisMessage {
            protocol_version: PROTOCOL_VERSION.to_string(),
            id: msg_id,
            message_type,
            origin_device_id: self.local_device_id.clone(),
            target_device_id,
            timestamp_ms: Self::current_time_ms(),
            payload: serde_json::to_value(payload).unwrap_or(serde_json::Value::Null),
        }
    }

    pub fn update_presence(&self, payload: &DevicePresencePayload, measured_ping_ms: Option<u32>) {
        let mut status = self.current_status.lock().unwrap();
        status.state = ConnectionState::Connected;
        status.paired_device_id = Some(payload.device_id.clone());
        status.paired_device_name = Some(payload.device_name.clone());
        status.last_heartbeat_ms = Self::current_time_ms();
        status.ping_ms = measured_ping_ms.unwrap_or(0);
    }

    pub fn set_disconnected(&self) {
        let mut status = self.current_status.lock().unwrap();
        status.state = ConnectionState::Disconnected;
        status.paired_device_id = None;
        status.paired_device_name = None;
        status.ping_ms = 0;
    }

    pub fn get_status(&self) -> ConnectionStatusPayload {
        let mut status = self.current_status.lock().unwrap();
        let now = Self::current_time_ms();
        // If no heartbeat for > 15 seconds, mark as reconnecting
        if status.state == ConnectionState::Connected && (now.saturating_sub(status.last_heartbeat_ms) > 15000) {
            status.state = ConnectionState::Reconnecting;
        }
        status.clone()
    }
}
