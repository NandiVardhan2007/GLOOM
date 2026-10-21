import { appleSpring } from "../design/island";

/**
 * Apple's motion model, expressed as response + damping ratio.
 *
 * Spring response is the time to reach the target, not a fixed duration;
 * a spring can always be redirected mid-flight, which is what keeps island
 * transitions interruptible. Default damping is 1.0 (critically damped);
 * bounce is reserved for gestures that carried momentum.
 */
export const MOTION_PHYSICS = {
  /** Idle -> compact and compact -> idle. */
  morph: appleSpring(0.35, 1.0),
  /** Compact -> expanded and expanded -> compact. Apple expands slightly slower. */
  expand: appleSpring(0.4, 1.0),
  /** The contract back to rest is quicker. */
  collapse: appleSpring(0.3, 1.0),
  /** Hover glances. */
  peek: appleSpring(0.33, 1.0),
  /** A flick or an activity that arrives with momentum. */
  momentum: appleSpring(0.4, 0.8),
  /** Alert arrival: a hint of life without bounce on the card itself. */
  alertPop: appleSpring(0.38, 0.86),
  /** Tab switches inside the expanded panel. */
  tabSwitch: appleSpring(0.28, 1.0),
  /** Content replacement inside a stable island size. */
  fade: {
    duration: 0.16,
    ease: [0.32, 0.72, 0, 1] as const,
  },
  /** Reduced motion: a short crossfade, never a travel animation. */
  reducedMotion: {
    duration: 0.12,
    ease: "easeOut" as const,
  },
};

/** Projected resting point for a flick (Apple's exponential decay). */
export function projectMomentum(velocity: number, decelerationRate = 0.998): number {
  return (velocity / 1000) * (decelerationRate / (1 - decelerationRate));
}

/** Progressive resistance at a boundary. */
export function rubberband(overshoot: number, dimension: number, constant = 0.55): number {
  return (overshoot * dimension * constant) / (dimension + constant * Math.abs(overshoot));
}
