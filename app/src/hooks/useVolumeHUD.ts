import { useEffect, useState, useRef, useCallback } from "react";
import { invoke } from "@tauri-apps/api/core";
import { VolumeState } from "../types";
import { isTauriRuntime, safeListen } from "../lib/tauri";

export function useVolumeHUD() {
  const [volumeState, setVolumeState] = useState<VolumeState>({ volume: 0.5, isMuted: false });
  const [showHUD, setShowHUD] = useState<boolean>(false);
  const hideTimerRef = useRef<number | null>(null);
  const isInitialMount = useRef<boolean>(true);

  useEffect(() => {
    if (!isTauriRuntime()) return;
    let unlisten: (() => void) | undefined;

    safeListen<VolumeState>("jarvis://volume-update", (event) => {
      setVolumeState(event.payload);

      // Don't pop HUD on very first initial reading
      if (isInitialMount.current) {
        isInitialMount.current = false;
        return;
      }

      setShowHUD(true);
      if (hideTimerRef.current) {
        window.clearTimeout(hideTimerRef.current);
      }
      hideTimerRef.current = window.setTimeout(() => {
        setShowHUD(false);
      }, 2200);
    })
      .then((fn) => {
        unlisten = fn;
      })
      .catch(console.error);

    return () => {
      if (unlisten) unlisten();
      if (hideTimerRef.current) window.clearTimeout(hideTimerRef.current);
    };
  }, []);

  const setVolume = useCallback(async (level: number) => {
    try {
      await invoke("jarvis_set_volume", { level });
      setVolumeState((prev) => ({ ...prev, volume: level }));
    } catch (e) {
      console.error("setVolume error:", e);
    }
  }, []);

  return {
    volume: volumeState.volume,
    isMuted: volumeState.isMuted,
    showHUD,
    setVolume,
    dismissHUD: () => setShowHUD(false),
  };
}
