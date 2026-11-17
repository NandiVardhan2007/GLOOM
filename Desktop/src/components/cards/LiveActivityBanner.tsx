import { motion, AnimatePresence } from "framer-motion";
import { ArrowDownCircle, X } from "lucide-react";
import { playTactileSound } from "../../utils/audioFeedback";
import "./LiveActivityBanner.css";

export interface LiveActivityItem {
	id: string;
	type: "download" | "timer" | "battery";
	title: string;
	progress?: number;
	detail: string;
}

export interface LiveActivityBannerProps {
	activities: LiveActivityItem[];
	onOpenDownloads?: () => void;
	onDismiss?: (id: string) => void;
}

export function LiveActivityBanner({
	activities,
	onOpenDownloads,
	onDismiss
}: LiveActivityBannerProps) {
	if (!activities || activities.length === 0) return null;

	const primary = activities[0];

	return (
		<AnimatePresence>
			<motion.div
				key={primary.id}
				className="live-activity-capsule"
				initial={{ opacity: 0, scale: 0.85, y: -8 }}
				animate={{ opacity: 1, scale: 1, y: 0 }}
				exit={{ opacity: 0, scale: 0.85, y: -8, transition: { duration: 0.15 } }}
				transition={{ type: "spring", stiffness: 450, damping: 30 }}
				onClick={(e) => {
					e.stopPropagation();
					playTactileSound("snap");
					onOpenDownloads?.();
				}}
				title="Click to open Downloads"
			>
				{/* Animated Icon */}
				<div className="live-activity-icon-wrapper">
					<ArrowDownCircle size={15} strokeWidth={2.4} className="live-activity-icon" />
					<div className="live-activity-pulse-glow" />
				</div>

				{/* Metadata info */}
				<div className="live-activity-info">
					<div className="live-activity-title-row">
						<span className="live-activity-title">{primary.title}</span>
						{activities.length > 1 && (
							<span className="live-activity-multi-badge">+{activities.length - 1}</span>
						)}
					</div>
					<div className="live-activity-detail-row">
						<span className="live-activity-detail">{primary.detail}</span>
						<span className="live-activity-state">Downloading…</span>
					</div>
				</div>

				{/* Indeterminate Activity Shimmer Bar */}
				<div className="live-activity-progress-track">
					<div className="live-activity-shimmer-thumb" />
				</div>

				{/* Optional Dismiss */}
				{onDismiss && (
					<button
						type="button"
						className="live-activity-dismiss-btn"
						onClick={(e) => {
							e.stopPropagation();
							playTactileSound("pop");
							onDismiss(primary.id);
						}}
						title="Dismiss live activity"
					>
						<X size={11} strokeWidth={2.5} />
					</button>
				)}
			</motion.div>
		</AnimatePresence>
	);
}
