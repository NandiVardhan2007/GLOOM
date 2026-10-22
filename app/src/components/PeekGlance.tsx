import React, { useEffect, useState } from "react";
import { motion } from "motion/react";
import { MediaState, SystemTelemetry, TimerState } from "../types";
import { ISLAND, ISLAND_COLOR, ISLAND_TYPE, px } from "../design/island";
import { MOTION_PHYSICS } from "../motion/physics";
import { Equalizer } from "./CollapsedPill";
import {
  BackwardIcon,
  BoltIcon,
  CheckIcon,
  CloseIcon,
  DownloadDocIcon,
  ForwardIcon,
  LaptopIcon,
  PauseIcon,
  PlayIcon,
  TimerIcon,
  UploadDocIcon,
} from "./IslandIcons";

export type ActivityKind = "media" | "timer" | "transfer" | "notification" | "continuity";

export interface TransferActivity {
  fileName: string;
  fileSizeMb: number;
  progress: number;
  speedMbps: string;
  isUpload: boolean;
  status: "transferring" | "completed" | "failed";
}

export interface NotificationActivity {
  appName: string;
  title: string;
  text: string;
  avatar?: string | null;
  otp?: string | null;
}

interface PeekGlanceProps {
  kind: ActivityKind;
  media: MediaState;
  telemetry: SystemTelemetry;
  timer?: TimerState | null;
  transfer?: TransferActivity | null;
  notification?: NotificationActivity | null;
  scale?: number;
  reducedMotion?: boolean;
  onPlayPause?: () => void;
  onNext?: () => void;
  onPrev?: () => void;
  onSeek?: (ms: number) => void;
  onOpen?: () => void;
  onClose?: () => void;
}

/**
 * Apple's expanded presentation: 371 x 84-160, continuous 44pt corners.
 * Content begins below the TrueDepth camera band, exactly like a Live
 * Activity, so the camera never covers text.
 */
export const PeekGlance: React.FC<PeekGlanceProps> = ({
  kind,
  media,
  telemetry,
  timer,
  transfer,
  notification,
  scale = 1,
  reducedMotion = false,
  onPlayPause,
  onNext,
  onPrev,
  onSeek,
  onOpen,
  onClose,
}) => {
  const [copiedOtp, setCopiedOtp] = useState(false);
  useEffect(() => {
    setCopiedOtp(false);
  }, [notification?.otp]);
  const margin = px(ISLAND.expanded.margin, scale);
  const topInset = px(kind === "notification" ? 28 : kind === "continuity" ? 30 : 34, scale);
  const artwork = px(ISLAND.expanded.artwork, scale);
  const t = reducedMotion ? MOTION_PHYSICS.reducedMotion : MOTION_PHYSICS.fade;

  const reveal = (delay = 0) => ({
    initial: reducedMotion ? { opacity: 0 } : { opacity: 0, y: px(4, scale) },
    animate: { opacity: 1, y: 0 },
    transition: { ...t, delay: reducedMotion ? 0 : delay },
  });

  const closeButton = onClose ? (
    <motion.button
      type="button"
      aria-label="Close activity"
      onClick={(e) => {
        e.stopPropagation();
        onClose();
      }}
      whileTap={{ scale: 0.9 }}
      style={{
        width: px(24, scale),
        height: px(24, scale),
        borderRadius: "50%",
        border: "none",
        background: ISLAND_COLOR.fill,
        color: ISLAND_COLOR.secondary,
        display: "grid",
        placeItems: "center",
        cursor: "pointer",
        padding: 0,
        flex: "none",
      }}
    >
      <CloseIcon size={px(11, scale)} color={ISLAND_COLOR.secondary} />
    </motion.button>
  ) : null;

  const content = (() => {
    if (kind === "media") {
      return (
        <>
          <motion.div
            {...reveal(0)}
            style={{ display: "flex", alignItems: "center", gap: px(12, scale), minWidth: 0 }}
          >
            {media.thumbnail ? (
              <img
                src={media.thumbnail}
                alt=""
                style={{
                  width: artwork,
                  height: artwork,
                  borderRadius: px(10, scale),
                  objectFit: "cover",
                  boxShadow: "0 4px 14px rgba(0,0,0,0.45)",
                  flex: "none",
                }}
              />
            ) : (
              <div
                style={{
                  width: artwork,
                  height: artwork,
                  borderRadius: px(10, scale),
                  background: `linear-gradient(160deg, ${ISLAND_COLOR.pink} 0%, #a80f3a 100%)`,
                  display: "grid",
                  placeItems: "center",
                  flex: "none",
                }}
              >
                <PlayIcon size={px(17, scale)} color="#fff" />
              </div>
            )}
            <div style={{ minWidth: 0, flex: 1 }}>
              <div
                style={{
                  ...ISLAND_TYPE.expandedTitle,
                  fontSize: 15 * scale,
                  color: ISLAND_COLOR.label,
                  whiteSpace: "nowrap",
                  overflow: "hidden",
                  textOverflow: "ellipsis",
                }}
              >
                {media.title || "Nothing Playing"}
              </div>
              <div
                style={{
                  ...ISLAND_TYPE.expandedSubtitle,
                  fontSize: 13 * scale,
                  color: ISLAND_COLOR.secondary,
                  whiteSpace: "nowrap",
                  overflow: "hidden",
                  textOverflow: "ellipsis",
                  marginTop: px(1, scale),
                }}
              >
                {media.artist || "Start playback in any app"}
              </div>
            </div>
            <div style={{ display: "flex", alignItems: "center", gap: px(8, scale), flex: "none" }}>
              <Equalizer
                height={px(15, scale)}
                color={media.playing ? ISLAND_COLOR.green : ISLAND_COLOR.tertiary}
                reducedMotion={reducedMotion}
              />
              {closeButton}
            </div>
          </motion.div>

          <motion.div {...reveal(0.04)} style={{ marginTop: px(6, scale) }}>
            <SeekBar media={media} scale={scale} onSeek={onSeek} />
          </motion.div>

          <motion.div
            {...reveal(0.08)}
            style={{
              marginTop: px(4, scale),
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              gap: px(26, scale),
            }}
          >
            <TransportButton label="Previous track" onClick={onPrev} scale={scale}>
              <BackwardIcon size={px(19, scale)} color={ISLAND_COLOR.label} />
            </TransportButton>
            <motion.button
              type="button"
              aria-label={media.playing ? "Pause" : "Play"}
              onClick={(e) => {
                e.stopPropagation();
                onPlayPause?.();
              }}
              whileTap={{ scale: 0.92 }}
              transition={MOTION_PHYSICS.momentum}
              style={{
                width: px(40, scale),
                height: px(40, scale),
                borderRadius: "50%",
                border: "none",
                background: "#ffffff",
                color: "#000000",
                display: "grid",
                placeItems: "center",
                cursor: "pointer",
                padding: 0,
                flex: "none",
              }}
            >
              {media.playing ? (
                <PauseIcon size={px(18, scale)} color="#000" />
              ) : (
                <PlayIcon size={px(18, scale)} color="#000" style={{ marginLeft: px(1.5, scale) }} />
              )}
            </motion.button>
            <TransportButton label="Next track" onClick={onNext} scale={scale}>
              <ForwardIcon size={px(19, scale)} color={ISLAND_COLOR.label} />
            </TransportButton>
          </motion.div>
        </>
      );
    }

    if (kind === "timer") {
      const progress =
        timer && timer.totalSeconds > 0
          ? Math.min(1, Math.max(0, timer.remainingSeconds / timer.totalSeconds))
          : 0;
      return (
        <>
          <motion.div
            {...reveal(0)}
            style={{ display: "flex", alignItems: "center", gap: px(12, scale) }}
          >
            <div
              style={{
                width: artwork,
                height: artwork,
                borderRadius: px(10, scale),
                background: "rgba(255, 159, 10, 0.18)",
                display: "grid",
                placeItems: "center",
                flex: "none",
              }}
            >
              <TimerIcon size={px(24, scale)} color={ISLAND_COLOR.orange} strokeWidth={2} />
            </div>
            <div style={{ flex: 1, minWidth: 0 }}>
              <div
                style={{
                  ...ISLAND_TYPE.expandedTitle,
                  fontSize: 15 * scale,
                  color: ISLAND_COLOR.label,
                }}
              >
                {timer?.mode === "stopwatch" ? "Stopwatch" : "Focus Timer"}
              </div>
              <div
                style={{
                  ...ISLAND_TYPE.expandedSubtitle,
                  fontSize: 13 * scale,
                  color: ISLAND_COLOR.secondary,
                }}
              >
                {timer?.isRunning ? "Running" : "Paused"}
              </div>
            </div>
            {closeButton}
          </motion.div>

          <motion.div
            {...reveal(0.04)}
            style={{
              marginTop: px(10, scale),
              display: "flex",
              alignItems: "baseline",
              justifyContent: "space-between",
            }}
          >
            <span
              style={{
                fontSize: 38 * scale,
                lineHeight: 1.05,
                fontWeight: 600,
                letterSpacing: `${-0.8 * scale}px`,
                fontVariantNumeric: "tabular-nums",
                color: ISLAND_COLOR.orange,
              }}
            >
              {timer?.formattedTime ?? "0:00"}
            </span>
            <span
              style={{
                ...ISLAND_TYPE.expandedTime,
                fontSize: 11 * scale,
                color: ISLAND_COLOR.tertiary,
              }}
            >
              {Math.round(progress * 100)}%
            </span>
          </motion.div>

          <motion.div {...reveal(0.08)} style={{ marginTop: px(12, scale) }}>
            <div
              style={{
                height: px(5, scale),
                borderRadius: 999,
                background: "rgba(255,255,255,0.14)",
                overflow: "hidden",
              }}
            >
              <div
                style={{
                  width: `${progress * 100}%`,
                  height: "100%",
                  borderRadius: 999,
                  background: ISLAND_COLOR.orange,
                  transition: reducedMotion ? "none" : "width 0.3s linear",
                }}
              />
            </div>
          </motion.div>
        </>
      );
    }

    if (kind === "transfer") {
      const progress = Math.min(1, Math.max(0, transfer?.progress ?? 0));
      const done = transfer?.status === "completed" || progress >= 1;
      return (
        <>
          <motion.div
            {...reveal(0)}
            style={{ display: "flex", alignItems: "center", gap: px(12, scale) }}
          >
            <div
              style={{
                width: artwork,
                height: artwork,
                borderRadius: px(10, scale),
                background: done ? "rgba(48, 209, 88, 0.18)" : "rgba(10, 132, 255, 0.18)",
                display: "grid",
                placeItems: "center",
                flex: "none",
              }}
            >
              {done ? (
                <CheckIcon size={px(22, scale)} color={ISLAND_COLOR.green} strokeWidth={2.2} />
              ) : transfer?.isUpload ? (
                <UploadDocIcon size={px(22, scale)} color={ISLAND_COLOR.blue} />
              ) : (
                <DownloadDocIcon size={px(22, scale)} color={ISLAND_COLOR.blue} />
              )}
            </div>
            <div style={{ flex: 1, minWidth: 0 }}>
              <div
                style={{
                  ...ISLAND_TYPE.expandedTitle,
                  fontSize: 15 * scale,
                  color: ISLAND_COLOR.label,
                  whiteSpace: "nowrap",
                  overflow: "hidden",
                  textOverflow: "ellipsis",
                }}
              >
                {transfer?.fileName ?? "Transfer"}
              </div>
              <div
                style={{
                  ...ISLAND_TYPE.expandedSubtitle,
                  fontSize: 13 * scale,
                  color: ISLAND_COLOR.secondary,
                }}
              >
                {done
                  ? "Transfer complete"
                  : `${transfer?.isUpload ? "Sending to" : "Receiving from"} JARVIS PC`}
              </div>
            </div>
            <div style={{ display: "flex", alignItems: "center", gap: px(8, scale), flex: "none" }}>
              <span
                style={{
                  ...ISLAND_TYPE.expandedTime,
                  fontSize: 11 * scale,
                  color: done ? ISLAND_COLOR.green : ISLAND_COLOR.secondary,
                  background: ISLAND_COLOR.fill,
                  borderRadius: px(6, scale),
                  padding: `${px(4, scale)}px ${px(8, scale)}px`,
                }}
              >
                {done ? "Complete" : transfer?.speedMbps ?? ""}
              </span>
              {closeButton}
            </div>
          </motion.div>

          <motion.div {...reveal(0.04)} style={{ marginTop: px(14, scale) }}>
            <div
              style={{
                height: px(6, scale),
                borderRadius: 999,
                background: "rgba(255,255,255,0.14)",
                overflow: "hidden",
              }}
            >
              <div
                style={{
                  width: `${Math.max(2, progress * 100)}%`,
                  height: "100%",
                  borderRadius: 999,
                  background: done ? ISLAND_COLOR.green : ISLAND_COLOR.blue,
                  transition: reducedMotion ? "none" : "width 0.24s ease-out",
                }}
              />
            </div>
            <div
              style={{
                marginTop: px(7, scale),
                display: "flex",
                justifyContent: "space-between",
                ...ISLAND_TYPE.expandedTime,
                fontSize: 11 * scale,
                color: ISLAND_COLOR.tertiary,
              }}
            >
              <span>
                {((transfer?.fileSizeMb ?? 0) * progress).toFixed(1)} MB of{" "}
                {(transfer?.fileSizeMb ?? 0).toFixed(1)} MB
              </span>
              <span style={{ color: done ? ISLAND_COLOR.green : ISLAND_COLOR.secondary }}>
                {Math.round(progress * 100)}%
              </span>
            </div>
          </motion.div>
        </>
      );
    }

    if (kind === "notification") {
      return (
        <>
          <motion.div
            {...reveal(0)}
            style={{ display: "flex", alignItems: "center", gap: px(12, scale) }}
          >
            {notification?.avatar ? (
              <img
                src={notification.avatar}
                alt=""
                style={{
                  width: artwork,
                  height: artwork,
                  borderRadius: "50%",
                  objectFit: "cover",
                  border: "0.5px solid rgba(255,255,255,0.18)",
                  flex: "none",
                }}
              />
            ) : (
              <div
                style={{
                  width: artwork,
                  height: artwork,
                  borderRadius: "50%",
                  background: "rgba(255,255,255,0.12)",
                  display: "grid",
                  placeItems: "center",
                  flex: "none",
                }}
              >
                <BoltIcon size={px(20, scale)} color={ISLAND_COLOR.blue} />
              </div>
            )}
            <div style={{ flex: 1, minWidth: 0 }}>
              <div
                style={{
                  ...ISLAND_TYPE.caption,
                  fontSize: 11 * scale,
                  color: ISLAND_COLOR.secondary,
                  textTransform: "uppercase",
                }}
              >
                {notification?.appName || "Notification"}
              </div>
              <div
                style={{
                  ...ISLAND_TYPE.expandedTitle,
                  fontSize: 15 * scale,
                  color: ISLAND_COLOR.label,
                  whiteSpace: "nowrap",
                  overflow: "hidden",
                  textOverflow: "ellipsis",
                  marginTop: px(1, scale),
                }}
              >
                {notification?.title || "New message"}
              </div>
            </div>
            {closeButton}
          </motion.div>

          {notification?.otp ? (
            <motion.div
              {...reveal(0.04)}
              style={{
                marginTop: px(8, scale),
                display: "flex",
                alignItems: "center",
                gap: px(10, scale),
                alignSelf: "flex-start",
                padding: `${px(5, scale)}px ${px(10, scale)}px`,
                borderRadius: px(8, scale),
                background: ISLAND_COLOR.fill,
              }}
            >
              <span
                style={{
                  ...ISLAND_TYPE.expandedTitle,
                  fontSize: 15 * scale,
                  letterSpacing: `${3 * scale}px`,
                  fontVariantNumeric: "tabular-nums",
                  color: ISLAND_COLOR.label,
                }}
              >
                {notification.otp}
              </span>
              <span style={{ fontSize: 11 * scale, color: ISLAND_COLOR.blue, fontWeight: 600 }}>
                {copiedOtp ? "Copied" : "Copy"}
              </span>
            </motion.div>
          ) : (
            <motion.div
              {...reveal(0.04)}
              style={{
                marginTop: px(8, scale),
                ...ISLAND_TYPE.expandedSubtitle,
                fontSize: 13 * scale,
                lineHeight: `${16 * scale}px`,
                color: ISLAND_COLOR.secondary,
                display: "-webkit-box",
                WebkitLineClamp: 2,
                WebkitBoxOrient: "vertical",
                overflow: "hidden",
              }}
            >
              {notification?.text || ""}
            </motion.div>
          )}

          <motion.div
            {...reveal(0.08)}
            style={{ marginTop: px(8, scale), display: "flex", gap: px(8, scale) }}
          >
            <button
              type="button"
              onClick={(e) => {
                e.stopPropagation();
                if (notification?.otp) {
                  navigator.clipboard?.writeText(notification.otp).then(
                    () => setCopiedOtp(true),
                    () => setCopiedOtp(false)
                  );
                  return;
                }
                onOpen?.();
              }}
              style={{
                flex: 1,
                height: px(32, scale),
                borderRadius: px(10, scale),
                border: "none",
                background: ISLAND_COLOR.blue,
                color: "#fff",
                fontFamily: "var(--font-sans)",
                fontSize: 13 * scale,
                fontWeight: 600,
                letterSpacing: "-0.08px",
                cursor: "pointer",
              }}
            >
              {notification?.otp ? (copiedOtp ? "Copied" : "Copy Code") : "Open App"}
            </button>
            <button
              type="button"
              onClick={(e) => {
                e.stopPropagation();
                if (notification?.otp) {
                  onOpen?.();
                  return;
                }
                onClose?.();
              }}
              style={{
                flex: 1,
                height: px(32, scale),
                borderRadius: px(10, scale),
                border: "none",
                background: ISLAND_COLOR.fill,
                color: ISLAND_COLOR.label,
                fontFamily: "var(--font-sans)",
                fontSize: 13 * scale,
                fontWeight: 600,
                letterSpacing: "-0.08px",
                cursor: "pointer",
              }}
            >
              {notification?.otp ? "Open App" : "Dismiss"}
            </button>
          </motion.div>
        </>
      );
    }

    // Continuity / idle app card.
    return (
      <>
        <motion.div
          {...reveal(0)}
          style={{ display: "flex", alignItems: "center", gap: px(12, scale) }}
        >
          <div
            style={{
              width: artwork,
              height: artwork,
              borderRadius: px(10, scale),
              background: ISLAND_COLOR.fill,
              display: "grid",
              placeItems: "center",
              flex: "none",
            }}
          >
            <LaptopIcon size={px(22, scale)} color={ISLAND_COLOR.label} />
          </div>
          <div style={{ flex: 1, minWidth: 0 }}>
            <div
              style={{
                ...ISLAND_TYPE.expandedTitle,
                fontSize: 15 * scale,
                color: ISLAND_COLOR.label,
              }}
            >
              JARVIS Ecosystem
            </div>
            <div
              style={{
                ...ISLAND_TYPE.expandedSubtitle,
                fontSize: 13 * scale,
                color: ISLAND_COLOR.secondary,
              }}
            >
              {telemetry.isCharging ? "Charging" : "Live Continuity Active"}
            </div>
          </div>
          {closeButton}
        </motion.div>

        <motion.div
          {...reveal(0.04)}
          style={{
            marginTop: px(8, scale),
            height: px(30, scale),
            borderRadius: px(9, scale),
            background: "rgba(255,255,255,0.06)",
            display: "flex",
            alignItems: "center",
            justifyContent: "space-between",
            padding: `0 ${px(11, scale)}px`,
          }}
        >
          <span style={{ display: "flex", alignItems: "center", gap: px(7, scale) }}>
            <span
              style={{
                width: px(6, scale),
                height: px(6, scale),
                borderRadius: "50%",
                background: telemetry.isCharging ? ISLAND_COLOR.green : ISLAND_COLOR.blue,
              }}
            />
            <span
              style={{
                ...ISLAND_TYPE.expandedSubtitle,
                fontSize: 12 * scale,
                color: ISLAND_COLOR.secondary,
              }}
            >
              {telemetry.isCharging ? `Charging · ${telemetry.batteryPercent ?? 0}%` : "High-Speed Link"}
            </span>
          </span>
          <span
            style={{
              ...ISLAND_TYPE.expandedTime,
              fontSize: 11 * scale,
              color: ISLAND_COLOR.tertiary,
            }}
          >
            {telemetry.cpuPercent ? `CPU ${Math.round(telemetry.cpuPercent)}%` : "Ready"}
          </span>
        </motion.div>

        <motion.div {...reveal(0.08)} style={{ marginTop: px(8, scale), display: "flex", gap: px(8, scale) }}>
          <ActionPill label="Open" scale={scale} onClick={onOpen} accent />
          <ActionPill label="Dismiss" scale={scale} onClick={onClose} />
        </motion.div>
      </>
    );
  })();

  return (
    <div
      style={{
        width: "100%",
        height: "100%",
        padding: `${topInset}px ${margin}px ${px(8, scale)}px`,
        boxSizing: "border-box",
        display: "flex",
        flexDirection: "column",
        justifyContent: "flex-start",
        fontFamily: "var(--font-sans)",
        overflow: "hidden",
      }}
    >
      {content}
    </div>
  );
};

const SeekBar: React.FC<{
  media: MediaState;
  scale: number;
  onSeek?: (ms: number) => void;
}> = ({ media, scale, onSeek }) => {
  const duration = media.durationMs || 1;
  const progress = Math.min(1, Math.max(0, media.positionMs / duration));
  const format = (ms: number) => {
    const total = Math.max(0, Math.floor(ms / 1000));
    return `${Math.floor(total / 60)}:${String(total % 60).padStart(2, "0")}`;
  };
  return (
    <div>
      <div
        role="slider"
        aria-label="Playback position"
        aria-valuemin={0}
        aria-valuemax={Math.round(duration / 1000)}
        aria-valuenow={Math.round(media.positionMs / 1000)}
        onClick={(e) => {
          e.stopPropagation();
          const rect = e.currentTarget.getBoundingClientRect();
          onSeek?.(Math.round(((e.clientX - rect.left) / rect.width) * duration));
        }}
        style={{
          height: px(10, scale),
          display: "flex",
          alignItems: "center",
          cursor: "pointer",
        }}
      >
        <div
          style={{
            width: "100%",
            height: px(4, scale),
            borderRadius: 999,
            background: "rgba(255,255,255,0.16)",
            position: "relative",
            overflow: "hidden",
          }}
        >
          <div
            style={{
              width: `${progress * 100}%`,
              height: "100%",
              borderRadius: 999,
              background: "#ffffff",
            }}
          />
        </div>
      </div>
      <div
        style={{
          display: "flex",
          justifyContent: "space-between",
          ...ISLAND_TYPE.expandedTime,
          fontSize: 11 * scale,
          color: ISLAND_COLOR.tertiary,
          marginTop: px(1, scale),
        }}
      >
        <span>{format(media.positionMs)}</span>
        <span>-{format(Math.max(0, duration - media.positionMs))}</span>
      </div>
    </div>
  );
};

const TransportButton: React.FC<{
  label: string;
  onClick?: () => void;
  scale: number;
  children: React.ReactNode;
}> = ({ label, onClick, scale, children }) => (
  <motion.button
    type="button"
    aria-label={label}
    onClick={(e) => {
      e.stopPropagation();
      onClick?.();
    }}
    whileTap={{ scale: 0.9 }}
    transition={MOTION_PHYSICS.momentum}
    style={{
      width: px(36, scale),
      height: px(36, scale),
      borderRadius: "50%",
      border: "none",
      background: "transparent",
      display: "grid",
      placeItems: "center",
      cursor: "pointer",
      padding: 0,
      flex: "none",
      color: ISLAND_COLOR.label,
    }}
  >
    {children}
  </motion.button>
);

const ActionPill: React.FC<{
  label?: string;
  scale: number;
  onClick?: () => void;
  accent?: boolean;
}> = ({ label, scale, onClick, accent }) => (
  <button
    type="button"
    onClick={(e) => {
      e.stopPropagation();
      onClick?.();
    }}
    style={{
      flex: 1,
      height: px(32, scale),
      borderRadius: px(10, scale),
      border: "none",
      background: accent ? ISLAND_COLOR.blue : ISLAND_COLOR.fill,
      color: accent ? "#ffffff" : ISLAND_COLOR.label,
      fontFamily: "var(--font-sans)",
      fontSize: 13 * scale,
      fontWeight: 600,
      letterSpacing: "-0.08px",
      cursor: "pointer",
    }}
  >
    {label}
  </button>
);
