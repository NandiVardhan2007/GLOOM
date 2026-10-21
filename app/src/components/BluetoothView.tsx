import React, { useState, useEffect } from "react";
import { invoke } from "@tauri-apps/api/core";
import {
  Bluetooth,
  Smartphone,
  ExternalLink,
  ShieldCheck,
  RefreshCw,
} from "lucide-react";

interface BridgeDevice {
  id: string;
  name: string;
  deviceType: string;
  ip: string;
  port: number;
  batteryPercent: number;
  isCharging: boolean;
  wifiSsid: string;
  status: string;
  lastSeenMs: number;
}

export const BluetoothView: React.FC = () => {
  const [btEnabled, setBtEnabled] = useState<boolean>(() => {
    return localStorage.getItem("jarvis_bt_enabled") !== "false";
  });
  const [pairedDevices, setPairedDevices] = useState<BridgeDevice[]>([]);
  const [isLoading, setIsLoading] = useState(false);

  const fetchDevices = async () => {
    try {
      setIsLoading(true);
      const devs = await invoke<BridgeDevice[]>("jarvis_get_bridge_devices");
      setPairedDevices(devs || []);
    } catch {
      setPairedDevices([]);
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchDevices();
  }, []);

  const handleToggleBt = () => {
    const next = !btEnabled;
    setBtEnabled(next);
    localStorage.setItem("jarvis_bt_enabled", next ? "true" : "false");
  };

  const handleOpenWindowsBt = () => {
    invoke("jarvis_system_action", { action: "bluetooth" }).catch(() => {});
  };

  return (
    <div
      style={{
        display: "flex",
        flexDirection: "column",
        gap: "12px",
        width: "100%",
        height: "100%",
        boxSizing: "border-box",
        fontFamily: "-apple-system, BlinkMacSystemFont, 'SF Pro Text', system-ui, sans-serif",
      }}
    >
      {/* ── Top Header Card ── */}
      <div
        style={{
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
          background: "rgba(255, 255, 255, 0.05)",
          backdropFilter: "blur(20px)",
          borderRadius: "14px",
          padding: "12px 16px",
          border: "0.5px solid rgba(255, 255, 255, 0.1)",
        }}
      >
        <div style={{ display: "flex", alignItems: "center", gap: "10px" }}>
          <div
            style={{
              width: "32px",
              height: "32px",
              borderRadius: "8px",
              background: btEnabled ? "rgba(0, 122, 255, 0.2)" : "rgba(255, 255, 255, 0.06)",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
            }}
          >
            <Bluetooth size={18} color={btEnabled ? "#007aff" : "rgba(255, 255, 255, 0.4)"} />
          </div>
          <div>
            <div style={{ fontSize: "13px", fontWeight: 700, color: "#ffffff" }}>
              Bluetooth & Wireless Devices
            </div>
            <div style={{ fontSize: "11px", color: "rgba(255, 255, 255, 0.5)" }}>
              {btEnabled ? "Ready for wireless connections" : "Wireless adapter disabled"}
            </div>
          </div>
        </div>

        <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
          <button
            onClick={handleToggleBt}
            style={{
              background: btEnabled ? "#007aff" : "rgba(255, 255, 255, 0.1)",
              color: "#ffffff",
              border: "none",
              borderRadius: "20px",
              padding: "5px 14px",
              fontSize: "11px",
              fontWeight: 600,
              cursor: "pointer",
              transition: "background 0.2s ease",
            }}
          >
            {btEnabled ? "Enabled" : "Disabled"}
          </button>
        </div>
      </div>

      {/* ── Native Windows Bluetooth Pairing Section ── */}
      <div
        style={{
          background: "rgba(255, 255, 255, 0.055)",
          borderRadius: "14px",
          padding: "14px 16px",
          border: "0.5px solid rgba(255, 255, 255, 0.08)",
          display: "flex",
          flexDirection: "column",
          gap: "10px",
        }}
      >
        <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between" }}>
          <span style={{ fontSize: "12px", fontWeight: 600, color: "#ffffff" }}>
            Windows System Peripherals
          </span>
          <button
            onClick={handleOpenWindowsBt}
            style={{
              display: "flex",
              alignItems: "center",
              gap: "5px",
              background: "rgba(0, 122, 255, 0.15)",
              border: "0.5px solid rgba(0, 122, 255, 0.4)",
              color: "#0a84ff",
              borderRadius: "8px",
              padding: "4px 10px",
              fontSize: "11px",
              fontWeight: 600,
              cursor: "pointer",
            }}
          >
            <ExternalLink size={12} />
            <span>Open Windows Settings</span>
          </button>
        </div>
        <p style={{ margin: 0, fontSize: "11px", color: "rgba(255, 255, 255, 0.5)", lineHeight: "1.4" }}>
          Bluetooth peripheral pairing (audio, keyboards, mice, and game controllers) is handled securely by Windows native radio manager.
        </p>
      </div>

      {/* ── Real LiveBridge Companion Nodes ── */}
      <div
        style={{
          flex: 1,
          background: "rgba(255, 255, 255, 0.055)",
          borderRadius: "14px",
          padding: "12px 16px",
          border: "0.5px solid rgba(255, 255, 255, 0.08)",
          display: "flex",
          flexDirection: "column",
          gap: "8px",
          overflowY: "auto",
        }}
      >
        <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between" }}>
          <span style={{ fontSize: "12px", fontWeight: 600, color: "#ffffff" }}>
            Paired LiveBridge Companion Nodes
          </span>
          <button
            onClick={fetchDevices}
            disabled={isLoading}
            style={{
              background: "transparent",
              border: "none",
              color: "rgba(255, 255, 255, 0.6)",
              cursor: "pointer",
              display: "flex",
              alignItems: "center",
              gap: "4px",
              fontSize: "11px",
            }}
          >
            <RefreshCw size={12} className={isLoading ? "spin" : ""} />
            <span>Refresh</span>
          </button>
        </div>

        {pairedDevices.length === 0 ? (
          <div
            style={{
              display: "flex",
              flexDirection: "column",
              alignItems: "center",
              justifyContent: "center",
              padding: "20px 0",
              gap: "6px",
              color: "rgba(255, 255, 255, 0.4)",
              fontSize: "11px",
            }}
          >
            <Smartphone size={24} strokeWidth={1.5} />
            <span>No companion mobile devices paired yet</span>
            <span style={{ fontSize: "10px", color: "rgba(255, 255, 255, 0.3)" }}>
              Pair via PIN in the File Beam & Phone tab
            </span>
          </div>
        ) : (
          pairedDevices.map((dev) => (
            <div
              key={dev.id}
              style={{
                display: "flex",
                alignItems: "center",
                justifyContent: "space-between",
                background: "rgba(255, 255, 255, 0.055)",
                padding: "8px 12px",
                borderRadius: "10px",
                border: "0.5px solid rgba(255, 255, 255, 0.06)",
              }}
            >
              <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
                <Smartphone size={15} color="#0a84ff" />
                <div>
                  <div style={{ fontSize: "12px", fontWeight: 600, color: "#ffffff" }}>
                    {dev.name}
                  </div>
                  <div style={{ fontSize: "10px", color: "rgba(255, 255, 255, 0.45)" }}>
                    {dev.ip ? `${dev.ip}:${dev.port}` : "Offline"}
                  </div>
                </div>
              </div>
              <div style={{ display: "flex", alignItems: "center", gap: "6px" }}>
                <ShieldCheck size={13} color="#30d158" />
                <span style={{ fontSize: "11px", fontWeight: 600, color: dev.status === "connected" ? "#30d158" : "rgba(255, 255, 255, 0.4)" }}>
                  {dev.status === "connected" ? "Connected" : "Saved"}
                </span>
              </div>
            </div>
          ))
        )}
      </div>
    </div>
  );
};
