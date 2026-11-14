import React, { useEffect, useRef, useState, useCallback } from "react";
import type { MascotFaceProps, MascotMood } from "./mascot/types";
import { stepSpring, dampGaze } from "./mascot/springMath";
import "./MascotFace.css";

export const MascotFace: React.FC<MascotFaceProps> = ({
	mood = "idle",
	isListening = false,
	isPlayingMusic = false,
	micLevel = 0,
	scale = 1.0,
	className = "",
	onPoke
}) => {
	const [activeMood, setActiveMood] = useState<MascotMood>(mood);
	const [pokeCount, setPokeCount] = useState(0);

	// Spring state refs
	const eyeOpenRef = useRef({ pos: 1.0, vel: 0, target: 1.0 });
	const hopRef = useRef({ pos: 0, vel: 0, target: 0 });
	const gazeRef = useRef({ xPos: 0, xVel: 0, xTarget: 0, yPos: 0, yVel: 0, yTarget: 0 });
	const dizzyAngleRef = useRef(0);

	const [, setRenderTick] = useState(0);
	const animFrameRef = useRef<number | null>(null);
	const lastFrameTimeRef = useRef<number>(performance.now());
	const pokeTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
	const dizzyTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

	// Synchronize external mood prop
	useEffect(() => {
		if (activeMood !== "dizzy") {
			setActiveMood(mood);
		}
	}, [mood, activeMood]);

	// Natural blinking loop when idle
	useEffect(() => {
		let timeoutId: ReturnType<typeof setTimeout>;

		const scheduleNextBlink = () => {
			const delay = 2500 + Math.random() * 3000; // 2.5s - 5.5s
			timeoutId = setTimeout(() => {
				if (activeMood === "idle" || activeMood === "happy") {
					// Close eyes
					eyeOpenRef.current.target = 0.05;
					startAnimationLoop();

					setTimeout(() => {
						// Open eyes
						eyeOpenRef.current.target = 1.0;
						startAnimationLoop();

						// 25% chance of rapid double-blink
						if (Math.random() < 0.25) {
							setTimeout(() => {
								eyeOpenRef.current.target = 0.05;
								startAnimationLoop();
								setTimeout(() => {
									eyeOpenRef.current.target = 1.0;
									startAnimationLoop();
								}, 120);
							}, 150);
						}
					}, 110);
				}
				scheduleNextBlink();
			}, delay);
		};

		scheduleNextBlink();
		return () => clearTimeout(timeoutId);
	}, [activeMood]);

	// Animation solver loop
	const startAnimationLoop = useCallback(() => {
		if (animFrameRef.current !== null) return;

		const loop = (now: number) => {
			const dt = Math.min((now - lastFrameTimeRef.current) / 1000, 0.05);
			lastFrameTimeRef.current = now;

			// Step eye openness spring
			const [nextEyePos, nextEyeVel] = stepSpring(
				eyeOpenRef.current.pos,
				eyeOpenRef.current.target,
				eyeOpenRef.current.vel,
				240,
				14,
				dt
			);
			eyeOpenRef.current.pos = nextEyePos;
			eyeOpenRef.current.vel = nextEyeVel;

			// Step hop spring
			const [nextHopPos, nextHopVel] = stepSpring(
				hopRef.current.pos,
				hopRef.current.target,
				hopRef.current.vel,
				220,
				12,
				dt
			);
			hopRef.current.pos = nextHopPos;
			hopRef.current.vel = nextHopVel;

			// Step gaze springs
			const [nextGazeX, nextGazeXVel] = stepSpring(
				gazeRef.current.xPos,
				gazeRef.current.xTarget,
				gazeRef.current.xVel,
				180,
				14,
				dt
			);
			const [nextGazeY, nextGazeYVel] = stepSpring(
				gazeRef.current.yPos,
				gazeRef.current.yTarget,
				gazeRef.current.yVel,
				180,
				14,
				dt
			);
			gazeRef.current.xPos = nextGazeX;
			gazeRef.current.xVel = nextGazeXVel;
			gazeRef.current.yPos = nextGazeY;
			gazeRef.current.yVel = nextGazeYVel;

			if (activeMood === "dizzy") {
				dizzyAngleRef.current = (dizzyAngleRef.current + 360 * dt * 2.5) % 360;
			}

			setRenderTick((t) => (t + 1) % 1000000);

			// Check if springs are settled
			const eyeSettled =
				Math.abs(eyeOpenRef.current.pos - eyeOpenRef.current.target) < 0.002 &&
				Math.abs(eyeOpenRef.current.vel) < 0.005;
			const hopSettled =
				Math.abs(hopRef.current.pos - hopRef.current.target) < 0.002 &&
				Math.abs(hopRef.current.vel) < 0.005;
			const gazeSettled =
				Math.abs(gazeRef.current.xPos - gazeRef.current.xTarget) < 0.005 &&
				Math.abs(gazeRef.current.yPos - gazeRef.current.yTarget) < 0.005;

			if (eyeSettled && hopSettled && gazeSettled && activeMood !== "dizzy") {
				animFrameRef.current = null;
			} else {
				animFrameRef.current = requestAnimationFrame(loop);
			}
		};

		lastFrameTimeRef.current = performance.now();
		animFrameRef.current = requestAnimationFrame(loop);
	}, [activeMood]);

	// Trigger animation on props change
	useEffect(() => {
		startAnimationLoop();
	}, [startAnimationLoop, isListening, micLevel, activeMood]);

	// Poking handler
	const handleClick = (e: React.MouseEvent) => {
		e.stopPropagation();
		const nextCount = pokeCount + 1;
		setPokeCount(nextCount);
		onPoke?.(nextCount);

		if (pokeTimerRef.current) clearTimeout(pokeTimerRef.current);
		pokeTimerRef.current = setTimeout(() => {
			setPokeCount(0);
		}, 1400);

		if (nextCount >= 4) {
			// Trigger dizzy mood for 2.2 seconds!
			setActiveMood("dizzy");
			startAnimationLoop();
			if (dizzyTimerRef.current) clearTimeout(dizzyTimerRef.current);
			dizzyTimerRef.current = setTimeout(() => {
				setActiveMood(mood);
				setPokeCount(0);
			}, 2200);
		} else {
			// Normal poke hop and blink
			hopRef.current.pos = -4.5;
			hopRef.current.vel = -8;
			eyeOpenRef.current.pos = 0.2;
			startAnimationLoop();
		}
	};

	// Mouse move gaze tracking inside pill
	const handleMouseMove = (e: React.MouseEvent<HTMLDivElement>) => {
		const rect = e.currentTarget.getBoundingClientRect();
		const relX = (e.clientX - (rect.left + rect.width / 2)) / (rect.width / 2);
		const relY = (e.clientY - (rect.top + rect.height / 2)) / (rect.height / 2);
		const [clampedX, clampedY] = dampGaze(relX * 2.0, relY * 1.5, 2.2);

		gazeRef.current.xTarget = clampedX;
		gazeRef.current.yTarget = clampedY;
		startAnimationLoop();
	};

	const handleMouseLeave = () => {
		gazeRef.current.xTarget = 0;
		gazeRef.current.yTarget = 0;
		startAnimationLoop();
	};

	// Eye open multiplier (accounts for mic reactivity when listening)
	const rawOpen = eyeOpenRef.current.pos;
	const listeningDilation = isListening ? 1.0 + Math.min(micLevel, 1.0) * 0.35 : 1.0;
	const eyeHeight = Math.max(1.2, 7.5 * rawOpen * listeningDilation);
	const hopY = hopRef.current.pos;

	const gazeX = gazeRef.current.xPos;
	const gazeY = gazeRef.current.yPos;

	return (
		<div
			className={`mascot-face-container ${className}`}
			style={{
				width: `${38 * scale}px`,
				height: `${20 * scale}px`,
				transform: `scale(${scale})`
			}}
			onClick={handleClick}
			onMouseMove={handleMouseMove}
			onMouseLeave={handleMouseLeave}
			title="Nixi"
		>
			<svg
				className="mascot-face-svg"
				viewBox="0 0 38 20"
				width="38"
				height="20"
				style={{
					transform: `translateY(${hopY}px)`
				}}
			>
				{/* DJ Over-Ear Headphones (Auto-equipped during music playback) */}
				{isPlayingMusic && (
					<g className="mascot-headphones" opacity="0.95">
						{/* Headband arch */}
						<path
							d="M 5,12 C 5,1 33,1 33,12"
							fill="none"
							stroke="#00E5FF"
							strokeWidth="2.4"
							strokeLinecap="round"
						/>
						{/* Left earcup */}
						<rect
							x="2.5"
							y="8"
							width="3.5"
							height="8"
							rx="1.75"
							fill="#00E5FF"
						/>
						{/* Right earcup */}
						<rect
							x="32"
							y="8"
							width="3.5"
							height="8"
							rx="1.75"
							fill="#00E5FF"
						/>
					</g>
				)}

				{/* Expression rendering */}
				{activeMood === "happy" ? (
					/* Heart Eyes */
					<g fill="#FF2D55">
						{/* Left Heart */}
						<path d="M 12,6 A 2.2,2.2 0 0,0 8.5,8 C 8.5,10.2 12,12.5 12,12.5 C 12,12.5 15.5,10.2 15.5,8 A 2.2,2.2 0 0,0 12,6 Z" />
						{/* Right Heart */}
						<path d="M 26,6 A 2.2,2.2 0 0,0 22.5,8 C 22.5,10.2 26,12.5 26,12.5 C 26,12.5 29.5,10.2 29.5,8 A 2.2,2.2 0 0,0 26,6 Z" />
					</g>
				) : activeMood === "sleepy" ? (
					/* Sleepy Smile Eye Arcs */
					<g stroke="#FFFFFF" strokeWidth="2" strokeLinecap="round" fill="none">
						<path d="M 8.5,10 Q 12,13 15.5,10" />
						<path d="M 22.5,10 Q 26,13 29.5,10" />
					</g>
				) : activeMood === "dizzy" ? (
					/* Swirling Spiral Eyes */
					<g
						stroke="#FFFFFF"
						strokeWidth="1.6"
						strokeLinecap="round"
						fill="none"
					>
						<g transform={`rotate(${dizzyAngleRef.current} 12 10)`}>
							<path d="M 12,10 m -4,0 a 4,4 0 1,0 8,0 a 2.5,2.5 0 1,0 -5,0" />
						</g>
						<g transform={`rotate(${-dizzyAngleRef.current} 26 10)`}>
							<path d="M 26,10 m -4,0 a 4,4 0 1,0 8,0 a 2.5,2.5 0 1,0 -5,0" />
						</g>
					</g>
				) : (
					/* Normal / Attentive Eyes */
					<g>
						{/* Left Sclera */}
						<ellipse
							cx="12"
							cy="10"
							rx="4.5"
							ry={eyeHeight / 2}
							fill="#FFFFFF"
						/>
						{/* Right Sclera */}
						<ellipse
							cx="26"
							cy="10"
							rx="4.5"
							ry={eyeHeight / 2}
							fill="#FFFFFF"
						/>

						{/* Pupils (visible when eye height is sufficient) */}
						{eyeHeight > 3 && (
							<>
								{/* Left Pupil */}
								<circle
									cx={12 + gazeX}
									cy={10 + gazeY}
									r={isListening ? 2.3 : 2.0}
									fill="#0F1015"
								/>
								{/* Left Specular Glint */}
								<circle
									cx={11.2 + gazeX * 0.7}
									cy={9.2 + gazeY * 0.7}
									r="0.75"
									fill="#FFFFFF"
								/>

								{/* Right Pupil */}
								<circle
									cx={26 + gazeX}
									cy={10 + gazeY}
									r={isListening ? 2.3 : 2.0}
									fill="#0F1015"
								/>
								{/* Right Specular Glint */}
								<circle
									cx={25.2 + gazeX * 0.7}
									cy={9.2 + gazeY * 0.7}
									r="0.75"
									fill="#FFFFFF"
								/>
							</>
						)}
					</g>
				)}

				{/* Thinking Indicator (3 floating dots) */}
				{activeMood === "thinking" && (
					<g fill="#FFFFFF" opacity="0.8">
						<circle cx="33" cy="4" r="0.8" />
						<circle cx="35" cy="3" r="1.0" />
						<circle cx="37" cy="2" r="1.2" />
					</g>
				)}
			</svg>

			{/* Happy floating hearts flourish */}
			{activeMood === "happy" && (
				<>
					<span className="mascot-heart-particle" style={{ left: "20%", ["--dx" as string]: "-8px" }}>
						♥
					</span>
					<span className="mascot-heart-particle" style={{ left: "70%", ["--dx" as string]: "10px", animationDelay: "0.2s" }}>
						♥
					</span>
				</>
			)}

			{/* Sleepy floating zZ */}
			{activeMood === "sleepy" && (
				<span className="mascot-sleep-z">zZ</span>
			)}
		</div>
	);
};
