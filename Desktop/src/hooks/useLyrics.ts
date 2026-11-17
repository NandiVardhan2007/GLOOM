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
	const [activeLyricIndex, setActiveLyricIndex] = useState<number>(-1);
	const [nextLyric, setNextLyric] = useState<string | null>(null);
	const [isLoading, setIsLoading] = useState(false);
	const lastKeyRef = useRef<string>("");

	useEffect(() => {
		if (!title || title === "Unknown Title") {
			setLyrics([]);
			setActiveLyric(null);
			setActiveLyricIndex(-1);
			setNextLyric(null);
			setIsLoading(false);
			lastKeyRef.current = "";
			return;
		}

		// Strip common extraneous metadata: "(feat. ...)", "[Official ...]", "(From "...")", etc.
		const cleanTitle = title
			.replace(/\s*[\(\[][^()\[\]]*(feat|ft\.|official|video|audio|remaster|version|from|ost|soundtrack)[^()\[\]]*[\)\]]/gi, "")
			.replace(/\s*-\s*(single|ep|deluxe|remastered|remaster)\b/gi, "")
			.trim();
		const cleanArtist = (artist || "")
			.replace(/\s*[\(\[][^()\[\]]*[\)\]]/gi, "")
			.replace(/\.{2,}/g, "")
			.trim();
		const primaryArtist = cleanArtist.split(/[,&/]/)[0].trim();
		const cacheKey = `${primaryArtist} - ${cleanTitle}`.toLowerCase();

		if (lastKeyRef.current === cacheKey) return;
		lastKeyRef.current = cacheKey;

		if (lyricsCache.has(cacheKey)) {
			setLyrics(lyricsCache.get(cacheKey)!);
			setIsLoading(false);
			return;
		}

		let active = true;
		setIsLoading(true);
		const controller = new AbortController();

		async function fetchLyrics() {
			try {
				let data: LrcLibResponse | null = null;

				// 1. Try exact get with primary artist first
				try {
					const params = new URLSearchParams({
						track_name: cleanTitle,
						artist_name: primaryArtist
					});
					const res = await fetch(`https://lrclib.net/api/get?${params.toString()}`, {
						signal: controller.signal
					});
					if (res.ok) {
						data = await res.json();
					}
				} catch {
					// Fall through to search query
				}

				// 2. Fallback to /api/search if exact get returned 404 or had no synced lyrics
				if (!data || (!data.syncedLyrics && !data.plainLyrics)) {
					const query = `${cleanTitle} ${primaryArtist}`.trim();
					const searchRes = await fetch(`https://lrclib.net/api/search?q=${encodeURIComponent(query)}`, {
						signal: controller.signal
					});
					if (searchRes.ok) {
						const searchData = await searchRes.json();
						if (Array.isArray(searchData) && searchData.length > 0) {
							// Prefer result with syncedLyrics
							const withSynced = searchData.find((item: any) => !!item.syncedLyrics);
							data = withSynced || searchData[0];
						}
					}
				}

				if (!active) return;
				setIsLoading(false);

				if (data && data.syncedLyrics) {
					const parsed = parseLrc(data.syncedLyrics);
					lyricsCache.set(cacheKey, parsed);
					setLyrics(parsed);
				} else if (data && data.plainLyrics) {
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
				if (active) {
					setIsLoading(false);
					setLyrics([]);
				}
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
			setActiveLyricIndex(-1);
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
			setActiveLyricIndex(currentIdx);
			setNextLyric(lyrics[currentIdx + 1]?.text ?? null);
		} else {
			setActiveLyric(lyrics[0]?.text ?? null);
			setActiveLyricIndex(0);
			setNextLyric(lyrics[1]?.text ?? null);
		}
	}, [lyrics, positionMs]);

	return {
		lyrics,
		activeLyric,
		activeLyricIndex,
		nextLyric,
		hasLyrics: lyrics.length > 0,
		isSynced: lyrics.some((l) => l.timeMs > 0),
		isLoading
	};
}
