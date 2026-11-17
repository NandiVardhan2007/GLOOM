export function formatRelativeTime(timestamp: number, now: number = Date.now()): string {
	const elapsedSeconds = Math.max(0, Math.floor((now - timestamp) / 1000));
	if (elapsedSeconds < 10) return "Just now";
	if (elapsedSeconds < 60) return `${elapsedSeconds}s ago`;
	const mins = Math.floor(elapsedSeconds / 60);
	if (mins < 60) return `${mins}m ago`;
	const hours = Math.floor(mins / 60);
	if (hours < 24) return `${hours}h ago`;
	return `${Math.floor(hours / 24)}d ago`;
}

export function countWordsAndChars(text: string): { words: number; chars: number } {
	const trimmed = text.trim();
	const words = trimmed.length === 0 ? 0 : trimmed.split(/\s+/).length;
	return { words, chars: text.length };
}

export function truncateSnippet(text: string, maxLength: number = 80): string {
	if (text.length <= maxLength) return text;
	return text.slice(0, maxLength).trimEnd() + "…";
}
