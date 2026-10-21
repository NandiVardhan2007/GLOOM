import { useEffect, useState } from "react";
import { invoke } from "@tauri-apps/api/core";
import { SystemTelemetry } from "../types";
import { isTauriRuntime, safeListen } from "../lib/tauri";

const DEFAULT_TELEMETRY: SystemTelemetry = {
  cpuPercent: 0,
  ramUsedMb: 0,
  ramTotalMb: 16384,
  batteryPercent: null,
  isCharging: false,
  hasBattery: false,
};

// Browser-only fixture so the island can be reviewed outside Tauri.
const PREVIEW_TELEMETRY: SystemTelemetry = {
  ...DEFAULT_TELEMETRY,
  cpuPercent: 18,
  ramUsedMb: 9420,
  batteryPercent: 76,
  isCharging: false,
  hasBattery: true,
  netDownloadKbps: 1200,
  netUploadKbps: 320,
};

export function useSystemTelemetry() {
  const [telemetry, setTelemetry] = useState<SystemTelemetry>(() =>
    isTauriRuntime() ? DEFAULT_TELEMETRY : PREVIEW_TELEMETRY
  );

  useEffect(() => {
    if (!isTauriRuntime()) return;

    // 1. Immediate initial fetch from Rust backend
    invoke<SystemTelemetry>("jarvis_get_telemetry")
      .then((data) => {
        if (data && (data.ramTotalMb > 0 || data.cpuPercent > 0)) {
          setTelemetry(data);
        }
      })
      .catch(console.error);

    // 2. Real-time event listener
    let unlisten: (() => void) | undefined;
    safeListen<SystemTelemetry>("jarvis://system-update", (event) => {
      if (event.payload) {
        setTelemetry(event.payload);
      }
    })
      .then((fn) => {
        unlisten = fn;
      })
      .catch(console.error);

    // 3. Fallback poll interval every 2.5s
    const pollInterval = setInterval(() => {
      invoke<SystemTelemetry>("jarvis_get_telemetry")
        .then((data) => {
          if (data && (data.ramTotalMb > 0 || data.cpuPercent > 0)) {
            setTelemetry(data);
          }
        })
        .catch(console.error);
    }, 2500);

    return () => {
      if (unlisten) unlisten();
      clearInterval(pollInterval);
    };
  }, []);

  return telemetry;
}
