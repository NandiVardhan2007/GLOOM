import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { DOCK_CONFIG, sizeFor, xOffsetFor } from "../src/dockConfig.ts";

describe("DOCK_CONFIG tuning constants", () => {
	it("exposes all required tuning constants", () => {
		assert.equal(DOCK_CONFIG.BASE, 42);
		assert.equal(DOCK_CONFIG.MAX, 72);
		assert.equal(DOCK_CONFIG.FALLOFF, 3);
		assert.equal(DOCK_CONFIG.GAP, 12);
		assert.equal(DOCK_CONFIG.SLOT, 54); // BASE + GAP
		assert.equal(DOCK_CONFIG.SPRING.type, "spring");
		assert.ok(DOCK_CONFIG.SPRING.stiffness >= 300 && DOCK_CONFIG.SPRING.stiffness <= 500);
		assert.ok(DOCK_CONFIG.SPRING.damping >= 20 && DOCK_CONFIG.SPRING.damping <= 30);
		assert.deepEqual(DOCK_CONFIG.BOUNCE.y, [0, -28, 0, -10, 0]);
		assert.equal(DOCK_CONFIG.BOUNCE.transition.duration, 0.6);
		assert.ok(["discrete", "continuous"].includes(DOCK_CONFIG.MODE));
		assert.ok(["linear", "cosine"].includes(DOCK_CONFIG.FALLOFF_TYPE));
		assert.equal(DOCK_CONFIG.CORNER_RADIUS_RATIO, 0.22);
	});
});

describe("sizeFor calculation", () => {
	it("returns resting size BASE when no item is hovered (pos is null)", () => {
		for (let i = 0; i < 10; i++) {
			assert.equal(sizeFor(i, null), 42);
		}
	});

	it("returns MAX for hovered item (distance 0)", () => {
		assert.equal(sizeFor(2, 2), 72);
	});

	it("returns progressive symmetrical falloff sizes (roughly 62 at dist 1, 52 at dist 2, 42 at dist 3+)", () => {
		const h = 3;
		// Distance 0
		assert.equal(Math.round(sizeFor(h, h)), 72);

		// Distance 1
		const d1_left = Math.round(sizeFor(h - 1, h));
		const d1_right = Math.round(sizeFor(h + 1, h));
		assert.equal(d1_left, d1_right);
		assert.equal(d1_left, 62); // 42 + 30*(2/3) = 62

		// Distance 2
		const d2_left = Math.round(sizeFor(h - 2, h));
		const d2_right = Math.round(sizeFor(h + 2, h));
		assert.equal(d2_left, d2_right);
		assert.equal(d2_left, 52); // 42 + 30*(1/3) = 52

		// Distance 3 and beyond
		assert.equal(sizeFor(h - 3, h), 42);
		assert.equal(sizeFor(h + 3, h), 42);
		assert.equal(sizeFor(h + 4, h), 42);
	});

	it("supports continuous fractional hover positions symmetrically", () => {
		const s1 = sizeFor(1, 1.5);
		const s2 = sizeFor(2, 1.5);
		assert.equal(s1, s2); // Both at distance 0.5
		assert.equal(Math.round(s1), 67); // 42 + 30*(1 - 0.5/3) = 67
	});

	it("supports cosine falloff variant", () => {
		const cfg = { ...DOCK_CONFIG, FALLOFF_TYPE: "cosine" };
		assert.equal(Math.round(sizeFor(2, 2, cfg)), 72);
		assert.equal(Math.round(sizeFor(1, 2, cfg)), 68);
		assert.equal(Math.round(sizeFor(0, 2, cfg)), 57);
		assert.equal(sizeFor(5, 2, cfg), 42);
	});
});

describe("xOffsetFor calculation for large docks (>15 items)", () => {
	it("returns 0 for all items at rest (pos is null)", () => {
		const sizes = Array(20).fill(42);
		for (let i = 0; i < 20; i++) {
			assert.equal(Math.round(xOffsetFor(i, sizes)), 0);
		}
	});

	it("pushes left neighbors left and right neighbors right symmetrically", () => {
		const sizes = Array(20).fill(42);
		const h = 10;
		for (let i = 0; i < 20; i++) {
			sizes[i] = sizeFor(i, h);
		}
		// Center icon does not shift
		assert.equal(Math.round(xOffsetFor(h, sizes)), 0);

		// Left neighbors shift negatively (left)
		assert.ok(xOffsetFor(h - 1, sizes) < 0);
		assert.ok(xOffsetFor(h - 2, sizes) < 0);

		// Right neighbors shift positively (right)
		assert.ok(xOffsetFor(h + 1, sizes) > 0);
		assert.ok(xOffsetFor(h + 2, sizes) > 0);

		// Exact symmetry
		assert.equal(
			Math.round(xOffsetFor(h - 1, sizes)),
			-Math.round(xOffsetFor(h + 1, sizes))
		);
		assert.equal(
			Math.round(xOffsetFor(h - 2, sizes)),
			-Math.round(xOffsetFor(h + 2, sizes))
		);
	});
});
