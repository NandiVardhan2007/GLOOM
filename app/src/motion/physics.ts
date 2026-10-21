export const MOTION_PHYSICS = {
  // Apple's default: critically damped, no visible overshoot. Response ≈ 0.35s.
  morph: {
    type: "spring" as const,
    stiffness: 240,
    damping: 29,
    mass: 0.85,
    restDelta: 0.001,
    restSpeed: 0.001,
  },
  // Collapse uses the same critical damping so size changes never bounce.
  collapse: {
    type: "spring" as const,
    stiffness: 200,
    damping: 26,
    mass: 0.8,
    restDelta: 0.001,
    restSpeed: 0.001,
  },
  // Expand is quicker but still critically damped.
  expand: {
    type: "spring" as const,
    stiffness: 260,
    damping: 29,
    mass: 0.8,
    restDelta: 0.001,
    restSpeed: 0.001,
  },
  // High-speed bezier curve alternative
  fluidCurve: {
    duration: 0.18,
    ease: [0.22, 1, 0.36, 1] as const,
  },
  // Hover swell: quick and flat.
  peek: {
    type: "spring" as const,
    stiffness: 500,
    damping: 32,
    mass: 0.5,
  },
  // Alerts settle cleanly with only a hint of life.
  alertPop: {
    type: "spring" as const,
    stiffness: 420,
    damping: 30,
    mass: 0.55,
  },
  // Snappy tab switches inside the expanded panel.
  tabSwitch: {
    type: "spring" as const,
    stiffness: 480,
    damping: 33,
    mass: 0.55,
  },
  // Content crossfade
  fade: {
    duration: 0.14,
    ease: [0.32, 0.72, 0, 1] as const,
  },
  // Reduced motion alternative
  reducedMotion: {
    duration: 0.08,
    ease: "linear" as const,
  },
};
