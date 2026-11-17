import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { formatRelativeTime, countWordsAndChars, truncateSnippet } from "../src/components/cards/productivityUtils.ts";

describe("Productivity Drawer Utilities", () => {
	it("formats relative times cleanly", () => {
		const now = 1000000;
		assert.equal(formatRelativeTime(now - 5000, now), "Just now");
		assert.equal(formatRelativeTime(now - 30000, now), "30s ago");
		assert.equal(formatRelativeTime(now - 120000, now), "2m ago");
		assert.equal(formatRelativeTime(now - 7200000, now), "2h ago");
		assert.equal(formatRelativeTime(now - 86400000 * 3, now), "3d ago");
	});

	it("accurately counts words and characters", () => {
		assert.deepEqual(countWordsAndChars(""), { words: 0, chars: 0 });
		assert.deepEqual(countWordsAndChars("   "), { words: 0, chars: 3 });
		assert.deepEqual(countWordsAndChars("Hello world!"), { words: 2, chars: 12 });
		assert.deepEqual(countWordsAndChars("One  two   three \n four"), { words: 4, chars: 23 });
	});

	it("truncates long snippets with ellipsis", () => {
		assert.equal(truncateSnippet("Short text", 20), "Short text");
		assert.equal(truncateSnippet("A long sentence that exceeds length", 15), "A long sentence…");
	});
});
