/**
 * Apple Dynamic Island geometry, type and motion.
 *
 * Values are taken from the Human Interface Guidelines "Live Activities"
 * specifications (iOS 393 x 852 class) and are expressed in design points,
 * which map 1:1 to CSS pixels here. Everything scales through `islandScale`.
 *
 * Reference:
 * - island at rest            126 x 36.67
 * - compact leading/trailing  52.33 x 36.67 each
 * - camera band               125.34 x 36.67
 * - compact total             230 x 36.67
 * - expanded                  400 x 180, corner radius 36
 * - content margin            14 (Lock Screen), 16 top inside expanded
 * - artwork                   44
 */

export const ISLAND = {
  rest: { width: 126, height: 36.67 },
  compact: {
    width: 230,
    height: 36.67,
    wing: 52.33,
    camera: 125.34,
    artwork: 22,
    radius: 18.335,
  },
  minimal: { min: 36.67, max: 45, height: 36.67 },
  expanded: {
    width: 400,
    minHeight: 84,
    height: 180,
    maxHeight: 180,
    radius: 36,
    margin: 14,
    topInset: 16,
    cameraClearance: 37,
    artwork: 44,
  },
  keyline: "rgba(255, 255, 255, 0.12)",
  keylineSpecular: "rgba(255, 255, 255, 0.22)",
  surface: "#000000",
  surfaceRaised: "#1c1c1e",
  surfaceRaised2: "#2c2c2e",
} as const;

/** Apple's type ramp, tuned for the island. Tracking is size specific. */
export const ISLAND_TYPE = {
  compactTitle: {
    fontSize: 13,
    lineHeight: "16px",
    fontWeight: 600,
    letterSpacing: "-0.08px",
  },
  compactNumber: {
    fontSize: 13,
    lineHeight: "16px",
    fontWeight: 600,
    letterSpacing: "-0.08px",
    fontVariantNumeric: "tabular-nums",
  },
  expandedTitle: {
    fontSize: 15,
    lineHeight: "20px",
    fontWeight: 600,
    letterSpacing: "-0.24px",
  },
  expandedSubtitle: {
    fontSize: 13,
    lineHeight: "18px",
    fontWeight: 400,
    letterSpacing: "-0.08px",
  },
  expandedTime: {
    fontSize: 11,
    lineHeight: "13px",
    fontWeight: 500,
    letterSpacing: "0.06px",
    fontVariantNumeric: "tabular-nums",
  },
  caption: {
    fontSize: 11,
    lineHeight: "13px",
    fontWeight: 600,
    letterSpacing: "0.06px",
  },
} as const;

/** Material colors used inside the island. The island itself is always black. */
export const ISLAND_COLOR = {
  label: "#ffffff",
  secondary: "rgba(235, 235, 245, 0.60)",
  tertiary: "rgba(235, 235, 245, 0.30)",
  divider: "rgba(255, 255, 255, 0.08)",
  fill: "rgba(120, 120, 128, 0.20)",
  fillStrong: "rgba(120, 120, 128, 0.32)",
  green: "#30d158",
  blue: "#0a84ff",
  orange: "#ff9f0a",
  red: "#ff453a",
  pink: "#ff375f",
  cyan: "#64d2ff",
} as const;

export function clampScale(scale: number | undefined): number {
  if (!scale || Number.isNaN(scale)) return 1;
  return Math.min(1.6, Math.max(0.7, scale));
}

export function px(value: number, scale: number): number {
  return Math.round(value * scale * 100) / 100;
}

/**
 * Apple's springs are described by response (seconds to reach the target)
 * and damping ratio, rather than stiffness/damping.
 *
 *   omega = 2 * PI / response
 *   stiffness = omega^2 * mass
 *   damping = 2 * dampingRatio * omega * mass
 */
export function appleSpring(response: number, dampingRatio = 1, mass = 1) {
  const omega = (2 * Math.PI) / response;
  return {
    type: "spring" as const,
    stiffness: omega * omega * mass,
    damping: 2 * dampingRatio * omega * mass,
    mass,
    restDelta: 0.001,
    restSpeed: 0.001,
  };
}
