import React, { useState } from "react";
import {
  Bluetooth,
  RefreshCw,
  Smartphone,
  Headphones,
  Mouse,
  Keyboard,
  Battery,
  Power,
  Radio,
  Plus,
} from "lucide-react";

interface BtDevice {
  id: string;
  name: string;
  category: "audio" | "phone" | "mouse" | "keyboard";
  status: "connected" | "disconnected" | "connecting";
  battery?: number;
  info: string;
}

const INITIAL_DEVICES: BtDevice[] = [
  {
    id: "dev-audio",
    name: "Sony WH-1000XM5",
    category: "audio",
    status: "connected",
    battery: 85,
    info: "Stereo Audio & Hands-Free Mic",
  },
  {
    id: "dev-mouse",
    name: "MX Master 3S",
    category: "mouse",
    status: "connected",
    battery: 92,
    info: "Low Latency BLE Mouse",
  },
  {
    id: "dev-kb",
    name: "Keychron K2 Pro",
    category: "keyboard",
    status: "connected",
    battery: 76,
    info: "Mechanical Bluetooth Keyboard",
  },
  {
    id: "dev-phone",
    name: "Motorola Edge 50 Pro",
    category: "phone",
    status: "connected",
    battery: 100,
    info: "JARVIS LiveBridge Active",
  },
];

export const BluetoothView: React.FC = () => {
  const [btEnabled, setBtEnabled] = useState<boolean>(() => {
    return localStorage.getItem("jarvis_bt_enabled") !== "false";
  });
  const [isScanning, setIsScanning] = useState(false);
  const [devices, setDevices] = useState<BtDevice[]>(INITIAL_DEVICES);
  const [discoveredDevice, setDiscoveredDevice] = useState<BtDevice | null>(null);
  const [toastMessage, setToastMessage] = useState<string | null>(null);

  const showToast = (msg: string) => {
    setToastMessage(msg);
    setTimeout(() => setToastMessage(null), 2400);
  };

  const handleToggleBt = () => {
    const next = !btEnabled;
    setBtEnabled(next);
    localStorage.setItem("jarvis_bt_enabled", next ? "true" : "false");
    showToast(next ? "Bluetooth turned ON" : "Bluetooth turned OFF");
  };

  const handleScan = () => {
    if (!btEnabled) {
      showToast("Turn on Bluetooth to scan for devices");
      return;
    }
    setIsScanning(true);
    setDiscoveredDevice(null);
    showToast("Scanning for nearby Bluetooth devices...");

    setTimeout(() => {
      setIsScanning(false);
      setDiscoveredDevice({
        id: `dev-discovered-${Date.now()}`,
        name: "Wireless Earbuds (Pairing Mode)",
        category: "audio",
        status: "disconnected",
        info: "Ready to pair",
      });
      showToast("Found 1 new nearby device");
    }, 2200);
  };

  const handleToggleConnection = (id: string) => {
    if (!btEnabled) return;

    setDevices((prev) =>
      prev.map((d) => {
        if (d.id !== id) return d;
        if (d.status === "connected") {
          showToast(`Disconnected from ${d.name}`);
          return { ...d, status: "disconnected" };
        } else {
          // Transition to connecting
          setTimeout(() => {
            setDevices((current) =>
              current.map((curr) =>
                curr.id === id ? { ...curr, status: "connected" } : curr
              )
            );
            showToast(`Connected to ${d.name}`);
          }, 650);
          return { ...d, status: "connecting" };
        }
      })
    );
  };

  const handlePairDiscovered = () => {
    if (!discoveredDevice) return;
    const newDev = { ...discoveredDevice, status: "connected" as const, battery: 100 };
    setDevices((prev) => [newDev, ...prev]);
    setDiscoveredDevice(null);
    showToast(`Paired & connected to ${newDev.name}`);
  };

  const renderIcon = (cat: BtDevice["category"]) => {
    switch (cat) {
      case "audio":
        return <Headphones size={16} color="#38bdf8" />;
      case "mouse":
        return <Mouse size={16} color="#fbbf24" />;
      case "keyboard":
        return <Keyboard size={16} color="#c084fc" />;
      case "phone":
        return <Smartphone size={16} color="#34d399" />;
      default:
        return <Bluetooth size={16} color="#38bdf8" />;
    }
  };

  return (
    <div
      style={{
        display: "flex",
        flexDirection: "column",
        gap: "10px",
        width: "100%",
        height: "100%",
        boxSizing: "border-box",
        padding: "2px",
        overflowY: "auto",
      }}
    >
      {/* ── Top Control Bar ── */}
      <div
        style={{
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
          background: "rgba(255, 255, 255, 0.04)",
          border: "1px solid rgba(255, 255, 255, 0.08)",
          borderRadius: "14px",
          padding: "8px 12px",
        }}
      >
        {/* Left Status */}
        <div style={{ display: "flex", alignItems: "center", gap: "10px" }}>
          <div
            style={{
              width: "32px",
              height: "32px",
              borderRadius: "50%",
              background: btEnabled
                ? "linear-gradient(135deg, #0284c7, #2563eb)"
                : "rgba(255, 255, 255, 0.08)",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              boxShadow: btEnabled ? "0 0 12px rgba(2, 132, 199, 0.4)" : "none",
              transition: "all 0.2s ease",
            }}
          >
            <Bluetooth size={16} color={btEnabled ? "#ffffff" : "rgba(255,255,255,0.4)"} />
          </div>

          <div style={{ display: "flex", flexDirection: "column" }}>
            <span style={{ fontSize: "12.5px", fontWeight: 700, color: "#ffffff" }}>
              Bluetooth Manager
            </span>
            <div style={{ display: "flex", alignItems: "center", gap: "6px" }}>
              <span
                style={{
                  width: "6px",
                  height: "6px",
                  borderRadius: "50%",
                  backgroundColor: btEnabled ? "#10b981" : "#ef4444",
                  boxShadow: btEnabled ? "0 0 6px #10b981" : "none",
                }}
              />
              <span
                style={{
                  fontSize: "11px",
                  color: btEnabled ? "rgba(255, 255, 255, 0.7)" : "rgba(255, 255, 255, 0.4)",
                  fontWeight: 500,
                }}
              >
                {btEnabled ? "Active & Discoverable" : "Radio Turned Off"}
              </span>
            </div>
          </div>
        </div>

        {/* Right Actions: Scan & Master Toggle */}
        <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
          {btEnabled && (
            <button
              onClick={handleScan}
              disabled={isScanning}
              style={{
                display: "flex",
                alignItems: "center",
                gap: "5px",
                background: isScanning ? "rgba(2, 132, 199, 0.25)" : "rgba(255, 255, 255, 0.08)",
                border: "1px solid rgba(255, 255, 255, 0.12)",
                borderRadius: "9999px",
                padding: "5px 10px",
                color: "#ffffff",
                fontSize: "11px",
                fontWeight: 600,
                cursor: isScanning ? "default" : "pointer",
                transition: "all 0.15s ease",
              }}
              title="Scan for nearby Bluetooth devices"
            >
              <RefreshCw
                size={12}
                style={{
                  animation: isScanning ? "spin 1s linear infinite" : "none",
                }}
              />
              <span>{isScanning ? "Scanning..." : "Scan"}</span>
            </button>
          )}

          {/* Master ON/OFF Toggle Switch */}
          <button
            onClick={handleToggleBt}
            style={{
              display: "flex",
              alignItems: "center",
              gap: "6px",
              padding: "5px 12px",
              borderRadius: "9999px",
              border: "none",
              cursor: "pointer",
              background: btEnabled ? "#0284c7" : "rgba(255, 255, 255, 0.12)",
              color: "#ffffff",
              fontSize: "11px",
              fontWeight: 700,
              transition: "all 0.18s ease",
            }}
            title={btEnabled ? "Turn Bluetooth OFF" : "Turn Bluetooth ON"}
          >
            <Power size={11} />
            <span>{btEnabled ? "ON" : "OFF"}</span>
          </button>
        </div>
      </div>

      {/* ── Discovered Device Banner (if found while scanning) ── */}
      {discoveredDevice && btEnabled && (
        <div
          style={{
            display: "flex",
            alignItems: "center",
            justifyContent: "space-between",
            background: "linear-gradient(135deg, rgba(2, 132, 199, 0.18), rgba(56, 189, 248, 0.1))",
            border: "1px solid rgba(56, 189, 248, 0.3)",
            borderRadius: "12px",
            padding: "8px 12px",
          }}
        >
          <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
            <Radio size={16} color="#38bdf8" />
            <div style={{ display: "flex", flexDirection: "column" }}>
              <span style={{ fontSize: "12px", fontWeight: 700, color: "#ffffff" }}>
                {discoveredDevice.name}
              </span>
              <span style={{ fontSize: "10.5px", color: "rgba(255, 255, 255, 0.6)" }}>
                {discoveredDevice.info}
              </span>
            </div>
          </div>
          <button
            onClick={handlePairDiscovered}
            style={{
              display: "flex",
              alignItems: "center",
              gap: "4px",
              padding: "4px 10px",
              borderRadius: "8px",
              border: "none",
              background: "#0284c7",
              color: "#ffffff",
              fontSize: "11px",
              fontWeight: 700,
              cursor: "pointer",
            }}
          >
            <Plus size={12} />
            <span>Pair</span>
          </button>
        </div>
      )}

      {/* ── Paired Devices Grid ── */}
      {btEnabled ? (
        <div
          style={{
            display: "grid",
            gridTemplateColumns: "1fr 1fr",
            gap: "8px",
            flex: 1,
          }}
        >
          {devices.map((device) => {
            const isConn = device.status === "connected";
            const isConnecting = device.status === "connecting";

            return (
              <div
                key={device.id}
                style={{
                  background: isConn
                    ? "rgba(255, 255, 255, 0.05)"
                    : "rgba(255, 255, 255, 0.02)",
                  border: isConn
                    ? "1px solid rgba(255, 255, 255, 0.12)"
                    : "1px solid rgba(255, 255, 255, 0.05)",
                  borderRadius: "14px",
                  padding: "10px 12px",
                  display: "flex",
                  flexDirection: "column",
                  justifyContent: "space-between",
                  gap: "8px",
                  transition: "all 0.15s ease",
                }}
              >
                {/* Device Header */}
                <div style={{ display: "flex", alignItems: "flex-start", justifyContent: "space-between" }}>
                  <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
                    <div
                      style={{
                        width: "28px",
                        height: "28px",
                        borderRadius: "8px",
                        background: "rgba(255, 255, 255, 0.08)",
                        display: "flex",
                        alignItems: "center",
                        justifyContent: "center",
                      }}
                    >
                      {renderIcon(device.category)}
                    </div>
                    <div style={{ display: "flex", flexDirection: "column", minWidth: 0 }}>
                      <span
                        style={{
                          fontSize: "12px",
                          fontWeight: 700,
                          color: "#ffffff",
                          whiteSpace: "nowrap",
                          overflow: "hidden",
                          textOverflow: "ellipsis",
                          maxWidth: "130px",
                        }}
                      >
                        {device.name}
                      </span>
                      <span
                        style={{
                          fontSize: "10px",
                          color: "rgba(255, 255, 255, 0.45)",
                          whiteSpace: "nowrap",
                          overflow: "hidden",
                          textOverflow: "ellipsis",
                          maxWidth: "130px",
                        }}
                      >
                        {device.info}
                      </span>
                    </div>
                  </div>

                  {/* Battery Indicator if connected */}
                  {isConn && device.battery !== undefined && (
                    <div
                      style={{
                        display: "flex",
                        alignItems: "center",
                        gap: "3px",
                        fontSize: "10px",
                        fontWeight: 600,
                        color: device.battery > 20 ? "#34d399" : "#f87171",
                        background: "rgba(0, 0, 0, 0.3)",
                        padding: "2px 5px",
                        borderRadius: "6px",
                      }}
                    >
                      <Battery size={10} />
                      <span>{device.battery}%</span>
                    </div>
                  )}
                </div>

                {/* Device Action Bar */}
                <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between" }}>
                  <div style={{ display: "flex", alignItems: "center", gap: "5px" }}>
                    <span
                      style={{
                        width: "5px",
                        height: "5px",
                        borderRadius: "50%",
                        backgroundColor: isConn
                          ? "#34d399"
                          : isConnecting
                          ? "#fbbf24"
                          : "rgba(255, 255, 255, 0.3)",
                      }}
                    />
                    <span
                      style={{
                        fontSize: "10.5px",
                        fontWeight: 600,
                        color: isConn
                          ? "#34d399"
                          : isConnecting
                          ? "#fbbf24"
                          : "rgba(255, 255, 255, 0.4)",
                      }}
                    >
                      {isConnecting ? "Connecting..." : isConn ? "Connected" : "Paired"}
                    </span>
                  </div>

                  <button
                    onClick={() => handleToggleConnection(device.id)}
                    disabled={isConnecting}
                    style={{
                      padding: "4px 9px",
                      borderRadius: "7px",
                      border: "none",
                      background: isConn
                        ? "rgba(239, 68, 68, 0.15)"
                        : "rgba(2, 132, 199, 0.25)",
                      color: isConn ? "#f87171" : "#38bdf8",
                      fontSize: "10.5px",
                      fontWeight: 700,
                      cursor: isConnecting ? "default" : "pointer",
                      transition: "all 0.15s ease",
                    }}
                  >
                    {isConn ? "Disconnect" : "Connect"}
                  </button>
                </div>
              </div>
            );
          })}
        </div>
      ) : (
        /* Disabled State Screen */
        <div
          style={{
            display: "flex",
            flexDirection: "column",
            alignItems: "center",
            justifyContent: "center",
            gap: "10px",
            flex: 1,
            background: "rgba(255, 255, 255, 0.02)",
            borderRadius: "14px",
            border: "1px dashed rgba(255, 255, 255, 0.08)",
            padding: "20px",
          }}
        >
          <Bluetooth size={32} color="rgba(255, 255, 255, 0.2)" />
          <div style={{ textAlign: "center" }}>
            <div style={{ fontSize: "13px", fontWeight: 700, color: "#ffffff", marginBottom: "3px" }}>
              Bluetooth is Turned Off
            </div>
            <div style={{ fontSize: "11px", color: "rgba(255, 255, 255, 0.45)" }}>
              Enable Bluetooth to connect and manage paired wireless devices.
            </div>
          </div>
          <button
            onClick={handleToggleBt}
            style={{
              padding: "6px 14px",
              borderRadius: "8px",
              border: "none",
              background: "#0284c7",
              color: "#ffffff",
              fontSize: "12px",
              fontWeight: 700,
              cursor: "pointer",
              marginTop: "4px",
            }}
          >
            Turn On Bluetooth
          </button>
        </div>
      )}

      {/* ── Toast Notification ── */}
      {toastMessage && (
        <div
          style={{
            textAlign: "center",
            fontSize: "11px",
            color: "#38bdf8",
            fontWeight: 700,
            letterSpacing: "0.02em",
            padding: "2px 0",
          }}
        >
          {toastMessage}
        </div>
      )}
    </div>
  );
};
