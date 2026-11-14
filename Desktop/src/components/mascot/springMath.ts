/**
 * Damped harmonic oscillator step using semi-implicit Euler integration.
 * F = -tension * (pos - target) - friction * vel
 * acc = F
 * vel = vel + acc * dt
 * pos = pos + vel * dt
 */
export function stepSpring(
	pos: number,
	target: number,
	vel: number,
	tension: number = 180,
	friction: number = 12,
	dt: number = 1 / 60
): [number, number] {
	const clampedDt = Math.min(dt, 0.05); // prevent explosion on tab freeze
	const force = -tension * (pos - target) - friction * vel;
	const nextVel = vel + force * clampedDt;
	const nextPos = pos + nextVel * clampedDt;
	return [nextPos, nextVel];
}

/**
 * Clamps and scales gaze offset coordinates to an elliptic maximum radius.
 */
export function dampGaze(x: number, y: number, maxRadius: number = 1.0): [number, number] {
	const dist = Math.hypot(x, y);
	if (dist <= maxRadius || dist === 0) {
		return [x, y];
	}
	const scale = maxRadius / dist;
	return [x * scale, y * scale];
}
