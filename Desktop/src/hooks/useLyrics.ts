import { useState, useEffect, useRef } from "react";
import { invoke } from "@tauri-apps/api/core";

export interface LyricLine {
	timeMs: number;
	text: string;
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
	primaryTitle: string;
	subtitle?: string;
} {
	let t = (title || "").trim();
	let a = (artist || "").trim();

	// Strip common audio/video file extensions
	t = t.replace(/\.(mp3|flac|wav|m4a|aac|ogg|wma|opus)$/i, "").trim();

	// Strip leading track numbers like "01. ", "01 - ", "1. "
	t = t.replace(/^\d+[\s.\-_]+/, "").trim();

	// Remove soundtrack and album bracketed noise from artist
	a = a
		.replace(
			/\s*[\(\[][^()\[\]]*(original motion picture soundtrack|soundtrack|ost|deluxe|remaster|edition)[^()\[\]]*[\)\]]/gi,
			""
		)
		.trim();

	// If artist contains " - " followed by movie/album info (e.g. "Anirudh ... - Remo (Telugu)"), strip after " - "
	if (a.includes(" - ")) {
		const parts = a.split(" - ");
		if (parts[0].trim()) {
			a = parts[0].trim();
		}
	}

	// Strip common video/audio/bracket noise from title
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

	// Extract primary title & subtitle if separated by bullet / pipe / tilde
	let primaryTitle = t;
	let subtitle: string | undefined;
	for (const sep of ["•", "·", "∙", "|", "~"]) {
		if (t.includes(sep)) {
			const parts = t.split(sep);
			if (parts[0].trim()) {
				primaryTitle = parts[0].trim();
				if (parts[1]?.trim()) {
					subtitle = parts[1].trim();
				}
				break;
			}
		}
	}

	// Strip emojis and symbols from primary title, subtitle & artist
	const emojiRegex =
		/[\u{1F300}-\u{1F9FF}\u{2600}-\u{26FF}\u{2700}-\u{27BF}\u{FE00}-\u{FE0F}\u{1F000}-\u{1F2FF}\u{2022}\u{00B7}\u{2219}]/gu;
	primaryTitle = primaryTitle.replace(emojiRegex, "").trim();
	if (subtitle) {
		subtitle = subtitle.replace(emojiRegex, "").trim();
	}
	const cleanTitle = t.replace(emojiRegex, "").trim();
	a = a.replace(emojiRegex, "").trim();

	// Extract primary artist (before comma, &, /, x, feat., ft.)
	const primaryArtist = a
		.split(/[,&/]|(\s+feat\.?\s+)|\s+ft\.?\s+|\s+x\s+/i)[0]
		.replace(/\.{2,}/g, "")
		.trim();

	return {
		cleanTitle: cleanTitle || t,
		cleanArtist: a,
		primaryArtist: primaryArtist || a,
		primaryTitle: primaryTitle || cleanTitle || t,
		subtitle
	};
}

/**
 * Extracts any real-time lyric line being broadcast directly by the music app
 * via media session title or subtitle (e.g. "Kathakaadhey • 🎶 ఒదిలేయవే ఒదిలేయవే").
 */
export function extractLiveLyricFromApp(rawTitle = "", rawSubtitle = "", rawArtist = ""): {
	cleanTitle: string;
	liveLyric: string | null;
} {
	let cleanTitle = (rawTitle || "").trim();
	let liveLyric: string | null = null;

	const emojiRegex =
		/[\u{1F300}-\u{1F9FF}\u{2600}-\u{26FF}\u{2700}-\u{27BF}\u{FE00}-\u{FE0F}\u{1F000}-\u{1F2FF}\u{2022}\u{00B7}\u{2219}\u{266A}\u{266B}\u{266C}\u{2669}]/gu;
	const musicSymbolRegex = /[🎶🎵🎤🎼🎧♪♫♬♩✨⭐]/u;

	// 1. Check if subtitle has an active lyric line
	if (rawSubtitle && rawSubtitle.trim()) {
		const subClean = rawSubtitle.replace(emojiRegex, "").trim();
		if (subClean && subClean.toLowerCase() !== (rawArtist || "").toLowerCase()) {
			liveLyric = subClean;
		}
	}

	// 2. Delimiter followed by music note / symbol: e.g. "Title • 🎶 Lyric", "Title - 🎵 Lyric", "Title | ♫ Lyric"
	const matchWithSymbol = rawTitle.match(/^(.+?)\s*[•·∙|~–—\-]\s*[🎶🎵🎤🎼🎧♪♫♬♩✨⭐]\s*(.+)$/u);
	if (matchWithSymbol) {
		cleanTitle = matchWithSymbol[1].replace(emojiRegex, "").trim();
		liveLyric = matchWithSymbol[2].replace(emojiRegex, "").trim();
		return { cleanTitle: cleanTitle || rawTitle, liveLyric: liveLyric || null };
	}

	// 3. Delimiter without explicit note emoji: e.g. "Title • Lyric"
	const bulletMatch = rawTitle.match(/^(.+?)\s*[•·∙|~]\s*(.+)$/u);
	if (bulletMatch) {
		const candidateTitle = bulletMatch[1].replace(emojiRegex, "").trim();
		const candidateLyric = bulletMatch[2].replace(emojiRegex, "").trim();
		const isArtistRepeat = candidateLyric.toLowerCase() === (rawArtist || "").toLowerCase();
		const isKnownSuffix = /^(official|video|audio|remix|edit|single|ep|deluxe|instrumental|acoustic)$/i.test(candidateLyric);
		if (candidateLyric && !isArtistRepeat && !isKnownSuffix) {
			cleanTitle = candidateTitle;
			// If contains music symbol anywhere or distinct lyric text
			if (musicSymbolRegex.test(rawTitle) || candidateLyric.length > 1) {
				liveLyric = candidateLyric;
			}
			return { cleanTitle: cleanTitle || rawTitle, liveLyric: liveLyric || null };
		}
	}

	// 4. Bracketed or parenthesized lyric with music symbol: e.g. "Track (🎶 Lyric)" or "Track [🎶 Lyric]"
	const bracketMatch = rawTitle.match(/^(.+?)\s*[\(\[]\s*[🎶🎵🎤🎼🎧♪♫♬♩✨⭐]\s*(.+?)[\)\]]$/u);
	if (bracketMatch) {
		cleanTitle = bracketMatch[1].replace(emojiRegex, "").trim();
		liveLyric = bracketMatch[2].replace(emojiRegex, "").trim();
		return { cleanTitle: cleanTitle || rawTitle, liveLyric: liveLyric || null };
	}

	// 5. If title itself starts with a music symbol: e.g. "🎶 Lyric"
	if (!liveLyric && musicSymbolRegex.test(rawTitle.slice(0, 4))) {
		liveLyric = rawTitle.replace(emojiRegex, "").trim();
	}

	return {
		cleanTitle: cleanTitle || rawTitle,
		liveLyric: liveLyric || null
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

// In-memory cache for fetched lyrics to prevent duplicate calls
const lyricsCache = new Map<string, LyricLine[]>();

export function useLyrics(
	title?: string,
	artist?: string,
	positionMs = 0,
	durationMs?: number,
	subtitle?: string
) {
	const [lyrics, setLyrics] = useState<LyricLine[]>([]);
	const [activeLyric, setActiveLyric] = useState<string | null>(null);
	const [activeLyricIndex, setActiveLyricIndex] = useState<number>(-1);
	const [nextLyric, setNextLyric] = useState<string | null>(null);
	const [isLoading, setIsLoading] = useState(false);
	const [isStreamingFromApp, setIsStreamingFromApp] = useState(false);
	const lastKeyRef = useRef<string>("");

	// Stable reference to current playback position to avoid aborting effects on 100ms clock ticks
	const positionMsRef = useRef(positionMs);
	positionMsRef.current = positionMs;

	// Dynamic live-streamed lyrics directly from the music app
	const [appStreamedLyrics, setAppStreamedLyrics] = useState<LyricLine[]>([]);
	const lastSongRef = useRef<string>("");
	const lastAppLyricRef = useRef<string>("");

	// 1. Extract any real-time lyric line being broadcast directly by the song app
	const appExtraction = extractLiveLyricFromApp(title || "", subtitle || "", artist || "");
	const appLiveLyric = appExtraction.liveLyric;
	const appCleanTitle = appExtraction.cleanTitle;

	// Reset live stream when song changes
	useEffect(() => {
		const songKey = `${artist || ""} - ${appCleanTitle}`.toLowerCase();
		if (lastSongRef.current !== songKey) {
			lastSongRef.current = songKey;
			lastAppLyricRef.current = "";
			setAppStreamedLyrics([]);
		}
	}, [appCleanTitle, artist]);

	// Accumulate real-time streamed lyrics as they arrive from the playing app
	useEffect(() => {
		if (appLiveLyric && appLiveLyric !== lastAppLyricRef.current) {
			lastAppLyricRef.current = appLiveLyric;
			setAppStreamedLyrics((prev) => {
				if (prev.length > 0 && prev[prev.length - 1].text === appLiveLyric) {
					return prev;
				}
				return [...prev, { timeMs: positionMsRef.current, text: appLiveLyric }];
			});
		}
	}, [appLiveLyric]);

	const hasAppLyrics = Boolean(appLiveLyric || appStreamedLyrics.length > 0);

	// Primary effect: manage streaming lyrics vs offline/local lyrics
	useEffect(() => {
		// When the song app itself has the lyrics directly, use them and DO NOT query online
		if (hasAppLyrics) {
			setIsStreamingFromApp(true);
			setIsLoading(false);
			const lines =
				appStreamedLyrics.length > 0
					? appStreamedLyrics
					: appLiveLyric
					? [{ timeMs: positionMsRef.current, text: appLiveLyric }]
					: [];
			setLyrics(lines);
			return;
		}

		setIsStreamingFromApp(false);

		if (!title || title === "Unknown Title") {
			setLyrics([]);
			setActiveLyric(null);
			setActiveLyricIndex(-1);
			setNextLyric(null);
			setIsLoading(false);
			lastKeyRef.current = "";
			return;
		}

		const { cleanTitle, cleanArtist, primaryArtist, primaryTitle } = cleanMediaMetadata(title, artist);
		const targetTitle = primaryTitle || cleanTitle;
		const targetArtist = primaryArtist || cleanArtist;
		const cacheKey = `${targetArtist} - ${targetTitle}`.toLowerCase();

		if (lastKeyRef.current === cacheKey && lyrics.length > 0) return;
		lastKeyRef.current = cacheKey;

		if (lyricsCache.has(cacheKey)) {
			setLyrics(lyricsCache.get(cacheKey)!);
			setIsLoading(false);
			return;
		}

		let active = true;
		setIsLoading(true);

		async function fetchLyrics() {
			try {
				const rustResult = await invoke<RustLyricsResult>("fetch_lyrics", {
					title: targetTitle,
					artist: targetArtist,
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
				// Local / song app prioritized
			}

			if (active) {
				setIsLoading(false);
				setLyrics([]);
			}
		}

		fetchLyrics();

		return () => {
			active = false;
		};
	}, [hasAppLyrics, appLiveLyric, appStreamedLyrics, title, artist, durationMs]);

	const retryFetch = () => {
		lastKeyRef.current = "";
		setLyrics([]);
		setIsLoading(true);
	};

	// Match active lyric based on streaming player state or positionMs
	useEffect(() => {
		if (isStreamingFromApp) {
			if (appLiveLyric) {
				setActiveLyric(appLiveLyric);
				const idx = lyrics.findIndex((l) => l.text === appLiveLyric);
				setActiveLyricIndex(idx >= 0 ? idx : Math.max(0, lyrics.length - 1));
				setNextLyric(null);
			} else if (lyrics.length > 0) {
				const lastIdx = lyrics.length - 1;
				setActiveLyric(lyrics[lastIdx].text);
				setActiveLyricIndex(lastIdx);
				setNextLyric(null);
			}
			return;
		}

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
	}, [lyrics, positionMs, isStreamingFromApp, appLiveLyric]);

	const meta = cleanMediaMetadata(title, artist);

	return {
		lyrics,
		activeLyric,
		activeLyricIndex,
		nextLyric,
		hasLyrics: lyrics.length > 0 || hasAppLyrics,
		isSynced: true,
		isLoading,
		isStreamingFromApp,
		cleanTitle: appCleanTitle || meta.primaryTitle || meta.cleanTitle,
		cleanArtist: meta.cleanArtist,
		primaryArtist: meta.primaryArtist,
		subtitle: appLiveLyric || meta.subtitle,
		retryFetch
	};
}
