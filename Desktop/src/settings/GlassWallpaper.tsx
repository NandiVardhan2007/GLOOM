import { useState } from "react";
import { invoke } from "@tauri-apps/api/core";
import { Image } from "lucide-react";
import { SettingRow } from "./SettingRow";
import { useT } from "../i18n";

export function GlassWallpaper() {
  const t = useT();
  const [on, setOn] = useState(() => localStorage.getItem("gloom-glass-wallpaper") === "true");

  const toggle = () => {
    const next = !on;
    setOn(next);
    localStorage.setItem("gloom-glass-wallpaper", String(next));
    invoke("save_setting", { key: "gloom-glass-wallpaper", value: String(next) }).catch(() => { });
  };

  return (
    <SettingRow icon={Image} label={t("appearance.glassWallpaper")} desc={t("appearance.glassWallpaper.desc")}>
      <label className="toggle-switch">
        <input type="checkbox" checked={on} onChange={toggle} />
        <span className="slider"></span>
      </label>
    </SettingRow>
  );
}
