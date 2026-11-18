import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { formatBytes, formatUpdateProgressMeta } from "../src/settings/updaterUtils.ts";

describe("Updater Progress Utilities", () => {
	it("formats bytes correctly to megabytes", () => {
		assert.equal(formatBytes(0), "0 MB");
		assert.equal(formatBytes(1024 * 1024), "1.0 MB");
		assert.equal(formatBytes(15.5 * 1024 * 1024), "15.5 MB");
		assert.equal(formatBytes(42 * 1024 * 1024), "42.0 MB");
	});

	it("formats progress when both downloaded and total bytes are known", () => {
		const meta = formatUpdateProgressMeta("downloading", 50, 20 * 1024 * 1024, 40 * 1024 * 1024);
		assert.equal(meta.text, "20.0 MB of 40.0 MB");
		assert.equal(meta.pct, 50);
		assert.equal(meta.isIndeterminate, false);
	});

	it("formats initial connecting state", () => {
		const meta = formatUpdateProgressMeta("downloading", 0, 0, 0);
		assert.equal(meta.text, "Connecting to server...");
		assert.equal(meta.pct, 0);
		assert.equal(meta.isIndeterminate, true);
	});

	it("formats installing state", () => {
		const meta = formatUpdateProgressMeta("installing", 100, 40 * 1024 * 1024, 40 * 1024 * 1024);
		assert.equal(meta.text, "Restarting GLOOM to apply update...");
		assert.equal(meta.pct, 100);
		assert.equal(meta.isIndeterminate, false);
	});

	it("clamps percentage between 0 and 100", () => {
		const low = formatUpdateProgressMeta("downloading", -5, 0, 100);
		assert.equal(low.pct, 0);

		const high = formatUpdateProgressMeta("downloading", 125, 100, 100);
		assert.equal(high.pct, 100);
	});
});
