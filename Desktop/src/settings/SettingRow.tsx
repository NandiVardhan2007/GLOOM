import type { SettingRowProps } from "./types";

export function SettingRow({
	icon: Icon,
	label,
	desc,
	action,
	danger,
	divider = true,
	onClick,
	children,
	iconClassName
}: SettingRowProps) {
	const className = ["setting-item", action ? "action" : "", danger ? "danger" : ""]
		.filter(Boolean)
		.join(" ");

	return (
		<>
			<div className={className} onClick={onClick}>
				<div className="setting-icon-bg">
					<Icon size={14} strokeWidth={1.5} className={iconClassName} />
				</div>
				<div className="setting-info">
					<span className="setting-label">{label}</span>
					{desc && <div className="setting-desc">{desc}</div>}
				</div>
				{children}
			</div>
			{divider && <div className="setting-divider" />}
		</>
	);
}
