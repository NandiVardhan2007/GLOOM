import React from "react";
import { motion } from "motion/react";

interface WaveformProps {
  isPlaying: boolean;
  color?: string;
  barCount?: number;
  width?: number;
  height?: number;
  reducedMotion?: boolean;
}

export const Waveform: React.FC<WaveformProps> = ({
  isPlaying,
  color = "#38bdf8",
  barCount = 6,
  width = 24,
  height = 14,
  reducedMotion = false,
}) => {
  // Proportions: bar width is 1.3x gap
  const totalSlots = barCount + (barCount - 1) / 1.3;
  const barWidth = Math.max(1.8, width / totalSlots);
  const gap = barWidth / 1.3;

  // Staggered base heights for realistic equalizer frequency pattern
  const heights = [0.4, 0.8, 1.0, 0.7, 0.9, 0.5];

  return (
    <div
      style={{
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        gap: `${gap}px`,
        width: `${width}px`,
        height: `${height}px`,
      }}
    >
      {Array.from({ length: barCount }).map((_, i) => {
        const factor = heights[i % heights.length];
        const minH = 3;
        const maxH = height * factor;

        return (
          <motion.div
            key={i}
            animate={
              isPlaying
                ? reducedMotion
                  ? { height: maxH * 0.75 }
                  : {
                      height: [minH, maxH, minH * 1.5, maxH * 0.8, minH],
                    }
                : { height: minH }
            }
            transition={
              isPlaying && !reducedMotion
                ? {
                    duration: 0.85 + i * 0.12,
                    repeat: Infinity,
                    repeatType: "mirror",
                    ease: "easeInOut",
                    delay: i * 0.08,
                  }
                : { duration: 0.15 }
            }
            style={{
              width: `${barWidth}px`,
              borderRadius: `${barWidth / 2}px`,
              background: `linear-gradient(180deg, #818cf8 0%, ${color} 100%)`,
            }}
          />
        );
      })}
    </div>
  );
};
