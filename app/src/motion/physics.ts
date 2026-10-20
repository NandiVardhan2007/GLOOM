export const MOTION_PHYSICS = {
  // Ultra-fast, snappy Apple/HarmonyOS fluid spring morph (~160ms)
  morph: {
    type: "spring" as const,
    stiffness: 460,
    damping: 34,
    mass: 0.6,
    restDelta: 0.5,
    restSpeed: 0.5,
  },
  // High-speed bezier curve alternative
  fluidCurve: {
    duration: 0.18,
    ease: [0.22, 1, 0.36, 1] as const,
  },
  // Micro-swell when hovered in closed state
  peek: {
    type: "spring" as const,
    stiffness: 500,
    damping: 26,
    mass: 0.5,
  },
  // Pop alert notifications (volume change, battery alert)
  alertPop: {
    type: "spring" as const,
    stiffness: 420,
    damping: 26,
    mass: 0.55,
  },
  // Snappy tab switches inside expanded panel
  tabSwitch: {
    type: "spring" as const,
    stiffness: 480,
    damping: 30,
    mass: 0.55,
  },
  // Content crossfade - ultra-rapid concurrent transition
  fade: {
    duration: 0.10,
    ease: [0.25, 0.1, 0.25, 1] as const,
  },
  // Reduced motion alternative
  reducedMotion: {
    duration: 0.08,
    ease: "linear" as const,
  },
};
