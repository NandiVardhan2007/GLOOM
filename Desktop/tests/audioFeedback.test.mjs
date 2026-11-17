import { describe, it } from "node:test";
import assert from "node:assert/strict";
import {
	SOUND_PROFILES,
	isSoundFeedbackEnabled,
	setSoundFeedbackEnabled,
	getSoundFeedbackVolume,
	setSoundFeedbackVolume
} from "../src/utils/audioFeedback.ts";

describe("Audio Feedback Engine", () => {
	it("exposes sound profiles with valid frequencies and durations", () => {
		assert.ok(SOUND_PROFILES.snap);
		assert.ok(SOUND_PROFILES.tick);
		assert.ok(SOUND_PROFILES.switch);
		assert.ok(SOUND_PROFILES.pop);

		// Transient durations must be < 50ms for crisp tactile feedback
		assert.ok(SOUND_PROFILES.snap.duration <= 0.05);
		assert.ok(SOUND_PROFILES.tick.duration <= 0.05);
		assert.ok(SOUND_PROFILES.switch.duration <= 0.05);
		assert.ok(SOUND_PROFILES.pop.duration <= 0.05);

		// Frequencies must be in audible range
		assert.ok(SOUND_PROFILES.snap.startFreq > 100);
		assert.ok(SOUND_PROFILES.tick.startFreq > 100);
	});

	it("controls sound enabled state", () => {
		setSoundFeedbackEnabled(false);
		assert.equal(isSoundFeedbackEnabled(), false);
		setSoundFeedbackEnabled(true);
		assert.equal(isSoundFeedbackEnabled(), true);
	});

	it("clamps volume bounds between 0.0 and 1.0", () => {
		setSoundFeedbackVolume(0.8);
		assert.equal(getSoundFeedbackVolume(), 0.8);
		setSoundFeedbackVolume(1.5);
		assert.equal(getSoundFeedbackVolume(), 1.0);
		setSoundFeedbackVolume(-0.5);
		assert.equal(getSoundFeedbackVolume(), 0.0);
		setSoundFeedbackVolume(0.5);
	});
});
