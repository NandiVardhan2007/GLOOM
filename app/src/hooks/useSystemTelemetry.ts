import { useEffect, useState } from "react";
import { listen } from "@tauri-apps/api/event";
import { invoke } from "@tauri-apps/api/core";
import { SystemTelemetry } from "../types";

const DEFAULT_TELEMETRY: SystemTelemetry = {
  cpuPercent: 0,
  ramUsedMb: 0,
  ramTotalMb: 16384,
  batteryPercent: null,
  isCharging: false,
  hasBattery: false,
};

export function useSystemTelemetry() {
  const [telemetry, setTelemetry] = useState<SystemTelemetry>(DEFAULT_TELEMETRY);

  useEffect(() => {
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
    listen<SystemTelemetry>("jarvis://system-update", (event) => {
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
