import test from "node:test";
import assert from "node:assert/strict";

// Mirror of cleanMediaMetadata & parseLrc for node unit testing
function cleanMediaMetadata(title = "", artist = "") {
	let t = (title || "").trim();
	let a = (artist || "").trim();

	t = t.replace(/\.(mp3|flac|wav|m4a|aac|ogg|wma|opus)$/i, "").trim();
	t = t.replace(/^\d+[\s.\-_]+/, "").trim();

	a = a
		.replace(
			/\s*[\(\[][^()\[\]]*(original motion picture soundtrack|soundtrack|ost|deluxe|remaster|edition)[^()\[\]]*[\)\]]/gi,
			""
		)
		.trim();

	if (a.includes(" - ")) {
		const parts = a.split(" - ");
		if (parts[0].trim()) {
			a = parts[0].trim();
		}
	}

	t = t
		.replace(
			/\s*[\(\[][^()\[\]]*(feat|ft\.|official|video|audio|remaster|version|from|ost|soundtrack|lyrics|lyric|visualizer|prod\.|mv|hd|4k|hq|clip)[^()\[\]]*[\)\]]/gi,
			""
		)
		.trim();
	t = t.replace(/\s*-\s*(single|ep|deluxe|remastered|remaster)\b/gi, "").trim();
	t = t.replace(/\s*\|.*$/g, "").trim();
	a = a.replace(/\s*[\(\[][^()\[\]]*[\)\]]/gi, "").trim();
	a = a.replace(/\s*-\s*topic$/i, "").trim();

	const isGenericArtist =
		!a ||
		/^(youtube|youtube\s*music|google\s*chrome|chrome|brave|firefox|edge|spotify\s*free|unknown\s*artist)$/i.test(
			a
		);

	if (isGenericArtist) {
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

	let primaryTitle = t;
	let subtitle = undefined;
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

	const emojiRegex =
		/[\u{1F300}-\u{1F9FF}\u{2600}-\u{26FF}\u{2700}-\u{27BF}\u{FE00}-\u{FE0F}\u{1F000}-\u{1F2FF}\u{2022}\u{00B7}\u{2219}]/gu;
	primaryTitle = primaryTitle.replace(emojiRegex, "").trim();
	if (subtitle) {
		subtitle = subtitle.replace(emojiRegex, "").trim();
	}
	const cleanTitle = t.replace(emojiRegex, "").trim();
	a = a.replace(emojiRegex, "").trim();

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

function parseLrc(lrcText) {
	const lines = [];
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

test("cleanMediaMetadata: strips YouTube suffixes and official video tags", () => {
	const res = cleanMediaMetadata(
		"Adele - Rolling in the Deep (Official Music Video)",
		"YouTube"
	);
	assert.equal(res.cleanArtist, "Adele");
	assert.equal(res.cleanTitle, "Rolling in the Deep");
	assert.equal(res.primaryArtist, "Adele");
});

test("cleanMediaMetadata: strips bracketed lyric and audio markers", () => {
	const res = cleanMediaMetadata(
		"Queen - Bohemian Rhapsody [Lyrics]",
		"Google Chrome"
	);
	assert.equal(res.cleanArtist, "Queen");
	assert.equal(res.cleanTitle, "Bohemian Rhapsody");
});

test("cleanMediaMetadata: handles Spotify multi-artist with primary artist extraction", () => {
	const res = cleanMediaMetadata(
		"Die With A Smile",
		"Lady Gaga & Bruno Mars"
	);
	assert.equal(res.cleanTitle, "Die With A Smile");
	assert.equal(res.primaryArtist, "Lady Gaga");
});

test("cleanMediaMetadata: strips - Topic and file extensions", () => {
	const res = cleanMediaMetadata(
		"01. Blinding Lights.mp3",
		"The Weeknd - Topic"
	);
	assert.equal(res.cleanTitle, "Blinding Lights");
	assert.equal(res.cleanArtist, "The Weeknd");
	assert.equal(res.primaryArtist, "The Weeknd");
});

test("cleanMediaMetadata: handles multilingual title with bullets, emojis, and soundtrack artist", () => {
	const res = cleanMediaMetadata(
		"Kathakaadhey • 🎶 ఒదిలేయవే ఒదిలేయవే",
		"Anirudh Ravichander, Ajesh, Srinidhi Venkatesh - Remo (Telugu) [Original Motion Picture Soundtrack]"
	);
	assert.equal(res.primaryTitle, "Kathakaadhey");
	assert.equal(res.primaryArtist, "Anirudh Ravichander");
	assert.equal(res.cleanArtist, "Anirudh Ravichander, Ajesh, Srinidhi Venkatesh");
	assert.equal(res.subtitle, "ఒదిలేయవే ఒదిలేయవే");
});

test("parseLrc: accurately extracts millisecond timestamps and sorts lines", () => {
	const lrc = `
[00:13.13] Yeah
[00:27.16] I've been tryna call
[00:05.10] Intro music
	`;
	const lines = parseLrc(lrc);
	assert.equal(lines.length, 3);
	assert.equal(lines[0].timeMs, 5100);
	assert.equal(lines[0].text, "Intro music");
	assert.equal(lines[1].timeMs, 13130);
	assert.equal(lines[1].text, "Yeah");
	assert.equal(lines[2].timeMs, 27160);
	assert.equal(lines[2].text, "I've been tryna call");
});

function extractLiveLyricFromApp(rawTitle = "", rawSubtitle = "", rawArtist = "") {
	let cleanTitle = (rawTitle || "").trim();
	let liveLyric = null;

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

test("extractLiveLyricFromApp: extracts real-time lyric from bullet with emoji (user case)", () => {
	const res = extractLiveLyricFromApp("Kathakaadhey • 🎶 ఒదిలేయవే ఒదిలేయవే");
	assert.equal(res.cleanTitle, "Kathakaadhey");
	assert.equal(res.liveLyric, "ఒదిలేయవే ఒదిలేయవే");
});

test("extractLiveLyricFromApp: extracts lyric with eighth note symbol ♪ and ♫", () => {
	const res = extractLiveLyricFromApp("Inkem Inkem • ♪ గోవిందా గోవిందా");
	assert.equal(res.cleanTitle, "Inkem Inkem");
	assert.equal(res.liveLyric, "గోవిందా గోవిందా");
});

test("extractLiveLyricFromApp: extracts lyric from bullet without emoji", () => {
	const res = extractLiveLyricFromApp("Samajavaragamana • నీ కాళ్లను పట్టుకు వదలనన్నవి", "", "Sid Sriram");
	assert.equal(res.cleanTitle, "Samajavaragamana");
	assert.equal(res.liveLyric, "నీ కాళ్లను పట్టుకు వదలనన్నవి");
});

test("extractLiveLyricFromApp: extracts lyric from subtitle property", () => {
	const res = extractLiveLyricFromApp("Blinding Lights", "🎶 I said, ooh, I'm blinded by the lights");
	assert.equal(res.cleanTitle, "Blinding Lights");
	assert.equal(res.liveLyric, "I said, ooh, I'm blinded by the lights");
});

test("extractLiveLyricFromApp: extracts lyric from bracketed notation", () => {
	const res = extractLiveLyricFromApp("Flowers (🎶 I can buy myself flowers)");
	assert.equal(res.cleanTitle, "Flowers");
	assert.equal(res.liveLyric, "I can buy myself flowers");
});

test("extractLiveLyricFromApp: returns null for standard songs without live lyrics", () => {
	const res = extractLiveLyricFromApp("Bohemian Rhapsody", "");
	assert.equal(res.cleanTitle, "Bohemian Rhapsody");
	assert.equal(res.liveLyric, null);
});
