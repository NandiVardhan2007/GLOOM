export type MascotMood =
	| "idle"
	| "listening"
	| "thinking"
	| "happy"
	| "sleepy"
	| "dizzy";

export interface EyeGaze {
	x: number; // -1.0 to 1.0
	y: number; // -1.0 to 1.0
}

export interface SpringConfig {
	tension: number;
	friction: number;
}

export interface MascotFaceProps {
	mood?: MascotMood;
	isListening?: boolean;
	isPlayingMusic?: boolean;
	micLevel?: number; // 0.0 to 1.0
	scale?: number;
	className?: string;
	onPoke?: (clickCount: number) => void;
}
