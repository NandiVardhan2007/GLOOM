import { useEffect, useState, useCallback } from "react";
import { invoke } from "@tauri-apps/api/core";
import { MediaState } from "../types";
import { isTauriRuntime, safeListen } from "../lib/tauri";

const DEFAULT_MEDIA: MediaState = {
  playing: false,
  title: "",
  artist: "",
  album: "",
  positionMs: 0,
  durationMs: 0,
  thumbnail: null,
};

// Browser-only fixture so the island can be reviewed outside Tauri.
const PREVIEW_MEDIA: MediaState = {
  playing: true,
  title: "Midnight City",
  artist: "M83",
  album: "Hurry Up, We're Dreaming",
  positionMs: 82000,
  durationMs: 244000,
  thumbnail: null,
};

export function useMediaSession() {
  const [media, setMedia] = useState<MediaState>(() =>
    isTauriRuntime() ? DEFAULT_MEDIA : PREVIEW_MEDIA
  );

  useEffect(() => {
    if (!isTauriRuntime()) return;
    let unlisten: (() => void) | undefined;

    safeListen<MediaState>("jarvis://media-update", (event) => {
      setMedia(event.payload);
    })
      .then((fn) => {
        unlisten = fn;
      })
      .catch(console.error);

    return () => {
      if (unlisten) unlisten();
    };
  }, []);

  const togglePlay = useCallback(async () => {
    try {
      await invoke("jarvis_media_control", { action: "toggle" });
    } catch (e) {
      console.error("togglePlay error:", e);
    }
  }, []);

  const nextTrack = useCallback(async () => {
    try {
      await invoke("jarvis_media_control", { action: "next" });
    } catch (e) {
      console.error("nextTrack error:", e);
    }
  }, []);

  const prevTrack = useCallback(async () => {
    try {
      await invoke("jarvis_media_control", { action: "previous" });
    } catch (e) {
      console.error("prevTrack error:", e);
    }
  }, []);

  const seek = useCallback(async (ms: number) => {
    try {
      await invoke("jarvis_media_control", { action: "seek", payload: ms });
    } catch (e) {
      console.error("seek error:", e);
    }
  }, []);

  return {
    media,
    togglePlay,
    nextTrack,
    prevTrack,
    seek,
    hasMedia: !!(media.title || media.artist),
  };
}
