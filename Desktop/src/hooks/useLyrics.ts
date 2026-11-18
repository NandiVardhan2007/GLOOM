import { useState, useEffect, useRef } from "react";
import { invoke } from "@tauri-apps/api/core";

export interface LyricLine {
	timeMs: number;
	text: string;
}

interface LrcLibResponse {
	id?: number;
	trackName?: string;
	artistName?: string;
	syncedLyrics?: string;
	plainLyrics?: string;
	duration?: number;
}

interface RustLyricsResult {
	id?: number;
	track_name?: string;
	artist_name?: string;
	synced_lyrics?: string;
	plain_lyrics?: string;
	lines: { time_ms: number; text: string }[];
	is_synced: boolean;
}

/**
 * Strips extraneous video, audio, bracket, and platform metadata noise
 * from song titles and artist names.
 */
export function cleanMediaMetadata(title = "", artist = ""): {
	cleanTitle: string;
	cleanArtist: string;
	primaryArtist: string;
} {
	let t = (title || "").trim();
	let a = (artist || "").trim();

	// Strip common audio/video file extensions
	t = t.replace(/\.(mp3|flac|wav|m4a|aac|ogg|wma|opus)$/i, "").trim();

	// Strip leading track numbers like "01. ", "01 - ", "1. "
	t = t.replace(/^\d+[\s.\-_]+/, "").trim();

	// Strip common video/audio/bracket noise
	// e.g. [Official Video], (Lyrics), (Visualizer), (Audio), (Lyric Video), [MV], (4K), (From "...")
	t = t
		.replace(
			/\s*[\(\[][^()\[\]]*(feat|ft\.|official|video|audio|remaster|version|from|ost|soundtrack|lyrics|lyric|visualizer|prod\.|mv|hd|4k|hq|clip)[^()\[\]]*[\)\]]/gi,
			""
		)
		.trim();

	// Strip trailing " - Single", " - EP", " - Deluxe", " - Remastered"
	t = t.replace(/\s*-\s*(single|ep|deluxe|remastered|remaster)\b/gi, "").trim();

	// Strip YouTube pipe suffixes like " | Official Video", " | T-Series", " | Lyrics"
	t = t.replace(/\s*\|.*$/g, "").trim();

	// Clean artist: remove parens/brackets and " - Topic"
	a = a.replace(/\s*[\(\[][^()\[\]]*[\)\]]/gi, "").trim();
	a = a.replace(/\s*-\s*topic$/i, "").trim();

	// Check if artist is empty or a generic browser / platform name
	const isGenericArtist =
		!a ||
		/^(youtube|youtube\s*music|google\s*chrome|chrome|brave|firefox|edge|spotify\s*free|unknown\s*artist)$/i.test(
			a
		);

	if (isGenericArtist) {
		// Try splitting "Artist - Track" from title
		for (const sep of [" - ", " – ", " — ", " : "]) {
			if (t.includes(sep)) {
				const parts = t.split(sep);
				if (parts.length >= 2 && parts[0].trim() && parts[1].trim()) {
					a = parts[0].trim();
					t = parts.slice(1).join(sep).trim();
					break;
				}
			}
		}
	}

	// Extract primary artist (before comma, &, /, x, feat., ft.)
	const primaryArtist = a
		.split(/[,&/]|(\s+feat\.?\s+)|\s+ft\.?\s+|\s+x\s+/i)[0]
		.replace(/\.{2,}/g, "")
		.trim();

	return {
		cleanTitle: t.trim(),
		cleanArtist: a.trim(),
		primaryArtist: primaryArtist || a.trim()
	};
}

export function parseLrc(lrcText: string): LyricLine[] {
	const lines: LyricLine[] = [];
	const lineRegex = /\[(\d+):(\d+(?:\.\d+)?)\](.*)/;
	for (const rawLine of lrcText.split("\n")) {
		const match = lineRegex.exec(rawLine.trim());
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

// In-memory cache for fetched lyrics to prevent duplicate network calls
const lyricsCache = new Map<string, LyricLine[]>();

export function useLyrics(
	title?: string,
	artist?: string,
	positionMs = 0,
	durationMs?: number
) {
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

		const { cleanTitle, cleanArtist, primaryArtist } = cleanMediaMetadata(title, artist);
		const cacheKey = `${primaryArtist || cleanArtist} - ${cleanTitle}`.toLowerCase();

		if (lastKeyRef.current === cacheKey && lyrics.length > 0) return;
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
				// 1. Try native Rust backend fetch command first (bypasses browser CSP/CORS, uses pooled HTTP client)
				try {
					const rustResult = await invoke<RustLyricsResult>("fetch_lyrics", {
						title: cleanTitle,
						artist: cleanArtist || primaryArtist,
						durationMs: durationMs && durationMs > 0 ? durationMs : undefined
					});
					if (rustResult && rustResult.lines && rustResult.lines.length > 0) {
						if (!active) return;
						const parsed = rustResult.lines.map((l) => ({
							timeMs: l.time_ms,
							text: l.text
						}));
						lyricsCache.set(cacheKey, parsed);
						setLyrics(parsed);
						setIsLoading(false);
						return;
					}
				} catch {
					// Fall through to client-side fetch fallback
				}

				// 2. Client-side fetch fallback (LRCLIB)
				let data: LrcLibResponse | null = null;

				// 2a. Try exact get if primary artist is known
				if (primaryArtist && !primaryArtist.toLowerCase().includes("unknown")) {
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
				}

				// 2b. Fallback to /api/search if exact get returned 404 or lacked synced lyrics
				if (!data || (!data.syncedLyrics && !data.plainLyrics)) {
					const searchEndpoints = [
						`https://lrclib.net/api/search?track_name=${encodeURIComponent(cleanTitle)}&artist_name=${encodeURIComponent(primaryArtist)}`,
						`https://lrclib.net/api/search?q=${encodeURIComponent(`${cleanTitle} ${primaryArtist}`.trim())}`,
						`https://lrclib.net/api/search?track_name=${encodeURIComponent(cleanTitle)}`
					];

					for (const endpoint of searchEndpoints) {
						try {
							const searchRes = await fetch(endpoint, {
								signal: controller.signal
							});
							if (searchRes.ok) {
								const searchData: LrcLibResponse[] = await searchRes.json();
								if (Array.isArray(searchData) && searchData.length > 0) {
									// Pick candidate with synced lyrics closest to duration, else first with synced, else first
									if (durationMs && durationMs > 0) {
										const targetSecs = durationMs / 1000;
										const withSynced = searchData.filter((i) => !!i.syncedLyrics);
										if (withSynced.length > 0) {
											withSynced.sort((a, b) => {
												const da = Math.abs((a.duration ?? 0) - targetSecs);
												const db = Math.abs((b.duration ?? 0) - targetSecs);
												return da - db;
											});
											data = withSynced[0];
										} else {
											data = searchData[0];
										}
									} else {
										const withSynced = searchData.find((item) => !!item.syncedLyrics);
										data = withSynced || searchData[0];
									}
									if (data && (data.syncedLyrics || data.plainLyrics)) {
										break;
									}
								}
							}
						} catch {
							// Continue to next endpoint variant
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
	}, [title, artist, durationMs]);

	// Match active lyric based on positionMs with binary search / fast lookup
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
