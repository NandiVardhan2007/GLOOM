import { useEffect, useState, useCallback } from "react";
import { listen } from "@tauri-apps/api/event";
import { invoke } from "@tauri-apps/api/core";
import { MediaState } from "../types";

const DEFAULT_MEDIA: MediaState = {
  playing: false,
  title: "",
  artist: "",
  album: "",
  positionMs: 0,
  durationMs: 0,
  thumbnail: null,
};

export function useMediaSession() {
  const [media, setMedia] = useState<MediaState>(DEFAULT_MEDIA);

  useEffect(() => {
    let unlisten: (() => void) | undefined;

    listen<MediaState>("jarvis://media-update", (event) => {
      setMedia(event.payload);
    }).then((fn) => {
      unlisten = fn;
    }).catch(console.error);

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
