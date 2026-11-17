import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { rgbToHsl, DEFAULT_PALETTE } from "../src/utils/colorExtractor.ts";

describe("Color Extractor Engine", () => {
	it("exposes default OLED glow palette", () => {
		assert.ok(DEFAULT_PALETTE.primary);
		assert.ok(DEFAULT_PALETTE.secondary);
		assert.ok(DEFAULT_PALETTE.accent);
	});

	it("converts RGB to HSL correctly", () => {
		// Pure Red
		const [h1, s1, l1] = rgbToHsl(255, 0, 0);
		assert.equal(Math.round(h1), 0);
		assert.equal(Math.round(s1 * 100), 100);
		assert.equal(Math.round(l1 * 100), 50);

		// Pure Green
		const [h2, s2, l2] = rgbToHsl(0, 255, 0);
		assert.equal(Math.round(h2), 120);
		assert.equal(Math.round(s2 * 100), 100);
		assert.equal(Math.round(l2 * 100), 50);

		// Pure White
		const [, , l3] = rgbToHsl(255, 255, 255);
		assert.equal(Math.round(l3 * 100), 100);

		// Pure Black
		const [, , l4] = rgbToHsl(0, 0, 0);
		assert.equal(Math.round(l4 * 100), 0);
	});
});
