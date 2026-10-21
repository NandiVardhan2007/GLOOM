import React from "react";
import { motion } from "motion/react";
import { MediaState, SystemTelemetry, TimerState } from "../types";
import { ISLAND, ISLAND_COLOR, ISLAND_TYPE, px } from "../design/island";
import { MOTION_PHYSICS } from "../motion/physics";
import {
  BellIcon,
  BoltIcon,
  DownloadDocIcon,
  MusicNoteIcon,
  TimerIcon,
  UploadDocIcon,
  WaveformIcon,
} from "./IslandIcons";

export type CompactKind =
  | "media"
  | "timer"
  | "transfer"
  | "notification"
  | "charging"
  | "drop"
  | "idle";

interface CollapsedPillProps {
  kind: CompactKind;
  media: MediaState;
  telemetry: SystemTelemetry;
  timer?: TimerState | null;
  transfer?: { fileName: string; progress: number; isUpload: boolean } | null;
  notification?: { appName: string; title: string; avatar?: string | null } | null;
  scale?: number;
  reducedMotion?: boolean;
  onClick?: () => void;
}

/**
 * Apple's compact presentation: a leading element and a trailing element on
 * either side of the TrueDepth camera. Content hugs the camera with no gap
 * between the two, and 11pt of inset from the outer rounded ends.
 */
export const CollapsedPill: React.FC<CollapsedPillProps> = ({
  kind,
  media,
  telemetry,
  timer,
  transfer,
  notification,
  scale = 1,
  reducedMotion = false,
}) => {
  const height = px(ISLAND.compact.height, scale);
  const wing = px(ISLAND.compact.wing, scale);
  const camera = px(ISLAND.compact.camera, scale);
  const artworkSize = px(ISLAND.compact.artwork, scale);
  const inset = px(11, scale);
  const artworkRadius = px(6.5, scale);

  const spring = reducedMotion ? MOTION_PHYSICS.reducedMotion : MOTION_PHYSICS.morph;

  const leading = (() => {
    switch (kind) {
      case "media":
        return media.thumbnail ? (
          <motion.img
            key={media.thumbnail}
            src={media.thumbnail}
            alt=""
            initial={reducedMotion ? false : { scale: 0.8, opacity: 0 }}
            animate={{ scale: 1, opacity: 1 }}
            transition={spring}
            style={{
              width: artworkSize,
              height: artworkSize,
              borderRadius: artworkRadius,
              objectFit: "cover",
              display: "block",
              boxShadow: "0 1px 3px rgba(0,0,0,0.55)",
            }}
          />
        ) : (
          <div
            style={{
              width: artworkSize,
              height: artworkSize,
              borderRadius: artworkRadius,
              background: `linear-gradient(160deg, ${ISLAND_COLOR.pink} 0%, #b3123f 100%)`,
              display: "grid",
              placeItems: "center",
            }}
          >
            <MusicNoteIcon size={artworkSize * 0.58} color="#ffffff" />
          </div>
        );
      case "timer":
        return (
          <div
            style={{
              width: artworkSize,
              height: artworkSize,
              borderRadius: "50%",
              display: "grid",
              placeItems: "center",
              background: "rgba(255, 159, 10, 0.18)",
            }}
          >
            <TimerIcon size={artworkSize * 0.62} color={ISLAND_COLOR.orange} strokeWidth={2} />
          </div>
        );
      case "transfer":
        return (
          <div
            style={{
              width: artworkSize,
              height: artworkSize,
              borderRadius: "50%",
              display: "grid",
              placeItems: "center",
              background: "rgba(10, 132, 255, 0.18)",
            }}
          >
            {transfer?.isUpload ? (
              <UploadDocIcon size={artworkSize * 0.6} color={ISLAND_COLOR.blue} />
            ) : (
              <DownloadDocIcon size={artworkSize * 0.6} color={ISLAND_COLOR.blue} />
            )}
          </div>
        );
      case "notification":
        return notification?.avatar ? (
          <img
            src={notification.avatar}
            alt=""
            style={{
              width: artworkSize,
              height: artworkSize,
              borderRadius: "50%",
              objectFit: "cover",
              border: "0.5px solid rgba(255,255,255,0.18)",
            }}
          />
        ) : (
          <div
            style={{
              width: artworkSize,
              height: artworkSize,
              borderRadius: "50%",
              display: "grid",
              placeItems: "center",
              background: "rgba(255, 255, 255, 0.12)",
            }}
          >
            <BellIcon size={artworkSize * 0.58} color="#ffffff" strokeWidth={2} />
          </div>
        );
      case "charging":
        return <BoltIcon size={artworkSize * 0.82} color={ISLAND_COLOR.green} />;
      case "drop":
        return (
          <div
            style={{
              width: artworkSize,
              height: artworkSize,
              borderRadius: "50%",
              display: "grid",
              placeItems: "center",
              background: "rgba(10, 132, 255, 0.18)",
            }}
          >
            <UploadDocIcon size={artworkSize * 0.6} color={ISLAND_COLOR.blue} />
          </div>
        );
      default:
        return null;
    }
  })();

  const trailing = (() => {
    switch (kind) {
      case "media":
        return media.playing ? (
          <Equalizer height={px(13, scale)} color={ISLAND_COLOR.green} reducedMotion={reducedMotion} />
        ) : (
          <WaveformIcon size={px(15, scale)} color={ISLAND_COLOR.secondary} strokeWidth={2} />
        );
      case "timer":
        return (
          <span style={{ ...ISLAND_TYPE.compactNumber, color: ISLAND_COLOR.orange, fontSize: 13 * scale }}>
            {timer?.formattedTime ?? "0:00"}
          </span>
        );
      case "transfer":
        return (
          <span style={{ ...ISLAND_TYPE.compactNumber, color: ISLAND_COLOR.label, fontSize: 13 * scale }}>
            {Math.round((transfer?.progress ?? 0) * 100)}%
          </span>
        );
      case "notification":
        return (
          <span
            style={{
              width: px(6, scale),
              height: px(6, scale),
              borderRadius: "50%",
              background: ISLAND_COLOR.blue,
              display: "block",
            }}
          />
        );
      case "charging":
        return (
          <span style={{ ...ISLAND_TYPE.compactNumber, color: ISLAND_COLOR.green, fontSize: 13 * scale }}>
            {telemetry.batteryPercent ?? 0}%
          </span>
        );
      case "drop":
        return (
          <span
            style={{
              ...ISLAND_TYPE.compactNumber,
              color: ISLAND_COLOR.blue,
              fontSize: 12 * scale,
            }}
          >
            Beam
          </span>
        );
      default:
        return null;
    }
  })();

  const label = (() => {
    switch (kind) {
      case "media":
        return `${media.title || "Media"} — ${media.artist || "Now playing"}`;
      case "timer":
        return `Focus timer ${timer?.formattedTime ?? ""}`.trim();
      case "transfer":
        return `${transfer?.isUpload ? "Uploading" : "Downloading"} ${transfer?.fileName ?? ""}`.trim();
      case "notification":
        return `${notification?.appName ?? "Notification"}: ${notification?.title ?? ""}`.trim();
      case "charging":
        return `Charging, ${telemetry.batteryPercent ?? 0} percent`;
      case "drop":
        return "Drop to beam to JARVIS PC";
      default:
        return "JARVIS";
    }
  })();

  if (kind === "idle") {
    return <div role="status" aria-label="JARVIS at rest" style={{ width: "100%", height: "100%" }} />;
  }

  return (
    <div
      role="status"
      aria-label={label}
      title={label}
      style={{
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        width: "100%",
        height: height,
        fontFamily: "var(--font-sans)",
        color: ISLAND_COLOR.label,
      }}
    >
      <div
        style={{
          width: wing,
          height: "100%",
          display: "flex",
          alignItems: "center",
          justifyContent: "flex-start",
          paddingLeft: inset,
          boxSizing: "border-box",
          flex: "none",
        }}
      >
        {leading}
      </div>

      {/* TrueDepth camera band: never place content here. */}
      <div style={{ width: camera, height: "100%", flex: "none" }} aria-hidden="true" />

      <div
        style={{
          width: wing,
          height: "100%",
          display: "flex",
          alignItems: "center",
          justifyContent: "flex-end",
          paddingRight: inset,
          boxSizing: "border-box",
          flex: "none",
        }}
      >
        {trailing}
      </div>
    </div>
  );
};

/** Small 4-bar equalizer in the SF Symbols waveform spirit. */
export const Equalizer: React.FC<{
  height: number;
  color: string;
  reducedMotion?: boolean;
}> = ({ height, color, reducedMotion = false }) => {
  const bars = [0.42, 0.9, 0.62, 1];
  return (
    <div
      aria-hidden="true"
      style={{
        display: "flex",
        alignItems: "center",
        gap: Math.max(1.5, height * 0.16),
        height,
      }}
    >
      {bars.map((factor, i) => (
        <motion.span
          key={i}
          animate={
            reducedMotion
              ? { height: height * factor * 0.7 }
              : { height: [height * 0.28, height * factor, height * 0.34, height * factor * 0.82, height * 0.28] }
          }
          transition={
            reducedMotion
              ? { duration: 0.12 }
              : {
                  duration: 0.9 + i * 0.11,
                  repeat: Infinity,
                  repeatType: "mirror",
                  ease: "easeInOut",
                  delay: i * 0.07,
                }
          }
          style={{
            width: Math.max(1.6, height * 0.17),
            borderRadius: 999,
            background: color,
            display: "block",
          }}
        />
      ))}
    </div>
  );
};
