import MusicNoteSvg from "./sf/music.note.svg?react";

interface MusicNoteIconProps {
	size?: number;
	className?: string;
	style?: React.CSSProperties;
}

export function MusicNoteIcon({ size = 32, className, style }: MusicNoteIconProps) {
	return <MusicNoteSvg width={size} height={size} className={className} style={style} />;
}
