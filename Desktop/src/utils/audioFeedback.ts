/**
 * Synthesized Apple-grade tactile sound feedback using the Web Audio API.
 * Produces crisp, zero-latency micro-transients (<30ms) with zero external assets.
 */

export type SoundFeedbackType = "snap" | "tick" | "switch" | "pop";

export interface SoundProfile {
	startFreq: number;
	endFreq: number;
	duration: number; // in seconds
	type: OscillatorType;
	gain: number;
}

export const SOUND_PROFILES: Record<SoundFeedbackType, SoundProfile> = {
	snap: {
		startFreq: 820,
		endFreq: 220,
		duration: 0.022,
		type: "sine",
		gain: 0.28
	},
	tick: {
		startFreq: 1400,
		endFreq: 1100,
		duration: 0.009,
		type: "triangle",
		gain: 0.16
	},
	switch: {
		startFreq: 440,
		endFreq: 580,
		duration: 0.018,
		type: "sine",
		gain: 0.22
	},
	pop: {
		startFreq: 320,
		endFreq: 180,
		duration: 0.026,
		type: "sine",
		gain: 0.25
	}
};

let audioCtx: AudioContext | null = null;
let soundEnabled = true;
let soundVolume = 0.5;

// Load persisted user preference if in browser environment
if (typeof localStorage !== "undefined") {
	const storedEnabled = localStorage.getItem("gloom-sound-effects-enabled");
	if (storedEnabled !== null) {
		soundEnabled = storedEnabled !== "false";
	}
	const storedVol = localStorage.getItem("gloom-sound-effects-volume");
	if (storedVol !== null) {
		const parsed = parseFloat(storedVol);
		if (!isNaN(parsed)) soundVolume = Math.max(0, Math.min(1, parsed));
	}
}

function getAudioContext(): AudioContext | null {
	if (typeof window === "undefined") return null;
	const AudioContextClass =
		window.AudioContext ||
		(window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
	if (!AudioContextClass) return null;

	if (!audioCtx) {
		audioCtx = new AudioContextClass();
	}
	if (audioCtx.state === "suspended") {
		audioCtx.resume().catch(() => {});
	}
	return audioCtx;
}

export function isSoundFeedbackEnabled(): boolean {
	return soundEnabled;
}

export function setSoundFeedbackEnabled(enabled: boolean): void {
	soundEnabled = enabled;
	if (typeof localStorage !== "undefined") {
		localStorage.setItem("gloom-sound-effects-enabled", String(enabled));
	}
}

export function getSoundFeedbackVolume(): number {
	return soundVolume;
}

export function setSoundFeedbackVolume(volume: number): void {
	soundVolume = Math.max(0, Math.min(1, volume));
	if (typeof localStorage !== "undefined") {
		localStorage.setItem("gloom-sound-effects-volume", String(soundVolume));
	}
}

/**
 * Synthesizes and plays a tactile transient.
 */
export function playTactileSound(type: SoundFeedbackType): void {
	if (!soundEnabled || soundVolume <= 0) return;

	try {
		const ctx = getAudioContext();
		if (!ctx) return;

		const profile = SOUND_PROFILES[type];
		if (!profile) return;

		const now = ctx.currentTime;
		const osc = ctx.createOscillator();
		const gain = ctx.createGain();

		osc.type = profile.type;
		osc.frequency.setValueAtTime(profile.startFreq, now);
		osc.frequency.exponentialRampToValueAtTime(Math.max(10, profile.endFreq), now + profile.duration);

		const targetGain = profile.gain * soundVolume;
		gain.gain.setValueAtTime(targetGain, now);
		gain.gain.exponentialRampToValueAtTime(0.0001, now + profile.duration);

		osc.connect(gain);
		gain.connect(ctx.destination);

		osc.start(now);
		osc.stop(now + profile.duration);
	} catch {
		// AudioContext failures (e.g. autoplay restriction before interaction) are non-fatal
	}
}
