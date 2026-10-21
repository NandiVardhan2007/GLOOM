import React from "react";
import { SystemTelemetry } from "../types";
import {
  Cpu,
  HardDrive,
  BatteryCharging,
  Battery,
  ArrowDown,
  ArrowUp,
  Database,
} from "lucide-react";

interface SystemCardProps {
  telemetry: SystemTelemetry;
}

export const SystemCard: React.FC<SystemCardProps> = ({ telemetry }) => {
  const ramUsedGb = (telemetry.ramUsedMb / 1024).toFixed(1);
  const ramTotalGb = (telemetry.ramTotalMb / 1024).toFixed(1);
  const ramPercent =
    telemetry.ramTotalMb > 0
      ? Math.round((telemetry.ramUsedMb / telemetry.ramTotalMb) * 100)
      : 0;

  // Format network speed
  const formatNetSpeed = (kbps?: number) => {
    if (!kbps || kbps < 0.1) return "0.0 KB/s";
    if (kbps >= 1024) {
      return `${(kbps / 1024).toFixed(1)} MB/s`;
    }
    return `${kbps.toFixed(1)} KB/s`;
  };

  const diskUsedGb =
    telemetry.diskTotalGb && telemetry.diskFreeGb
      ? telemetry.diskTotalGb - telemetry.diskFreeGb
      : 0;
  const diskPercent =
    telemetry.diskTotalGb && telemetry.diskTotalGb > 0
      ? Math.round((diskUsedGb / telemetry.diskTotalGb) * 100)
      : 0;

  return (
    <div
      style={{
        display: "grid",
        gridTemplateColumns: "repeat(2, 1fr)",
        gap: "10px",
        width: "100%",
        padding: "2px 0",
      }}
    >
      {/* CPU Card */}
      <div
        style={{
          background: "#181820",
          borderRadius: "14px",
          padding: "10px 14px",
          display: "flex",
          flexDirection: "column",
          gap: "6px",
          border: "1px solid rgba(255, 255, 255, 0.1)",
        }}
      >
        <div
          style={{
            display: "flex",
            alignItems: "center",
            justifyContent: "space-between",
          }}
        >
          <div style={{ display: "flex", alignItems: "center", gap: "6px" }}>
            <Cpu size={15} color="#0a84ff" />
            <span
              style={{
                fontSize: "12px",
                fontWeight: 700,
                color: "#ffffff",
              }}
            >
              Processor
            </span>
          </div>
          <span
            className="tabular-nums"
            style={{ fontSize: "13px", fontWeight: 800, color: "#0a84ff" }}
          >
            {Math.round(telemetry.cpuPercent)}%
          </span>
        </div>
        <div
          style={{
            width: "100%",
            height: "5px",
            borderRadius: "3px",
            background: "#262632",
            overflow: "hidden",
          }}
        >
          <div
            style={{
              width: `${Math.min(100, Math.max(0, telemetry.cpuPercent))}%`,
              height: "100%",
              borderRadius: "3px",
              background: telemetry.cpuPercent > 80 ? "#f87171" : "#0a84ff",
              transition: "width 0.3s ease",
            }}
          />
        </div>
        <span
          style={{
            fontSize: "11px",
            color: "#94a3b8",
            fontWeight: 500,
          }}
        >
          Active Load
        </span>
      </div>

      {/* RAM Card */}
      <div
        style={{
          background: "#181820",
          borderRadius: "14px",
          padding: "10px 14px",
          display: "flex",
          flexDirection: "column",
          gap: "6px",
          border: "1px solid rgba(255, 255, 255, 0.1)",
        }}
      >
        <div
          style={{
            display: "flex",
            alignItems: "center",
            justifyContent: "space-between",
          }}
        >
          <div style={{ display: "flex", alignItems: "center", gap: "6px" }}>
            <HardDrive size={15} color="#a78bfa" />
            <span
              style={{
                fontSize: "12px",
                fontWeight: 700,
                color: "#ffffff",
              }}
            >
              Memory
            </span>
          </div>
          <span
            className="tabular-nums"
            style={{ fontSize: "13px", fontWeight: 800, color: "#a78bfa" }}
          >
            {ramPercent}%
          </span>
        </div>
        <div
          style={{
            width: "100%",
            height: "5px",
            borderRadius: "3px",
            background: "#262632",
            overflow: "hidden",
          }}
        >
          <div
            style={{
              width: `${Math.min(100, Math.max(0, ramPercent))}%`,
              height: "100%",
              borderRadius: "3px",
              background: "#a78bfa",
              transition: "width 0.3s ease",
            }}
          />
        </div>
        <span
          className="tabular-nums"
          style={{
            fontSize: "11px",
            color: "#94a3b8",
            fontWeight: 500,
          }}
        >
          {ramUsedGb} / {ramTotalGb} GB
        </span>
      </div>

      {/* Network I/O Speed Card */}
      <div
        style={{
          background: "#181820",
          borderRadius: "14px",
          padding: "10px 14px",
          display: "flex",
          flexDirection: "column",
          gap: "6px",
          border: "1px solid rgba(255, 255, 255, 0.1)",
        }}
      >
        <div
          style={{
            display: "flex",
            alignItems: "center",
            justifyContent: "space-between",
          }}
        >
          <span
            style={{
              fontSize: "12px",
              fontWeight: 700,
              color: "#ffffff",
            }}
          >
            Network I/O
          </span>
        </div>

        <div style={{ display: "flex", flexDirection: "column", gap: "4px" }}>
          <div
            style={{
              display: "flex",
              alignItems: "center",
              justifyContent: "space-between",
              fontSize: "11px",
            }}
          >
            <div style={{ display: "flex", alignItems: "center", gap: "5px" }}>
              <ArrowDown size={13} color="#34d399" />
              <span style={{ color: "#94a3b8", fontWeight: 600 }}>Down</span>
            </div>
            <span
              className="tabular-nums"
              style={{ fontWeight: 700, color: "#34d399" }}
            >
              {formatNetSpeed(telemetry.netDownloadKbps)}
            </span>
          </div>

          <div
            style={{
              display: "flex",
              alignItems: "center",
              justifyContent: "space-between",
              fontSize: "11px",
            }}
          >
            <div style={{ display: "flex", alignItems: "center", gap: "5px" }}>
              <ArrowUp size={13} color="#60a5fa" />
              <span style={{ color: "#94a3b8", fontWeight: 600 }}>Up</span>
            </div>
            <span
              className="tabular-nums"
              style={{ fontWeight: 700, color: "#60a5fa" }}
            >
              {formatNetSpeed(telemetry.netUploadKbps)}
            </span>
          </div>
        </div>
      </div>

      {/* Storage & Power Card */}
      <div
        style={{
          background: "#181820",
          borderRadius: "14px",
          padding: "10px 14px",
          display: "flex",
          flexDirection: "column",
          gap: "6px",
          border: "1px solid rgba(255, 255, 255, 0.1)",
        }}
      >
        <div
          style={{
            display: "flex",
            alignItems: "center",
            justifyContent: "space-between",
          }}
        >
          <div style={{ display: "flex", alignItems: "center", gap: "5px" }}>
            <Database size={14} color="#fbbf24" />
            <span
              style={{
                fontSize: "12px",
                fontWeight: 700,
                color: "#ffffff",
              }}
            >
              Drive C:\
            </span>
          </div>
          <span
            className="tabular-nums"
            style={{ fontSize: "11px", color: "#cbd5e1", fontWeight: 600 }}
          >
            {telemetry.diskFreeGb ? `${telemetry.diskFreeGb} GB free` : "Mounted"}
          </span>
        </div>

        <div
          style={{
            width: "100%",
            height: "5px",
            borderRadius: "3px",
            background: "#262632",
            overflow: "hidden",
          }}
        >
          <div
            style={{
              width: `${diskPercent}%`,
              height: "100%",
              borderRadius: "3px",
              background: "#fbbf24",
              transition: "width 0.3s ease",
            }}
          />
        </div>

        {/* Battery footer */}
        <div
          style={{
            display: "flex",
            alignItems: "center",
            justifyContent: "space-between",
            fontSize: "11px",
            paddingTop: "2px",
          }}
        >
          <div style={{ display: "flex", alignItems: "center", gap: "5px" }}>
            {telemetry.isCharging ? (
              <BatteryCharging size={13} color="#34d399" />
            ) : (
              <Battery size={13} color="#cbd5e1" />
            )}
            <span style={{ color: "#94a3b8", fontWeight: 600 }}>
              {telemetry.hasBattery
                ? telemetry.isCharging
                  ? "Charging"
                  : "Battery"
                : "AC Power"}
            </span>
          </div>
          {telemetry.batteryPercent !== undefined &&
            telemetry.batteryPercent !== null && (
              <span
                className="tabular-nums"
                style={{
                  fontWeight: 700,
                  color: telemetry.isCharging ? "#34d399" : "#ffffff",
                }}
              >
                {telemetry.batteryPercent}%
              </span>
            )}
        </div>
      </div>
    </div>
  );
};
