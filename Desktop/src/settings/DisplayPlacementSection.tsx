import { useEffect, useState } from "react";
import { invoke } from "@tauri-apps/api/core";
import { listen } from "@tauri-apps/api/event";
import { Monitor } from "lucide-react";

export interface MonitorInfo {
	id: string;
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
	value: string; // "primary" | "all" | monitor id
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

	// Calculate bounding box for canvas layout
	const minX = monitors.length > 0 ? Math.min(...monitors.map((m) => m.x)) : 0;
	const maxX = monitors.length > 0 ? Math.max(...monitors.map((m) => m.x + m.width)) : 1920;
	const minY = monitors.length > 0 ? Math.min(...monitors.map((m) => m.y)) : 0;
	const maxY = monitors.length > 0 ? Math.max(...monitors.map((m) => m.y + m.height)) : 1080;

	const totalW = Math.max(1, maxX - minX);
	const totalH = Math.max(1, maxY - minY);

	const canvasWidth = 360;
	const canvasHeight = 130;
	const scale = Math.min((canvasWidth - 40) / totalW, (canvasHeight - 30) / totalH);

	const isSelected = (m: MonitorInfo) => {
		if (value === "all") return true;
		if (value === "primary" || !value) return m.is_primary;
		return value === m.id || value === m.name;
	};

	const handleBoxClick = (m: MonitorInfo) => {
		if (isSingleMonitor) return;
		if (m.is_primary) {
			onChange("primary");
		} else {
			onChange(m.id);
		}
	};

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
				<div className="setting-control">
					<select
						className="settings-select"
						value={isSingleMonitor ? "primary" : value || "primary"}
						disabled={isSingleMonitor}
						onChange={(e) => onChange(e.target.value)}
					>
						<option value="primary">Main display only (Primary)</option>
						{!isSingleMonitor &&
							monitors.map((m) => (
								<option key={m.id} value={m.id}>
									{m.label}
								</option>
							))}
						{!isSingleMonitor && (
							<option value="all">Both monitors (All displays)</option>
						)}
					</select>
				</div>
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

							return (
								<button
									key={m.id || idx}
									type="button"
									className={`display-box ${active ? "selected" : ""}`}
									style={{
										left: `${left}px`,
										top: `${top}px`,
										width: `${boxW}px`,
										height: `${boxH}px`
									}}
									onClick={() => handleBoxClick(m)}
									title={m.label}
								>
									<span className="display-box-number">{idx + 1}</span>
									{m.is_primary && <span className="display-box-badge">Primary</span>}
								</button>
							);
						})
					)}
				</div>
				{isSingleMonitor ? (
					<div className="display-single-hint">
						Single display detected. Placement is locked to your primary monitor.
					</div>
				) : (
					<div className="display-multi-hint">
						Click a display above or use the dropdown to set placement.
					</div>
				)}
			</div>
		</div>
	);
}
