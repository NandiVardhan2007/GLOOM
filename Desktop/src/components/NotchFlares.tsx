import React from "react";

interface NotchFlaresProps {
	/** Whether the concave flares should be visible */
	visible: boolean;
	/** Fill color for the flare bodies (defaults to pure Apple obsidian black #000000) */
	color?: string;
	/** Outline rim color matching the notch border */
	strokeColor?: string;
}

/**
 * MacBook-style concave curvy ear flares bridging the attached Notch to the top screen edge.
 * Uses exact Apple cubic bezier curves with matched 1px border rim highlights.
 */
export const NotchFlares: React.FC<NotchFlaresProps> = ({
	visible,
	color = "#000000",
	strokeColor = "rgba(255, 255, 255, 0.08)"
}) => {
	if (!visible) return null;

	return (
		<>
			{/* Left Concave Flare / Ear */}
			<div
				className="notch-flare notch-flare-left"
				aria-hidden="true"
				style={{
					position: "absolute",
					top: 0,
					left: -14,
					width: 14,
					height: 14,
					pointerEvents: "none",
					zIndex: 10
				}}
			>
				<svg width="14" height="14" viewBox="0 0 14 14" fill="none" xmlns="http://www.w3.org/2000/svg">
					<path d="M 0 0 L 14 0 L 14 14 C 14 5.5 8.5 0 0 0 Z" fill={color} />
					<path d="M 0 0.5 C 8 0.5 13.5 6 13.5 14" stroke={strokeColor} strokeWidth="1" fill="none" />
				</svg>
			</div>

			{/* Right Concave Flare / Ear */}
			<div
				className="notch-flare notch-flare-right"
				aria-hidden="true"
				style={{
					position: "absolute",
					top: 0,
					right: -14,
					width: 14,
					height: 14,
					pointerEvents: "none",
					zIndex: 10
				}}
			>
				<svg width="14" height="14" viewBox="0 0 14 14" fill="none" xmlns="http://www.w3.org/2000/svg">
					<path d="M 14 0 L 0 0 L 0 14 C 0 5.5 5.5 0 14 0 Z" fill={color} />
					<path d="M 14 0.5 C 6 0.5 0.5 6 0.5 14" stroke={strokeColor} strokeWidth="1" fill="none" />
				</svg>
			</div>
		</>
	);
};
