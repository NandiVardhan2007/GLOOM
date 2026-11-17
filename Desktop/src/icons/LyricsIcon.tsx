interface LyricsIconProps {
	size?: number;
	active?: boolean;
	className?: string;
	style?: React.CSSProperties;
}

export function LyricsIcon({ size = 20, active = false, className, style }: LyricsIconProps) {
	return (
		<svg
			width={size}
			height={size}
			viewBox="0 0 24 24"
			fill="none"
			stroke="currentColor"
			strokeWidth="1.8"
			strokeLinecap="round"
			strokeLinejoin="round"
			className={className}
			style={style}
		>
			{/* Apple Music style speech bubble with lyrics quotes */}
			<path
				d="M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.5 8.5 0 0 1 8 8v.5z"
				fill={active ? "currentColor" : "rgba(255, 255, 255, 0.08)"}
			/>
			<path
				d="M8.5 10.5h2.5M13.5 10.5h2M8.5 14h5"
				stroke={active ? "#000000" : "currentColor"}
				strokeWidth="2"
			/>
		</svg>
	);
}
