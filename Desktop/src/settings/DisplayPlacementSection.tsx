import { useEffect, useState, useMemo } from "react";
import { invoke } from "@tauri-apps/api/core";
import { listen } from "@tauri-apps/api/event";
import { Monitor, Check } from "lucide-react";

export interface MonitorInfo {
	id: string;
	gdi_name: string;
	name: string;
	label: string;
	is_primary: boolean;
	x: number;
	y: number;
	width: number;
	height: number;
	scale_factor: number;
}

interface DisplayPlacementSectionProps {
	title: string;
	description: string;
	value: string; // "primary" | "all" | JSON array of IDs | single ID
	onChange: (val: string) => void;
}

export function DisplayPlacementSection({
	title,
	description,
	value,
	onChange
}: DisplayPlacementSectionProps) {
	const [monitors, setMonitors] = useState<MonitorInfo[]>([]);

	useEffect(() => {
		let unlisten: (() => void) | undefined;

		const fetchMonitors = async () => {
			try {
				const list = await invoke<MonitorInfo[]>("get_monitors");
				if (list && list.length > 0) {
					setMonitors(list);
				}
			} catch (e) {
				console.error("Failed to query connected monitors:", e);
			}
		};

		fetchMonitors();

		listen("gloom-displays-changed", () => {
			fetchMonitors();
		}).then((fn) => {
			unlisten = fn;
		});

		return () => {
			unlisten?.();
		};
	}, []);

	const isSingleMonitor = monitors.length <= 1;

	// Parse current selected IDs based on setting value
	const selectedIds = useMemo(() => {
		const trimmed = (value || "").trim();
		if (!trimmed || trimmed === "primary") {
			const primary = monitors.find((m) => m.is_primary) || monitors[0];
			return primary ? new Set([primary.id]) : new Set<string>();
		}
		if (trimmed === "all") {
			return new Set(monitors.map((m) => m.id));
		}
		// Try JSON array
		try {
			const parsed = JSON.parse(trimmed);
			if (Array.isArray(parsed)) {
				return new Set<string>(parsed.filter((item) => typeof item === "string"));
			}
		} catch {
			// Not JSON
		}
		// Comma-separated or single ID
		if (trimmed.includes(",")) {
			return new Set<string>(trimmed.split(",").map((s) => s.trim()).filter(Boolean));
		}
		return new Set<string>([trimmed]);
	}, [value, monitors]);

	const isSelected = (m: MonitorInfo): boolean => {
		const trimmed = (value || "").trim();
		if (trimmed === "all") return true;
		if (!trimmed || trimmed === "primary") return m.is_primary;
		return (
			selectedIds.has(m.id) ||
			(!!m.gdi_name && selectedIds.has(m.gdi_name)) ||
			(!!m.name && selectedIds.has(m.name))
		);
	};

	// Count currently selected connected monitors
	const selectedCount = useMemo(() => {
		return monitors.filter((m) => isSelected(m)).length;
	}, [monitors, value, selectedIds]);

	const handleToggleMonitor = (m: MonitorInfo) => {
		if (isSingleMonitor) return;

		const currentlySelected = isSelected(m);

		// Disallow unchecking the last remaining selected monitor
		if (currentlySelected && selectedCount <= 1) {
			return;
		}

		// Convert current state into an explicit array of IDs
		const currentSelectedList: string[] = [];
		for (const mon of monitors) {
			if (isSelected(mon)) {
				currentSelectedList.push(mon.id);
			}
		}

		let nextIds: string[];
		if (currentlySelected) {
			nextIds = currentSelectedList.filter(
				(id) => id !== m.id && id !== m.gdi_name && id !== m.name
			);
		} else {
			nextIds = [...currentSelectedList, m.id];
		}

		// Save as explicit JSON array
		onChange(JSON.stringify(nextIds));
	};

	const handleSelectPrimaryShortcut = () => {
		onChange("primary");
	};

	const handleSelectAllShortcut = () => {
		onChange("all");
	};

	// Calculate bounding box for canvas layout
	const minX = monitors.length > 0 ? Math.min(...monitors.map((m) => m.x)) : 0;
	const maxX = monitors.length > 0 ? Math.max(...monitors.map((m) => m.x + m.width)) : 1920;
	const minY = monitors.length > 0 ? Math.min(...monitors.map((m) => m.y)) : 0;
	const maxY = monitors.length > 0 ? Math.max(...monitors.map((m) => m.y + m.height)) : 1080;

	const totalW = Math.max(1, maxX - minX);
	const totalH = Math.max(1, maxY - minY);

	const canvasWidth = 360;
	const canvasHeight = 120;
	const scale = Math.min((canvasWidth - 40) / totalW, (canvasHeight - 30) / totalH);

	return (
		<div className="display-placement-container">
			<div className="setting-row">
				<div className="setting-icon-box">
					<Monitor size={18} />
				</div>
				<div className="setting-info">
					<div className="setting-label">{title}</div>
					<div className="setting-desc">{description}</div>
				</div>
				{!isSingleMonitor && (
					<div className="display-shortcuts">
						<button
							type="button"
							className={`display-shortcut-btn ${(value || "").trim() === "primary" ? "active" : ""}`}
							onClick={handleSelectPrimaryShortcut}
						>
							Primary only
						</button>
						<button
							type="button"
							className={`display-shortcut-btn ${(value || "").trim() === "all" ? "active" : ""}`}
							onClick={handleSelectAllShortcut}
						>
							All displays
						</button>
					</div>
				)}
			</div>

			{/* Interactive Windows 11 style Monitor Layout Canvas */}
			<div className="display-canvas-wrapper">
				<div className="display-canvas" style={{ width: `${canvasWidth}px`, height: `${canvasHeight}px` }}>
					{monitors.length === 0 ? (
						<div
							className="display-box selected"
							style={{
								width: "140px",
								height: "75px",
								position: "relative"
							}}
						>
							<span className="display-box-number">1</span>
							<span className="display-box-badge">Primary</span>
						</div>
					) : (
						monitors.map((m, idx) => {
							const boxW = Math.max(65, m.width * scale);
							const boxH = Math.max(48, m.height * scale);
							const left = (m.x - minX) * scale + 20;
							const top = (m.y - minY) * scale + 15;
							const active = isSelected(m);
							const disabled = isSingleMonitor || (active && selectedCount <= 1);

							return (
								<button
									key={m.id || idx}
									type="button"
									className={`display-box ${active ? "selected" : ""} ${disabled ? "locked" : ""}`}
									style={{
										left: `${left}px`,
										top: `${top}px`,
										width: `${boxW}px`,
										height: `${boxH}px`
									}}
									onClick={() => handleToggleMonitor(m)}
									title={`${m.label}${active && selectedCount <= 1 ? " (At least one display must stay selected)" : ""}`}
								>
									<span className="display-box-number">{idx + 1}</span>
									{m.is_primary && <span className="display-box-badge">Primary</span>}
									{active && (
										<span className="display-box-check">
											<Check size={12} strokeWidth={3} />
										</span>
									)}
								</button>
							);
						})
					)}
				</div>

				{/* Per-Monitor Checklist */}
				{!isSingleMonitor && monitors.length > 0 && (
					<div className="display-checklist" role="group" aria-label="Monitors checklist">
						{monitors.map((m, idx) => {
							const active = isSelected(m);
							const isOnlySelected = active && selectedCount <= 1;

							return (
								<label
									key={m.id || idx}
									className={`display-check-item ${active ? "checked" : ""} ${isOnlySelected ? "locked" : ""}`}
								>
									<input
										type="checkbox"
										className="display-check-input"
										checked={active}
										disabled={isOnlySelected}
										onChange={() => handleToggleMonitor(m)}
										aria-label={m.label}
									/>
									<span className="display-check-indicator">
										{active && <Check size={12} strokeWidth={3} />}
									</span>
									<div className="display-check-text">
										<span className="display-check-title">
											Monitor {idx + 1} - {m.name}
											{m.is_primary && <span className="display-primary-pill">Primary</span>}
										</span>
										<span className="display-check-specs">
											{m.width}×{m.height} • {Math.round(m.scale_factor * 100)}% scaling
										</span>
									</div>
								</label>
							);
						})}
					</div>
				)}

				{isSingleMonitor ? (
					<div className="display-single-hint">
						Single display detected. Placement is locked to your primary monitor.
					</div>
				) : (
					<div className="display-multi-hint">
						Click monitors above or check the boxes to customize placement. At least one display must stay selected.
					</div>
				)}
			</div>
		</div>
	);
}
