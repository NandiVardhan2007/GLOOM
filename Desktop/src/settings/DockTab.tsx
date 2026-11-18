import { useEffect, useState, type ChangeEvent, type CSSProperties } from "react";
import { invoke } from "@tauri-apps/api/core";
import {
	Monitor,
	Eye,
	EyeOff,
	Circle,
	Columns2,
	Maximize2,
	Keyboard,
	Sparkles,
	RotateCcw,
	Timer,
	LayoutPanelLeft,
	HardDrive,
	Trash2,
	Copy,
	FolderOpen,
	FolderPlus,
	Plus,
	X,
	BellDot,
	SquareTerminal,
	Search
} from "lucide-react";
import { SettingRow } from "./SettingRow";
import {
	KNOWN_FOLDER_OPTIONS,
	driveLetter,
	parseEnabledFolders,
	parseCustomFolders,
	parseHiddenDrives,
	type DockExtra
} from "../dockExtras";

/** Where a setting row's label starts: the row icon's 28px plus its 12px margin.
 * Content under a label (chips, lists) is indented by this to line up with it. */
const ROW_CONTENT_INDENT = "40px";

/** One chip per connected drive, plus hidden drives that aren't connected. */
function DrivePicker({
	hiddenDrives,
	onToggle
}: {
	hiddenDrives: string;
	onToggle: (letter: string) => void;
}) {
	const [drives, setDrives] = useState<{ letter: string; name: string }[]>([]);

	useEffect(() => {
		invoke<DockExtra[]>("get_dock_drives")
			.then((list) =>
				setDrives(
					list.flatMap((drive) => {
						const letter = driveLetter(drive);
						return letter ? [{ letter, name: drive.name }] : [];
					})
				)
			)
			.catch((error) => console.error("Failed to list drives:", error));
	}, []);

	const hidden = parseHiddenDrives(hiddenDrives);
	const disconnected = hidden
		.filter((letter) => !drives.some((drive) => drive.letter === letter))
		.map((letter) => ({ letter, name: `${letter}: (not connected)` }));

	return (
		<div style={{ display: "flex", gap: "8px", flexWrap: "wrap", paddingLeft: ROW_CONTENT_INDENT }}>
			{[...drives, ...disconnected].map((drive) => (
				<button
					key={drive.letter}
					type="button"
					className={`folder-chip ${hidden.includes(drive.letter) ? "" : "active"}`}
					onClick={() => onToggle(drive.letter)}
				>
					{drive.name}
				</button>
			))}
		</div>
	);
}

const START_ICON_PRESETS = [
	{ key: "default", src: "/gloom.svg", label: "GLOOM" },
	{ key: "gloom-colorful", src: "/gloom-colorful.svg", label: "Colorful" },
	{ key: "gloom-golden", src: "/gloom-golden.svg", label: "Golden" },
	{ key: "gloom-biscuit", src: "/gloom-biscuit.svg", label: "Orange" },
	{ key: "windows", src: "/windows-11.png", label: "Windows" }
];

const startIconTileStyle = (active: boolean): CSSProperties => ({
	width: "48px",
	height: "48px",
	borderRadius: "12px",
	border: active ? "2px solid var(--gloom-accent, #007aff)" : "2px solid rgba(255,255,255,0.1)",
	background: active ? "rgba(0,122,255,0.15)" : "rgba(255,255,255,0.05)",
	display: "flex",
	alignItems: "center",
	justifyContent: "center",
	cursor: "pointer",
	transition: "all 0.15s ease",
	padding: "6px",
	color: "rgba(255,255,255,0.5)"
});

interface DockTabProps {
	dockEnabled: boolean;
	toggleDock: () => void;
	dockMode: string;
	setDockModeValue: (mode: string) => void;
	dockEdgeDelay: number;
	handleDockEdgeDelayChange: (val: number) => void;
	dockPreviewEnabled: boolean;
	toggleDockPreview: () => void;
	dockIconOnly: boolean;
	toggleDockIconOnly: () => void;
	dockSeparatorEnabled: boolean;
	toggleDockSeparator: () => void;
	dockAdaptive: boolean;
	toggleDockAdaptive: () => void;
	dockWinNumberEnabled: boolean;
	toggleDockWinNumber: () => void;
	dockExtrasPosition: string;
	handleDockExtrasPositionChange: (position: string) => void;
	dockExtrasDrives: boolean;
	toggleDockExtrasDrives: () => void;
	dockExtrasHiddenDrives: string;
	toggleDockExtrasDrive: (letter: string) => void;
	dockExtrasRecycleBin: boolean;
	toggleDockExtrasRecycleBin: () => void;
	dockTaskView: boolean;
	toggleDockTaskView: () => void;
	dockExtrasFolders: string;
	toggleDockExtraFolder: (id: string) => void;
	dockExtrasCustomFolders: string;
	handleAddDockExtraFolder: () => void;
	handleRemoveDockExtraFolder: (path: string) => void;
	startIcon: string;
	handleStartIconChange: (icon: string) => void;
	dockAttentionEnabled?: boolean;
	toggleDockAttention?: () => void;
	dockConsoleWindows?: boolean;
	toggleDockConsoleWindows?: () => void;
	dockSearchEnabled?: boolean;
	toggleDockSearch?: () => void;
	dockSearchProvider?: string;
	handleDockSearchProviderChange?: (provider: string) => void;
	dockPreviewDelay?: number;
	handleDockPreviewDelayChange?: (val: number) => void;
	dockHideDelay?: number;
	handleDockHideDelayChange?: (val: number) => void;
}

export function DockTab({
	dockEnabled,
	toggleDock,
	dockMode,
	setDockModeValue,
	dockEdgeDelay,
	handleDockEdgeDelayChange,
	dockPreviewEnabled,
	toggleDockPreview,
	dockIconOnly,
	toggleDockIconOnly,
	dockSeparatorEnabled,
	toggleDockSeparator,
	dockAdaptive,
	toggleDockAdaptive,
	dockWinNumberEnabled,
	toggleDockWinNumber,
	dockExtrasPosition,
	handleDockExtrasPositionChange,
	dockExtrasDrives,
	toggleDockExtrasDrives,
	dockExtrasHiddenDrives,
	toggleDockExtrasDrive,
	dockExtrasRecycleBin,
	toggleDockExtrasRecycleBin,
	dockTaskView,
	toggleDockTaskView,
	dockExtrasFolders,
	toggleDockExtraFolder,
	dockExtrasCustomFolders,
	handleAddDockExtraFolder,
	handleRemoveDockExtraFolder,
	startIcon,
	handleStartIconChange,
	dockAttentionEnabled = true,
	toggleDockAttention,
	dockConsoleWindows = false,
	toggleDockConsoleWindows,
	dockSearchEnabled = true,
	toggleDockSearch,
	dockSearchProvider = "windows",
	handleDockSearchProviderChange,
	dockPreviewDelay = 200,
	handleDockPreviewDelayChange,
	dockHideDelay = 300,
	handleDockHideDelayChange
}: DockTabProps) {
	const handleStartIconUpload = (e: ChangeEvent<HTMLInputElement>) => {
		const file = e.target.files?.[0];
		if (!file) return;
		const reader = new FileReader();
		reader.onload = () => {
			handleStartIconChange(`custom:${reader.result as string}`);
		};
		reader.readAsDataURL(file);
		e.target.value = "";
	};

	return (
		<>
			<div className="setting-group-label">Dock</div>
			<div className="setting-group">
				<SettingRow icon={Monitor} label="GLOOM Dock" desc="Replace Windows taskbar">
					<label className="toggle-switch">
						<input type="checkbox" checked={dockEnabled} onChange={toggleDock} />
						<span className="slider"></span>
					</label>
				</SettingRow>

				{dockEnabled && (
					<>
						<SettingRow
							icon={dockMode === "fixed" ? EyeOff : Eye}
							label="Behavior"
							desc="Choose how the dock appears"
						>
							<select
								className="settings-select"
								value={dockMode}
								onChange={(e) => setDockModeValue(e.target.value)}
							>
								<option value="fixed">Fixed</option>
								<option value="smart">Smart</option>
								<option value="peek">Peek</option>
							</select>
						</SettingRow>

						{dockMode !== "fixed" && (
							<SettingRow
								icon={Timer}
								label="Peek Delay"
								desc={
									dockEdgeDelay === 0
										? "Reveal instantly on bottom-edge contact"
										: `Rest on the bottom edge for ${dockEdgeDelay}ms to reveal`
								}
							>
								<input
									type="range"
									min="0"
									max="800"
									step="50"
									value={dockEdgeDelay}
									onChange={(e) => handleDockEdgeDelayChange(parseInt(e.target.value))}
									className="settings-slider"
								/>
							</SettingRow>
						)}

						<SettingRow icon={Eye} label="Show App Previews" desc="Show window thumbnails on hover">
							<label className="toggle-switch">
								<input type="checkbox" checked={dockPreviewEnabled} onChange={toggleDockPreview} />
								<span className="slider"></span>
							</label>
						</SettingRow>

						{dockPreviewEnabled && handleDockPreviewDelayChange && (
							<SettingRow
								icon={Timer}
								label="Preview Delay"
								desc={`Hover duration before showing preview (${dockPreviewDelay}ms)`}
							>
								<input
									type="range"
									min="0"
									max="1000"
									step="50"
									value={dockPreviewDelay}
									onChange={(e) => handleDockPreviewDelayChange(parseInt(e.target.value))}
									className="settings-slider"
								/>
							</SettingRow>
						)}

						{dockMode !== "fixed" && handleDockHideDelayChange && (
							<SettingRow
								icon={Timer}
								label="Hide Delay"
								desc={`Delay before dock auto-hides (${dockHideDelay}ms)`}
							>
								<input
									type="range"
									min="0"
									max="1500"
									step="50"
									value={dockHideDelay}
									onChange={(e) => handleDockHideDelayChange(parseInt(e.target.value))}
									className="settings-slider"
								/>
							</SettingRow>
						)}

						{toggleDockAttention && (
							<SettingRow
								icon={BellDot}
								label="Attention Indicators"
								desc="Show pulsing attention indicators when an app needs focus"
							>
								<label className="toggle-switch">
									<input
										type="checkbox"
										checked={dockAttentionEnabled}
										onChange={toggleDockAttention}
									/>
									<span className="slider"></span>
								</label>
							</SettingRow>
						)}

						{toggleDockConsoleWindows && (
							<SettingRow
								icon={SquareTerminal}
								label="Show Console Windows"
								desc="Display terminal and command prompt windows in the dock"
							>
								<label className="toggle-switch">
									<input
										type="checkbox"
										checked={dockConsoleWindows}
										onChange={toggleDockConsoleWindows}
									/>
									<span className="slider"></span>
								</label>
							</SettingRow>
						)}

						{toggleDockSearch && (
							<SettingRow
								icon={Search}
								label="Search Widget"
								desc="Quick search launcher in the dock"
							>
								<div style={{ display: "flex", gap: "8px", alignItems: "center" }}>
									{dockSearchEnabled && handleDockSearchProviderChange && (
										<select
											className="settings-select"
											value={dockSearchProvider}
											onChange={(e) => handleDockSearchProviderChange(e.target.value)}
										>
											<option value="windows">Windows Search</option>
											<option value="ptrun">PowerToys Run</option>
											<option value="raycast">Raycast</option>
											<option value="shortcut">Custom Shortcut</option>
										</select>
									)}
									<label className="toggle-switch">
										<input
											type="checkbox"
											checked={dockSearchEnabled}
											onChange={toggleDockSearch}
										/>
										<span className="slider"></span>
									</label>
								</div>
							</SettingRow>
						)}

						<SettingRow icon={Circle} label="Icon Only" desc="Remove icon background and padding">
							<label className="toggle-switch">
								<input type="checkbox" checked={dockIconOnly} onChange={toggleDockIconOnly} />
								<span className="slider"></span>
							</label>
						</SettingRow>

						<SettingRow
							icon={Columns2}
							label="App Separator"
							desc="Show a divider before unpinned apps"
						>
							<label className="toggle-switch">
								<input
									type="checkbox"
									checked={dockSeparatorEnabled}
									onChange={toggleDockSeparator}
								/>
								<span className="slider"></span>
							</label>
						</SettingRow>

						<SettingRow icon={Copy} label="Task View" desc="Show the Task View button beside Start">
							<label className="toggle-switch">
								<input type="checkbox" checked={dockTaskView} onChange={toggleDockTaskView} />
								<span className="slider"></span>
							</label>
						</SettingRow>

						<SettingRow
							icon={Keyboard}
							label="Win+Number Shortcuts"
							desc="Open pinned apps with Win+1 through Win+9"
							divider={dockMode === "fixed"}
						>
							<label className="toggle-switch">
								<input
									type="checkbox"
									checked={dockWinNumberEnabled}
									onChange={toggleDockWinNumber}
								/>
								<span className="slider"></span>
							</label>
						</SettingRow>

						{dockMode === "fixed" && (
							<SettingRow
								icon={Maximize2}
								label="Adaptive Mode"
								desc="Stretch to full width when a window is maximized"
								divider={false}
							>
								<label className="toggle-switch">
									<input type="checkbox" checked={dockAdaptive} onChange={toggleDockAdaptive} />
									<span className="slider"></span>
								</label>
							</SettingRow>
						)}

						<SettingRow
							icon={LayoutPanelLeft}
							label="Dock Extras"
							desc="Show drives, folders and the Recycle Bin beside the apps"
						>
							<select
								className="settings-select"
								value={dockExtrasPosition}
								onChange={(e) => handleDockExtrasPositionChange(e.target.value)}
							>
								<option value="off">Off</option>
								<option value="left">Left</option>
								<option value="right">Right</option>
							</select>
						</SettingRow>

						{dockExtrasPosition !== "off" && (
							<>
								<SettingRow
									icon={HardDrive}
									label="Drives"
									desc="Fixed, removable and connected network drives"
									divider={!dockExtrasDrives}
								>
									<label className="toggle-switch">
										<input
											type="checkbox"
											checked={dockExtrasDrives}
											onChange={toggleDockExtrasDrives}
										/>
										<span className="slider"></span>
									</label>
								</SettingRow>

								{dockExtrasDrives && (
									<>
										<div className="setting-item" style={{ paddingTop: 0, paddingBottom: 12 }}>
											<DrivePicker
												hiddenDrives={dockExtrasHiddenDrives}
												onToggle={toggleDockExtrasDrive}
											/>
										</div>
										<div className="setting-divider" />
									</>
								)}

								<SettingRow
									icon={Trash2}
									label="Recycle Bin"
									desc="Show the Recycle Bin in the extras"
								>
									<label className="toggle-switch">
										<input
											type="checkbox"
											checked={dockExtrasRecycleBin}
											onChange={toggleDockExtrasRecycleBin}
										/>
										<span className="slider"></span>
									</label>
								</SettingRow>

								<div
									className="setting-item"
									style={{ flexDirection: "column", alignItems: "flex-start", gap: "10px" }}
								>
									<div style={{ display: "flex", alignItems: "center", width: "100%" }}>
										<div className="setting-icon-bg">
											<FolderOpen size={14} strokeWidth={1.5} />
										</div>
										<div className="setting-info">
											<span className="setting-label">Special Folders</span>
											<span className="setting-desc">
												Pick the folders that appear in the extras
											</span>
										</div>
									</div>
									<div
										style={{
											display: "flex",
											gap: "8px",
											flexWrap: "wrap",
											paddingLeft: ROW_CONTENT_INDENT
										}}
									>
										{KNOWN_FOLDER_OPTIONS.map((folder) => {
											const active = parseEnabledFolders(dockExtrasFolders).includes(folder.id);
											return (
												<button
													key={folder.id}
													type="button"
													className={`folder-chip ${active ? "active" : ""}`}
													onClick={() => toggleDockExtraFolder(folder.id)}
												>
													{folder.label}
												</button>
											);
										})}
									</div>
								</div>
								<div className="setting-divider" />

								<div
									className="setting-item"
									style={{ flexDirection: "column", alignItems: "flex-start", gap: "10px" }}
								>
									<div style={{ display: "flex", alignItems: "center", width: "100%" }}>
										<div className="setting-icon-bg">
											<FolderPlus size={14} strokeWidth={1.5} />
										</div>
										<div className="setting-info">
											<span className="setting-label">Custom Folders</span>
											<span className="setting-desc">Add any folder to the extras section</span>
										</div>
									</div>
									<div
										style={{
											display: "flex",
											flexDirection: "column",
											gap: "6px",
											width: "100%",
											paddingLeft: ROW_CONTENT_INDENT,
											boxSizing: "border-box"
										}}
									>
										{parseCustomFolders(dockExtrasCustomFolders).map((folder) => (
											<div key={folder.path} className="extra-folder-row">
												<span className="extra-folder-name">{folder.name}</span>
												<span className="extra-folder-path">{folder.path}</span>
												<button
													type="button"
													className="extra-folder-remove"
													title="Remove folder"
													onClick={() => handleRemoveDockExtraFolder(folder.path)}
												>
													<X size={14} strokeWidth={2} />
												</button>
											</div>
										))}
										<button
											type="button"
											className="folder-chip add"
											onClick={handleAddDockExtraFolder}
										>
											<Plus size={13} strokeWidth={2} />
											Add Folder...
										</button>
									</div>
								</div>
							</>
						)}

						<div className="setting-divider" />
						<div
							className="setting-item"
							style={{ flexDirection: "column", alignItems: "flex-start", gap: "10px" }}
						>
							<div style={{ display: "flex", alignItems: "center", width: "100%" }}>
								<div className="setting-icon-bg">
									<Sparkles size={14} strokeWidth={1.5} />
								</div>
								<div className="setting-info">
									<span className="setting-label">Start Menu Icon</span>
									<span className="setting-desc">Choose the dock start button icon</span>
								</div>
							</div>
							<div
								style={{
									display: "flex",
									gap: "8px",
									flexWrap: "wrap",
									paddingLeft: ROW_CONTENT_INDENT
								}}
							>
								{START_ICON_PRESETS.map((icon) => (
									<div
										key={icon.key}
										onClick={() => handleStartIconChange(icon.key)}
										style={startIconTileStyle(startIcon === icon.key)}
										title={icon.label}
									>
										<img
											src={icon.src}
											alt={icon.label}
											style={{ width: "100%", height: "100%", objectFit: "contain" }}
											draggable={false}
										/>
									</div>
								))}
								<div
									onClick={() => document.getElementById("start-icon-file-input")?.click()}
									style={startIconTileStyle(startIcon.startsWith("custom:"))}
									title="Custom icon"
								>
									{startIcon.startsWith("custom:") ? (
										<img
											src={startIcon.replace("custom:", "")}
											alt="Custom"
											style={{
												width: "100%",
												height: "100%",
												objectFit: "contain",
												borderRadius: "8px"
											}}
											draggable={false}
										/>
									) : (
										<Plus size={20} strokeWidth={2} />
									)}
								</div>
								{startIcon !== "default" && (
									<div
										onClick={() => handleStartIconChange("default")}
										style={startIconTileStyle(false)}
										title="Reset to default"
									>
										<RotateCcw size={18} strokeWidth={1.5} />
									</div>
								)}
							</div>
							<input
								id="start-icon-file-input"
								type="file"
								accept=".png,.ico,.jpg,.jpeg,.svg,.bmp"
								style={{ display: "none" }}
								onChange={handleStartIconUpload}
							/>
						</div>
					</>
				)}
			</div>
		</>
	);
}
