import React from "react";
import { AppConfig } from "../types";
import { SettingsView } from "./SettingsView";

interface SettingsSheetProps {
  config: AppConfig;
  onUpdateConfig: (newCfg: AppConfig) => void;
  onClose?: () => void;
}

export const SettingsSheet: React.FC<SettingsSheetProps> = ({
  config,
  onUpdateConfig,
  onClose,
}) => {
  return (
    <div style={{ width: "100%", height: "100%", overflow: "hidden" }}>
      <SettingsView
        config={config}
        onUpdateConfig={onUpdateConfig}
        standalone={false}
        onClose={onClose}
      />
    </div>
  );
};
