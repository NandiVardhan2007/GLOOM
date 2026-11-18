import { Download, RefreshCw, FileDown, Upload, CheckCircle2, Sparkles, Compass } from "lucide-react";
import { openUrl } from "@tauri-apps/plugin-opener";
import { emit } from "@tauri-apps/api/event";
import { GithubIcon } from "../icons";
import { SettingRow } from "./SettingRow";
import { formatUpdateProgressMeta } from "./updaterUtils";

interface AboutTabProps {
	appVersion: string;
	autoUpdate: boolean;
	toggleAutoUpdate: () => void;
	updateStatus: string;
	updateVersion: string;
	checkForUpdates: () => void;
	installUpdate: () => void;
	exportStatus: string;
	importStatus: string;
	handleExportSettings: () => void;
	handleImportSettings: () => void;
	updateProgress?: number;
	updateDownloadedBytes?: number;
	updateTotalBytes?: number;
}

export function AboutTab({
	appVersion,
	autoUpdate,
	toggleAutoUpdate,
	updateStatus,
	updateVersion,
	checkForUpdates,
	installUpdate,
	exportStatus,
	importStatus,
	handleExportSettings,
	handleImportSettings,
	updateProgress = 0,
	updateDownloadedBytes = 0,
	updateTotalBytes = 0
}: AboutTabProps) {
	const isDownloading = updateStatus === "downloading";
	const isInstalling = updateStatus === "installing";
	const isChecking = updateStatus === "checking";
	const isAvailable = updateStatus === "available";
	const isUpToDate = updateStatus === "uptodate";
	const isBusy = isDownloading || isInstalling || isChecking;

	const getUpdateIcon = () => {
		if (isUpToDate) return CheckCircle2;
		if (isAvailable) return Sparkles;
		return RefreshCw;
	};

	const getUpdateLabel = () => {
		if (isChecking) return "Checking for Updates...";
		if (isAvailable) return `Update Available (v${updateVersion})`;
		if (isUpToDate) return "GLOOM is Up to Date";
		if (isDownloading) return "Downloading Update...";
		if (isInstalling) return "Installing Update...";
		if (updateStatus === "error") return "Check for Updates";
		return "Check for Updates";
	};

	const getUpdateDesc = () => {
		if (isDownloading || isInstalling) {
			const meta = formatUpdateProgressMeta(
				updateStatus,
				updateProgress,
				updateDownloadedBytes,
				updateTotalBytes
			);

			return (
				<div className="update-progress-container">
					<div className="update-progress-meta">
						<span className="update-progress-subtext">{meta.text}</span>
						<span className="update-progress-pct tabular-nums">{meta.pct}%</span>
					</div>
					<div className="about-progress-track">
						<div
							className={`about-progress-fill ${meta.isIndeterminate ? "indeterminate" : ""}`}
							style={{ width: `${meta.pct}%` }}
						/>
					</div>
				</div>
			);
		}

		if (isAvailable) {
			return "A new release is ready to download and install";
		}

		if (isUpToDate) {
			return `Version ${appVersion} is the latest version`;
		}

		if (updateStatus === "error") {
			return `No updates found • Running v${appVersion}`;
		}

		return `Currently running v${appVersion}`;
	};

	const getExportLabel = () => {
		if (exportStatus === "exporting") return "Exporting...";
		if (exportStatus === "success") return "Exported!";
		if (exportStatus === "error") return "Export failed. Try again.";
		return "Export Settings";
	};

	const getImportLabel = () => {
		if (importStatus === "importing") return "Importing...";
		if (importStatus === "success") return "Imported!";
		if (importStatus === "error") return "Import failed. Check the file and try again.";
		return "Import Settings";
	};

	return (
		<div className="about-tab-container">
			<div className="about-header">
				<img src="/gloom.svg" className="about-logo" alt="GLOOM Logo" draggable={false} />
				<h1 className="about-title">GLOOM</h1>
				<p className="about-version">Version {appVersion}</p>
				<p className="about-credit">
					Made with <span className="about-heart">❤️</span> by{" "}
					<button
						className="about-author"
						onClick={() => openUrl("https://github.com/NandiVardhan2007")}
						title="Nandu on GitHub"
					>
						<GithubIcon size={12} />
						<span>Nandu</span>
					</button>
				</p>
			</div>

			<div className="setting-group-label">Software Updates</div>
			<div className="setting-group">
				<SettingRow icon={Download} label="Auto Update" desc="Update automatically on startup">
					<label className="toggle-switch">
						<input type="checkbox" checked={autoUpdate} onChange={toggleAutoUpdate} />
						<span className="slider"></span>
					</label>
				</SettingRow>

				<SettingRow
					icon={getUpdateIcon()}
					iconClassName={isBusy ? "setting-icon--spin" : ""}
					label={getUpdateLabel()}
					desc={getUpdateDesc()}
					action={!isBusy}
					divider={false}
					onClick={() => {
						if (isBusy) return;
						if (isAvailable) {
							installUpdate();
						} else {
							checkForUpdates();
						}
					}}
				>
					{isAvailable && (
						<button
							className="update-action-btn"
							onClick={(e) => {
								e.stopPropagation();
								installUpdate();
							}}
						>
							<Download size={12} strokeWidth={2.2} />
							<span>Update Now</span>
						</button>
					)}
				</SettingRow>
			</div>

			<div className="setting-group-label setting-group-label--spaced">Data</div>
			<div className="setting-group">
				<SettingRow
					icon={Compass}
					label="Interactive Feature Tour"
					desc="Replay the walkthrough guide of Dynamic Island, panels and dock"
					action
					onClick={() => emit("start-tour")}
				/>
				<SettingRow
					icon={FileDown}
					label={getExportLabel()}
					desc="Save settings to a file"
					action
					onClick={handleExportSettings}
				/>
				<SettingRow
					icon={Upload}
					label={getImportLabel()}
					desc="Load settings from a file"
					action
					divider={false}
					onClick={handleImportSettings}
				/>
			</div>
		</div>
	);
}
