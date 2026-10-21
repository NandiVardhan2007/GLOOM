import React from "react";
import { SystemTelemetry } from "../types";
import {
  Wifi,
  ArrowDown,
  ArrowUp,
  Globe,
  Smartphone,
  ShieldCheck,
  Zap,
} from "lucide-react";

interface NetworkViewProps {
  telemetry: SystemTelemetry;
}

export const NetworkView: React.FC<NetworkViewProps> = ({ telemetry }) => {
  const downSpeed = telemetry.netDownloadKbps ?? 0;
  const upSpeed = telemetry.netUploadKbps ?? 0;

  const formatSpeed = (kbps: number) => {
    if (kbps < 0.1) return "0.0 KB/s";
    if (kbps >= 1024) return `${(kbps / 1024).toFixed(1)} MB/s`;
    return `${kbps.toFixed(1)} KB/s`;
  };

  const downPercent = Math.min(100, Math.max(5, (downSpeed / (10 * 1024)) * 100));
  const upPercent = Math.min(100, Math.max(5, (upSpeed / (5 * 1024)) * 100));

  return (
    <div
      style={{
        display: "flex",
        flexDirection: "column",
        gap: "10px",
        width: "100%",
        height: "100%",
        padding: "2px 0",
        fontFamily: "-apple-system, BlinkMacSystemFont, 'SF Pro Text', 'SF Pro', system-ui, sans-serif",
      }}
    >
      {/* ── Top Row: Real-time Live Bandwidth (Apple Style Cards) ── */}
      <div
        style={{
          display: "grid",
          gridTemplateColumns: "1fr 1fr",
          gap: "10px",
        }}
      >
        {/* Download Speed Card */}
        <div
          style={{
            background: "rgba(255, 255, 255, 0.05)",
            backdropFilter: "blur(20px)",
            borderRadius: "14px",
            padding: "12px 14px",
            border: "0.5px solid rgba(50, 173, 230, 0.25)",
            boxShadow: "inset 0 0.5px 0.5px rgba(255, 255, 255, 0.15)",
            display: "flex",
            flexDirection: "column",
            gap: "8px",
          }}
        >
          <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between" }}>
            <div style={{ display: "flex", alignItems: "center", gap: "6px" }}>
              <div
                style={{
                  width: "24px",
                  height: "24px",
                  borderRadius: "6px",
                  background: "rgba(50, 173, 230, 0.18)",
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "center",
                }}
              >
                <ArrowDown size={14} color="#32ade6" />
              </div>
              <span style={{ fontSize: "12px", fontWeight: 600, color: "#ffffff" }}>Download</span>
            </div>
            <span
              className="tabular-nums"
              style={{ fontSize: "13px", fontWeight: 700, color: "#32ade6" }}
            >
              {formatSpeed(downSpeed)}
            </span>
          </div>
          <div
            style={{
              width: "100%",
              height: "4px",
              borderRadius: "9999px",
              background: "rgba(255, 255, 255, 0.1)",
              overflow: "hidden",
            }}
          >
            <div
              style={{
                width: `${downPercent}%`,
                height: "100%",
                background: "linear-gradient(90deg, #007aff, #32ade6)",
                borderRadius: "9999px",
                transition: "width 0.4s cubic-bezier(0.16, 1, 0.3, 1)",
              }}
            />
          </div>
        </div>

        {/* Upload Speed Card */}
        <div
          style={{
            background: "rgba(255, 255, 255, 0.05)",
            backdropFilter: "blur(20px)",
            borderRadius: "14px",
            padding: "12px 14px",
            border: "0.5px solid rgba(52, 199, 89, 0.25)",
            boxShadow: "inset 0 0.5px 0.5px rgba(255, 255, 255, 0.15)",
            display: "flex",
            flexDirection: "column",
            gap: "8px",
          }}
        >
          <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between" }}>
            <div style={{ display: "flex", alignItems: "center", gap: "6px" }}>
              <div
                style={{
                  width: "24px",
                  height: "24px",
                  borderRadius: "6px",
                  background: "rgba(52, 199, 89, 0.18)",
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "center",
                }}
              >
                <ArrowUp size={14} color="#34c759" />
              </div>
              <span style={{ fontSize: "12px", fontWeight: 600, color: "#ffffff" }}>Upload</span>
            </div>
            <span
              className="tabular-nums"
              style={{ fontSize: "13px", fontWeight: 700, color: "#34c759" }}
            >
              {formatSpeed(upSpeed)}
            </span>
          </div>
          <div
            style={{
              width: "100%",
              height: "4px",
              borderRadius: "9999px",
              background: "rgba(255, 255, 255, 0.1)",
              overflow: "hidden",
            }}
          >
            <div
              style={{
                width: `${upPercent}%`,
                height: "100%",
                background: "linear-gradient(90deg, #28a745, #34c759)",
                borderRadius: "9999px",
                transition: "width 0.4s cubic-bezier(0.16, 1, 0.3, 1)",
              }}
            />
          </div>
        </div>
      </div>

      {/* ── Middle & Bottom: LAN LiveBridge & Connectivity Diagnostics ── */}
      <div
        style={{
          background: "rgba(255, 255, 255, 0.04)",
          backdropFilter: "blur(24px)",
          borderRadius: "14px",
          padding: "12px 14px",
          border: "0.5px solid rgba(255, 255, 255, 0.1)",
          boxShadow: "inset 0 0.5px 0.5px rgba(255, 255, 255, 0.12)",
          display: "flex",
          flexDirection: "column",
          gap: "10px",
        }}
      >
        <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between" }}>
          <div style={{ display: "flex", alignItems: "center", gap: "7px" }}>
            <Globe size={15} color="#32ade6" />
            <span style={{ fontSize: "12px", fontWeight: 600, color: "#ffffff", letterSpacing: "-0.01em" }}>
              Local Network & Ecosystem Bridge
            </span>
          </div>
          <span
            style={{
              fontSize: "9.5px",
              fontWeight: 700,
              padding: "2px 8px",
              borderRadius: "9999px",
              background: "rgba(50, 173, 230, 0.18)",
              color: "#32ade6",
              border: "0.5px solid rgba(50, 173, 230, 0.35)",
              display: "flex",
              alignItems: "center",
              gap: "4px",
              letterSpacing: "0.03em",
            }}
          >
            <span
              style={{
                width: "5px",
                height: "5px",
                borderRadius: "50%",
                backgroundColor: "#32ade6",
                boxShadow: "0 0 6px #32ade6",
              }}
            />
            LISTENING
          </span>
        </div>

        <div
          style={{
            display: "grid",
            gridTemplateColumns: "repeat(3, 1fr)",
            gap: "8px",
          }}
        >
          {/* Item 1: WiFi Direct / LAN */}
          <div
            style={{
              background: "rgba(255, 255, 255, 0.04)",
              borderRadius: "10px",
              padding: "8px 10px",
              display: "flex",
              flexDirection: "column",
              gap: "3px",
              border: "0.5px solid rgba(255, 255, 255, 0.06)",
            }}
          >
            <div style={{ display: "flex", alignItems: "center", gap: "4px", color: "rgba(255, 255, 255, 0.45)", fontSize: "10px" }}>
              <Wifi size={11} />
              <span>Interface</span>
            </div>
            <span style={{ fontSize: "11px", fontWeight: 600, color: "#ffffff" }}>
              Local Network
            </span>
          </div>

          {/* Item 2: Beaming Socket Port */}
          <div
            style={{
              background: "rgba(255, 255, 255, 0.04)",
              borderRadius: "10px",
              padding: "8px 10px",
              display: "flex",
              flexDirection: "column",
              gap: "3px",
              border: "0.5px solid rgba(255, 255, 255, 0.06)",
            }}
          >
            <div style={{ display: "flex", alignItems: "center", gap: "4px", color: "rgba(255, 255, 255, 0.45)", fontSize: "10px" }}>
              <Zap size={11} color="#32ade6" />
              <span>Beam Socket</span>
            </div>
            <span className="tabular-nums" style={{ fontSize: "11px", fontWeight: 600, color: "#32ade6" }}>
              Port 53391 (Active)
            </span>
          </div>

          {/* Item 3: Companion Node */}
          <div
            style={{
              background: "rgba(255, 255, 255, 0.04)",
              borderRadius: "10px",
              padding: "8px 10px",
              display: "flex",
              flexDirection: "column",
              gap: "3px",
              border: "0.5px solid rgba(255, 255, 255, 0.06)",
            }}
          >
            <div style={{ display: "flex", alignItems: "center", gap: "4px", color: "rgba(255, 255, 255, 0.45)", fontSize: "10px" }}>
              <Smartphone size={11} color="#af52de" />
              <span>Android Bridge</span>
            </div>
            <span style={{ fontSize: "11px", fontWeight: 600, color: "#af52de" }}>
              P2P Stream Ready
            </span>
          </div>
        </div>

        <div
          style={{
            display: "flex",
            alignItems: "center",
            justifyContent: "space-between",
            fontSize: "11px",
            color: "rgba(255, 255, 255, 0.55)",
            paddingTop: "2px",
          }}
        >
          <div style={{ display: "flex", alignItems: "center", gap: "5px" }}>
            <ShieldCheck size={13} color="#34c759" />
            <span>JARVIS LiveBridge Authenticated Protocol</span>
          </div>
          <span style={{ color: "#32ade6", fontWeight: 600 }}>Port 53391</span>
        </div>
      </div>
    </div>
  );
};
