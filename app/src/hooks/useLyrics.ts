import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { invoke } from "@tauri-apps/api/core";
import { MediaState } from "../types";

export interface LyricLine {
  timeMs: number;
  text: string;
}

interface LyricsPayload {
  lines?: LyricLine[];
  synced?: boolean;
}

type LyricsStatus = "idle" | "loading" | "ready" | "empty" | "error";

interface LyricsState {
  lines: LyricLine[];
  synced: boolean;
  status: LyricsStatus;
}

const cache = new Map<string, { lines: LyricLine[]; synced: boolean }>();

const EMPTY: LyricsState = { lines: [], synced: false, status: "idle" };

export function useLyrics(media: MediaState) {
  const trackKey = media.title
    ? `${media.title}|${media.artist ?? ""}`.toLowerCase()
    : "";
  const [state, setState] = useState<LyricsState>(EMPTY);
  const [attempt, setAttempt] = useState(0);
  const requestId = useRef(0);

  useEffect(() => {
    if (!trackKey) {
      setState(EMPTY);
      return;
    }

    const cached = cache.get(trackKey);
    if (cached) {
      setState({
        ...cached,
        status: cached.lines.length > 0 ? "ready" : "empty",
      });
      return;
    }

    const id = ++requestId.current;
    setState({ lines: [], synced: false, status: "loading" });

    invoke<LyricsPayload>("jarvis_fetch_lyrics", {
      title: media.title,
      artist: media.artist ?? "",
      durationMs: media.durationMs ?? 0,
    })
      .then((payload) => {
        if (id !== requestId.current) return;
        const normalized = {
          lines: Array.isArray(payload?.lines) ? payload.lines : [],
          synced: !!payload?.synced,
        };
        cache.set(trackKey, normalized);
        setState({
          ...normalized,
          status: normalized.lines.length > 0 ? "ready" : "empty",
        });
      })
      .catch(() => {
        if (id === requestId.current) {
          setState({ lines: [], synced: false, status: "error" });
        }
      });
  }, [trackKey, attempt, media.title, media.artist, media.durationMs]);

  const activeIndex = useMemo(() => {
    if (!state.synced || state.lines.length === 0) return -1;
    let index = -1;
    for (let i = 0; i < state.lines.length; i += 1) {
      if (state.lines[i].timeMs <= media.positionMs) {
        index = i;
      } else {
        break;
      }
    }
    return index;
  }, [state.lines, state.synced, media.positionMs]);

  const retry = useCallback(() => {
    if (trackKey) cache.delete(trackKey);
    setAttempt((value) => value + 1);
  }, [trackKey]);

  return { ...state, activeIndex, retry };
}
