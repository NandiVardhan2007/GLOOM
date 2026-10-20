import React from "react";

interface NotchShapeProps {
  width: number;
  height: number;
  topFlare?: number;
  bottomRadius?: number;
  fill?: string;
  stroke?: string;
}

export const NotchShape: React.FC<NotchShapeProps> = ({
  width,
  height,
  topFlare = 10,
  bottomRadius = 14,
  fill = "rgba(10, 10, 14, 0.94)",
  stroke = "rgba(255, 255, 255, 0.12)",
}) => {
  const tr = topFlare;
  const br = Math.min(bottomRadius, height, (width - 2 * tr) / 2);
  const left = tr;
  const right = width - tr;

  // Path starts at top left outer screen edge (0, 0),
  // curves down and inward to (left, tr) via control point (left, 0),
  // goes straight down to (left, height - br),
  // curves into bottom edge to (left + br, height) via (left, height),
  // straight line across to (right - br, height),
  // curves up to (right, height - br) via (right, height),
  // straight line up to (right, tr),
  // curves outward to top right screen edge (width, 0) via (right, 0),
  // and closes across top bezel.
  const pathData = `
    M 0 0
    Q ${left} 0 ${left} ${tr}
    L ${left} ${height - br}
    Q ${left} ${height} ${left + br} ${height}
    L ${right - br} ${height}
    Q ${right} ${height} ${right} ${height - br}
    L ${right} ${tr}
    Q ${right} 0 ${width} 0
    Z
  `;

  return (
    <svg
      width={width}
      height={height}
      style={{
        position: "absolute",
        top: 0,
        left: 0,
        pointerEvents: "none",
        filter: "drop-shadow(0 12px 28px rgba(0, 0, 0, 0.75))",
      }}
    >
      <path d={pathData} fill={fill} stroke={stroke} strokeWidth={1} />
    </svg>
  );
};
