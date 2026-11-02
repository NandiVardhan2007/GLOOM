import { useState, useEffect, useRef } from "react";

export interface LyricLine {
	timeMs: number;
	text: string;
}

interface LrcLibResponse {
	syncedLyrics?: string;
	plainLyrics?: string;
}

function parseLrc(lrcText: string): LyricLine[] {
	const lines: LyricLine[] = [];
	const lineRegex = /\[(\d+):(\d+(?:\.\d+)?)\](.*)/;
	for (const rawLine of lrcText.split("\n")) {
		const match = lineRegex.exec(rawLine);
		if (match) {
			const minutes = parseInt(match[1], 10);
			const seconds = parseFloat(match[2]);
			const text = match[3].trim();
			if (text) {
				lines.push({
					timeMs: Math.round((minutes * 60 + seconds) * 1000),
					text
				});
			}
		}
	}
	return lines.sort((a, b) => a.timeMs - b.timeMs);
}

// Memory cache for fetched lyrics to prevent duplicate network calls
const lyricsCache = new Map<string, LyricLine[]>();

export function useLyrics(title?: string, artist?: string, positionMs = 0) {
	const [lyrics, setLyrics] = useState<LyricLine[]>([]);
	const [activeLyric, setActiveLyric] = useState<string | null>(null);
	const [nextLyric, setNextLyric] = useState<string | null>(null);
	const lastKeyRef = useRef<string>("");

	useEffect(() => {
		if (!title || title === "Unknown Title") {
			setLyrics([]);
			setActiveLyric(null);
			setNextLyric(null);
			lastKeyRef.current = "";
			return;
		}

		// Clean title (strip "(feat. ...)", "[Official Music Video]", etc.)
		const cleanTitle = title
			.replace(/\s*[\(\[][^()\[\]]*(feat|official|video|audio|remaster|version)[^()\[\]]*[\)\]]/gi, "")
			.trim();
		const cleanArtist = (artist || "")
			.replace(/\s*[\(\[][^()\[\]]*[\)\]]/gi, "")
			.trim();
		const cacheKey = `${cleanArtist} - ${cleanTitle}`.toLowerCase();

		if (lastKeyRef.current === cacheKey) return;
		lastKeyRef.current = cacheKey;

		if (lyricsCache.has(cacheKey)) {
			setLyrics(lyricsCache.get(cacheKey)!);
			return;
		}

		let active = true;
		const controller = new AbortController();

		async function fetchLyrics() {
			try {
				const params = new URLSearchParams({
					track_name: cleanTitle,
					artist_name: cleanArtist
				});
				const res = await fetch(`https://lrclib.net/api/get?${params.toString()}`, {
					signal: controller.signal
				});
				if (!res.ok) {
					if (active) setLyrics([]);
					return;
				}
				const data: LrcLibResponse = await res.json();
				if (!active) return;

				if (data.syncedLyrics) {
					const parsed = parseLrc(data.syncedLyrics);
					lyricsCache.set(cacheKey, parsed);
					setLyrics(parsed);
				} else if (data.plainLyrics) {
					const lines = data.plainLyrics
						.split("\n")
						.map((l) => l.trim())
						.filter(Boolean)
						.map((text, idx) => ({ timeMs: idx * 4000, text }));
					lyricsCache.set(cacheKey, lines);
					setLyrics(lines);
				} else {
					setLyrics([]);
				}
			} catch {
				if (active) setLyrics([]);
			}
		}

		fetchLyrics();

		return () => {
			active = false;
			controller.abort();
		};
	}, [title, artist]);

	// Match active lyric based on positionMs
	useEffect(() => {
		if (lyrics.length === 0) {
			setActiveLyric(null);
			setNextLyric(null);
			return;
		}

		let currentIdx = -1;
		for (let i = 0; i < lyrics.length; i++) {
			if (positionMs >= lyrics[i].timeMs) {
				currentIdx = i;
			} else {
				break;
			}
		}

		if (currentIdx >= 0) {
			setActiveLyric(lyrics[currentIdx].text);
			setNextLyric(lyrics[currentIdx + 1]?.text ?? null);
		} else {
			setActiveLyric(lyrics[0]?.text ?? null);
			setNextLyric(lyrics[1]?.text ?? null);
		}
	}, [lyrics, positionMs]);

	return {
		lyrics,
		activeLyric,
		nextLyric,
		hasLyrics: lyrics.length > 0
	};
}
