import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { stepSpring, dampGaze } from "../src/components/mascot/springMath.ts";

describe("Mascot spring physics and gaze damping", () => {
	it("stepSpring converges towards target with overshoot and settling", () => {
		let pos = 0;
		let vel = 0;
		const target = 1.0;
		const tension = 180;
		const friction = 12;
		const dt = 1 / 60;

		for (let i = 0; i < 90; i++) {
			const res = stepSpring(pos, target, vel, tension, friction, dt);
			pos = res[0];
			vel = res[1];
		}

		assert(Math.abs(pos - target) < 0.05, `Spring did not settle, pos=${pos}`);
		assert(Math.abs(vel) < 0.1, `Velocity did not settle, vel=${vel}`);
	});

	it("dampGaze clamps gaze coordinates to safe eye bounds", () => {
		assert.deepEqual(dampGaze(0, 0, 10), [0, 0]);
		const [x, y] = dampGaze(50, 50, 10);
		assert(Math.abs(x) <= 10.001);
		assert(Math.abs(y) <= 10.001);
	});
});
