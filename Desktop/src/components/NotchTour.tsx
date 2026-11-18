import { FC } from "react";
import "./NotchTour.css";

export interface TourStepDef {
  key: string;
  title: string;
  text: string;
  signal?: "panel" | "slider" | "cc" | "shelf" | "dock" | "mode";
  need?: number;
  actionText?: string;
}

export const TOUR_STEPS: TourStepDef[] = [
  {
    key: "welcome",
    title: "Welcome to GLOOM",
    text: "This is your standalone Dynamic Island. Hover or click to expand it. This interactive tour walks through everything GLOOM can do. Your volume, brightness, and settings are preserved.",
  },
  {
    key: "panels",
    title: "Fluid Multi-Card Navigation",
    text: "Use the top segmented tabs or scroll your mouse wheel over the Dynamic Island to seamlessly glide between Now Playing, Focus, Controls, Productivity, and System Monitor.",
    signal: "panel",
    need: 2,
  },
  {
    key: "sliders",
    title: "Direct Manipulation Sliders",
    text: "In the Control Center or Now Playing view, drag the volume or brightness slider with 1:1 pointer tracking, or scroll your mouse wheel over the island to nudge levels instantly.",
    signal: "slider",
    need: 1,
  },
  {
    key: "controlCenter",
    title: "iOS-Style Control Center",
    text: "Manage Wi-Fi networks, Bluetooth peripherals, audio output devices, and system quick actions with single-tap frosted glass tiles.",
    signal: "cc",
    need: 1,
    actionText: "Open Controls",
  },
  {
    key: "shelf",
    title: "Unified Productivity Drawer",
    text: "Drop files onto the island to stash them, copy text from your real-time clipboard history with 1 click, or jot persistent notes in the auto-saving Scratchpad.",
    signal: "shelf",
    need: 1,
  },
  {
    key: "dock",
    title: "Fluid Magnifying Dock",
    text: "Windows taskbar replacement with continuous Apple spring magnification, window previews on hover, unread attention dots, and launch bounce.",
    signal: "dock",
    need: 1,
  },
  {
    key: "modes",
    title: "Fixed, Smart, or Peek Modes",
    text: "Choose Fixed to reserve space, Smart to auto-hide when overlapping windows, or Peek to tuck away until you reach the screen edge. Try cycling modes in Control Center.",
    signal: "mode",
    need: 1,
  },
  {
    key: "customize",
    title: "Make It Yours",
    text: "Personalize your experience with Liquid Glass, Frosted Glass, Solid OLED, or Material You styles, dynamic accents, and layout preferences in Settings.",
    actionText: "Open Settings",
  },
];

type ActionId = "control-center" | "settings";

interface Props {
  step: number;
  done: boolean;
  dockEnabled: boolean;
  onStep: (next: number) => void;
  onClose: () => void;
  onAction: (id: ActionId) => void;
}

const ACTIONS: Record<string, ActionId> = {
  controlCenter: "control-center",
  customize: "settings",
};

export const NotchTour: FC<Props> = ({
  step,
  done,
  dockEnabled,
  onStep,
  onClose,
  onAction,
}) => {
  const current = TOUR_STEPS[step] || TOUR_STEPS[0];
  const last = step === TOUR_STEPS.length - 1;
  const action = ACTIONS[current.key];
  const tryIt = !!current.signal && (current.key !== "dock" || dockEnabled);

  return (
    <div className="tour-bar" onClick={(e) => e.stopPropagation()}>
      <div className="tour-head">
        <span className="tour-count">
          {step + 1}/{TOUR_STEPS.length}
        </span>
        <span className="tour-title">{current.title}</span>
        <button className="tour-skip" onClick={onClose} title="Skip tour">
          Skip
        </button>
      </div>
      <p className="tour-text">{current.text}</p>
      <div className="tour-foot">
        <span className="tour-dots">
          {TOUR_STEPS.map((_, i) => (
            <i key={i} className={i === step ? "on" : ""} />
          ))}
        </span>
        {action && (
          <button className="tour-btn ghost" onClick={() => onAction(action)}>
            {current.actionText || "Try it"}
          </button>
        )}
        {step > 0 && (
          <button className="tour-btn ghost" onClick={() => onStep(step - 1)}>
            Back
          </button>
        )}
        <button
          className={`tour-btn${tryIt ? (done ? " ready" : " waiting") : ""}`}
          disabled={tryIt && !done}
          onClick={() => (last ? onClose() : onStep(step + 1))}
        >
          {last ? "Get Started" : tryIt && !done ? "Try it..." : "Next"}
        </button>
      </div>
    </div>
  );
};
