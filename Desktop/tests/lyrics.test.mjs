import test from "node:test";
import assert from "node:assert/strict";

// Mirror of cleanMediaMetadata & parseLrc for node unit testing
function cleanMediaMetadata(title = "", artist = "") {
	let t = (title || "").trim();
	let a = (artist || "").trim();

	t = t.replace(/\.(mp3|flac|wav|m4a|aac|ogg|wma|opus)$/i, "").trim();
	t = t.replace(/^\d+[\s.\-_]+/, "").trim();
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
