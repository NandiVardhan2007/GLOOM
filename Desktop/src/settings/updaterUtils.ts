/**
 * Utilities for formatting updater progress and byte metrics.
 */

export function formatBytes(bytes: number): string {
	if (bytes <= 0 || isNaN(bytes)) return "0 MB";
	const mb = bytes / (1024 * 1024);
	return `${mb.toFixed(1)} MB`;
}

export interface UpdateProgressMeta {
	text: string;
	pct: number;
	isIndeterminate: boolean;
}

export function formatUpdateProgressMeta(
	status: string,
	progress: number,
	downloadedBytes: number = 0,
	totalBytes: number = 0
): UpdateProgressMeta {
	if (status === "installing") {
		return {
			text: "Restarting GLOOM to apply update...",
			pct: 100,
			isIndeterminate: false
		};
	}

	const clampedPct = Math.max(0, Math.min(100, progress || 0));

	if (totalBytes > 0 && downloadedBytes > 0) {
		return {
			text: `${formatBytes(downloadedBytes)} of ${formatBytes(totalBytes)}`,
			pct: clampedPct,
			isIndeterminate: false
		};
	}

	if (downloadedBytes > 0) {
		return {
			text: `${formatBytes(downloadedBytes)} downloaded`,
			pct: clampedPct,
			isIndeterminate: clampedPct === 0
		};
	}

	if (clampedPct > 0) {
		return {
			text: `${clampedPct}% downloaded`,
			pct: clampedPct,
			isIndeterminate: false
		};
	}

	return {
		text: "Connecting to server...",
		pct: 0,
		isIndeterminate: true
	};
}
