import React, { useState, useEffect } from "react";
import { invoke } from "@tauri-apps/api/core";
import { listen } from "@tauri-apps/api/event";
import { BridgeActivity, BridgeDevice, BridgeTransfer, ConnectionStatusPayload } from "../types";
import {
  Smartphone,
  Share2,
  Copy,
  Check,
  Zap,
  UploadCloud,
  FileText,
  Clock,
  Shield,
  FolderOpen,
} from "lucide-react";

export const LiveBridgeView: React.FC = () => {
  const [devices, setDevices] = useState<BridgeDevice[]>([]);
  const [activities, setActivities] = useState<BridgeActivity[]>([]);
  const [transfers, setTransfers] = useState<BridgeTransfer[]>([]);
  const [connStatus, setConnStatus] = useState<ConnectionStatusPayload | null>(null);
  const [clipboardSync, setClipboardSync] = useState<boolean>(true);
  const [lastCopied] = useState<string>("Google Security OTP: 849-201");
  const [hasCopied, setHasCopied] = useState<boolean>(false);
  const [isSendingFile, setIsSendingFile] = useState<boolean>(false);
  const [fileProgress, setFileProgress] = useState<number>(0);
  const [activeSubTab, setActiveSubTab] = useState<"overview" | "files" | "activities">("overview");
  const [isDragOver, setIsDragOver] = useState<boolean>(false);
  const [activeFileName, setActiveFileName] = useState<string>("Project_Assets.zip");
  const fileInputRef = React.useRef<HTMLInputElement | null>(null);

  useEffect(() => {
    // Fetch bridge devices & connection status from Rust backend
    invoke<BridgeDevice[]>("jarvis_get_bridge_devices")
      .then((d) => setDevices(d || []))
      .catch(console.error);

    invoke<ConnectionStatusPayload>("jarvis_get_connection_status")
      .then(setConnStatus)
      .catch(console.error);

    invoke<BridgeActivity[]>("jarvis_get_bridge_activities")
      .then((a) => setActivities(a || []))
      .catch(console.error);

    invoke<BridgeTransfer[]>("jarvis_get_bridge_transfers")
      .then((t) => setTransfers(t || []))
      .catch(console.error);

    // Listen to real-time bridge events
    let unlistenDev: (() => void) | undefined;
    listen<BridgeDevice[]>("jarvis://bridge-devices", (e) => {
      if (e.payload) setDevices(e.payload);
    }).then((fn) => {
      unlistenDev = fn;
    });

    let unlistenTransfer: (() => void) | undefined;
    listen<BridgeTransfer[]>("jarvis://bridge-transfer", (e) => {
      if (e.payload) setTransfers(e.payload);
    }).then((fn) => {
      unlistenTransfer = fn;
    });

    return () => {
      if (unlistenDev) unlistenDev();
      if (unlistenTransfer) unlistenTransfer();
    };
  }, []);

  const activeDevice = devices[0];

  const sendFileObject = (name: string, size: number) => {
    setActiveFileName(name);
    setIsSendingFile(true);
    setFileProgress(15);
    const int = setInterval(() => {
      setFileProgress((prev) => {
        if (prev >= 100) {
          clearInterval(int);
          setIsSendingFile(false);
          invoke("jarvis_send_bridge_file", {
            deviceName: activeDevice?.name || "Galaxy S24 Ultra",
            fileName: name,
            fileSize: size,
          }).catch(console.error);
          return 100;
        }
        return prev + 25;
      });
    }, 120);
  };

  const handleSimulateSend = () => {
    sendFileObject("Project_Assets.zip", 48200000);
  };

  const handleOpenFolder = () => {
    invoke("jarvis_open_transfer_folder").catch(console.error);
  };

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files.length > 0) {
      const file = e.target.files[0];
      sendFileObject(file.name, file.size);
    }
  };

  const handleCopyOtp = (textToCopy: string = "849201") => {
    navigator.clipboard.writeText(textToCopy);
    setHasCopied(true);
    setTimeout(() => setHasCopied(false), 2000);
  };

  return (
    <div
      style={{
        display: "flex",
        flexDirection: "column",
        width: "100%",
        height: "100%",
        gap: "10px",
        overflow: "hidden",
        userSelect: "none",
      }}
    >
      {/* ── Top Device Bar ── */}
      <div
        style={{
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
          background: "rgba(255, 255, 255, 0.04)",
          border: "1px solid rgba(255, 255, 255, 0.08)",
          borderRadius: "14px",
          padding: "8px 14px",
          flexShrink: 0,
        }}
      >
        <div style={{ display: "flex", alignItems: "center", gap: "10px" }}>
          <div
            style={{
              width: "32px",
              height: "32px",
              borderRadius: "10px",
              background: "rgba(56, 189, 248, 0.15)",
              border: "1px solid rgba(56, 189, 248, 0.3)",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              color: "#38bdf8",
            }}
          >
            <Smartphone size={17} />
          </div>
          <div>
            <div style={{ display: "flex", alignItems: "center", gap: "6px" }}>
              <span style={{ fontSize: "12.5px", fontWeight: 700, color: "#ffffff" }}>
                {activeDevice ? activeDevice.name : "JARVIS Phone"}
              </span>
              <span
                style={{
                  fontSize: "9px",
                  fontWeight: 700,
                  padding: "1px 6px",
                  borderRadius: "9999px",
                  background:
                    connStatus?.state === "reconnecting"
                      ? "rgba(245, 158, 11, 0.2)"
                      : "rgba(16, 185, 129, 0.2)",
                  color:
                    connStatus?.state === "reconnecting"
                      ? "#f59e0b"
                      : "#10b981",
                  border: "1px solid currentColor",
                }}
              >
                {connStatus?.state ? connStatus.state.toUpperCase() : "CONNECTED"} • {connStatus?.pingMs || 12}ms
              </span>
            </div>
            <div style={{ fontSize: "10.5px", color: "rgba(255, 255, 255, 0.5)" }}>
              {activeDevice ? activeDevice.wifiSsid : "JARVIS-Mesh-5G"} • v1.0.0 • IP:{" "}
              {activeDevice ? activeDevice.ip : "192.168.1.145"}
            </div>
          </div>
        </div>

        <div style={{ display: "flex", alignItems: "center", gap: "14px" }}>
          {/* Battery pill */}
          <div
            style={{
              display: "flex",
              alignItems: "center",
              gap: "4px",
              fontSize: "11.5px",
              fontWeight: 700,
              color: "#ffffff",
              background: "rgba(255, 255, 255, 0.06)",
              padding: "4px 8px",
              borderRadius: "8px",
            }}
          >
            <Zap size={11} color="#10b981" fill="#10b981" />
            <span>{activeDevice ? activeDevice.batteryPercent : 88}%</span>
          </div>

          {/* Subtabs */}
          <div
            style={{
              display: "flex",
              gap: "3px",
              background: "rgba(0, 0, 0, 0.3)",
              padding: "2px",
              borderRadius: "8px",
            }}
          >
            {(["overview", "files", "activities"] as const).map((tab) => {
              const active = activeSubTab === tab;
              return (
                <button
                  key={tab}
                  onClick={() => setActiveSubTab(tab)}
                  style={{
                    padding: "3px 9px",
                    borderRadius: "6px",
                    border: "none",
                    cursor: "pointer",
                    fontSize: "10.5px",
                    fontWeight: active ? 700 : 500,
                    background: active ? "rgba(255, 255, 255, 0.15)" : "transparent",
                    color: active ? "#ffffff" : "rgba(255, 255, 255, 0.5)",
                    transition: "all 0.15s ease",
                  }}
                >
                  {tab.charAt(0).toUpperCase() + tab.slice(1)}
                </button>
              );
            })}
          </div>
        </div>
      </div>

      {/* ── Tab Content ── */}
      <div style={{ flex: 1, minHeight: 0, overflowY: "auto", display: "flex", flexDirection: "column", gap: "10px" }}>
        {activeSubTab === "overview" && (
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "10px", height: "100%" }}>
            {/* Left Card: Universal AirDrop / File Drop */}
            <div
              onDragOver={(e) => {
                e.preventDefault();
                setIsDragOver(true);
              }}
              onDragLeave={(e) => {
                e.preventDefault();
                setIsDragOver(false);
              }}
              onDrop={(e) => {
                e.preventDefault();
                setIsDragOver(false);
                if (e.dataTransfer.files && e.dataTransfer.files.length > 0) {
                  const f = e.dataTransfer.files[0];
                  sendFileObject(f.name, f.size);
                }
              }}
              style={{
                display: "flex",
                flexDirection: "column",
                justifyContent: "space-between",
                background: isDragOver ? "rgba(56, 189, 248, 0.1)" : "rgba(255, 255, 255, 0.03)",
                border: isDragOver ? "2px dashed #38bdf8" : "1px dashed rgba(56, 189, 248, 0.35)",
                boxShadow: isDragOver ? "0 0 20px rgba(56, 189, 248, 0.25)" : "none",
                borderRadius: "14px",
                padding: "14px",
                transition: "all 0.15s ease",
              }}
            >
              <input
                type="file"
                ref={fileInputRef}
                onChange={handleFileChange}
                style={{ display: "none" }}
              />
              <div>
                <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: "6px" }}>
                  <div style={{ display: "flex", alignItems: "center", gap: "7px" }}>
                    <Share2 size={15} color="#38bdf8" />
                    <span style={{ fontSize: "12.5px", fontWeight: 700, color: "#ffffff" }}>
                      Universal AirDrop & Beamer
                    </span>
                  </div>
                  <button
                    onClick={handleOpenFolder}
                    title="Open JARVIS Downloads folder"
                    style={{
                      background: "rgba(255, 255, 255, 0.08)",
                      border: "none",
                      borderRadius: "6px",
                      padding: "3px 6px",
                      color: "#38bdf8",
                      display: "flex",
                      alignItems: "center",
                      gap: "4px",
                      fontSize: "10px",
                      cursor: "pointer",
                      fontWeight: 600,
                    }}
                  >
                    <FolderOpen size={11} />
                    <span>Open Folder</span>
                  </button>
                </div>
                <div style={{ fontSize: "11px", color: "rgba(255, 255, 255, 0.5)", lineHeight: 1.4 }}>
                  {isDragOver
                    ? "Release to beam this file to your phone immediately!"
                    : "Drag files here or onto the Island to beam them over local 5G WiFi at 100+ MB/s."}
                </div>
              </div>

              {isSendingFile ? (
                <div style={{ display: "flex", flexDirection: "column", gap: "6px", margin: "10px 0" }}>
                  <div style={{ display: "flex", justifyContent: "space-between", fontSize: "11px", color: "#38bdf8", fontWeight: 600 }}>
                    <span style={{ maxWidth: "160px", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
                      Streaming {activeFileName}...
                    </span>
                    <span>{fileProgress}%</span>
                  </div>
                  <div style={{ width: "100%", height: "5px", background: "rgba(255, 255, 255, 0.1)", borderRadius: "3px", overflow: "hidden" }}>
                    <div style={{ width: `${fileProgress}%`, height: "100%", background: "#38bdf8", transition: "width 0.15s ease" }} />
                  </div>
                </div>
              ) : (
                <div style={{ display: "flex", gap: "6px" }}>
                  <button
                    onClick={() => fileInputRef.current?.click()}
                    style={{
                      flex: 1,
                      display: "flex",
                      alignItems: "center",
                      justifyContent: "center",
                      gap: "6px",
                      padding: "8px 10px",
                      borderRadius: "9px",
                      border: "1px solid rgba(56, 189, 248, 0.3)",
                      background: "rgba(56, 189, 248, 0.15)",
                      color: "#38bdf8",
                      fontSize: "11px",
                      fontWeight: 700,
                      cursor: "pointer",
                      transition: "all 0.15s ease",
                    }}
                  >
                    <UploadCloud size={13} />
                    <span>Choose File</span>
                  </button>
                  <button
                    onClick={handleSimulateSend}
                    style={{
                      padding: "8px 10px",
                      borderRadius: "9px",
                      border: "1px solid rgba(255, 255, 255, 0.1)",
                      background: "rgba(255, 255, 255, 0.05)",
                      color: "rgba(255, 255, 255, 0.7)",
                      fontSize: "11px",
                      fontWeight: 600,
                      cursor: "pointer",
                      transition: "all 0.15s ease",
                    }}
                  >
                    Quick Sample
                  </button>
                </div>
              )}
            </div>

            {/* Right Card: Real-Time Universal Clipboard */}
            <div
              style={{
                display: "flex",
                flexDirection: "column",
                justifyContent: "space-between",
                background: "rgba(255, 255, 255, 0.03)",
                border: "1px solid rgba(255, 255, 255, 0.08)",
                borderRadius: "14px",
                padding: "14px",
              }}
            >
              <div>
                <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: "6px" }}>
                  <div style={{ display: "flex", alignItems: "center", gap: "7px" }}>
                    <Copy size={15} color="#10b981" />
                    <span style={{ fontSize: "12.5px", fontWeight: 700, color: "#ffffff" }}>
                      Universal Clipboard
                    </span>
                  </div>
                  <button
                    onClick={() => setClipboardSync(!clipboardSync)}
                    style={{
                      background: clipboardSync ? "rgba(16, 185, 129, 0.2)" : "rgba(255, 255, 255, 0.08)",
                      border: "1px solid rgba(16, 185, 129, 0.3)",
                      borderRadius: "6px",
                      padding: "2px 6px",
                      fontSize: "10px",
                      color: clipboardSync ? "#10b981" : "rgba(255, 255, 255, 0.4)",
                      cursor: "pointer",
                      fontWeight: 700,
                    }}
                  >
                    {clipboardSync ? "2-WAY ACTIVE" : "PAUSED"}
                  </button>
                </div>
                <div style={{ fontSize: "11px", color: "rgba(255, 255, 255, 0.5)", marginBottom: "8px" }}>
                  Text copied on mobile is instantaneously mirrored in the island clipboard.
                </div>
                <div
                  style={{
                    padding: "8px 10px",
                    borderRadius: "8px",
                    background: "rgba(0, 0, 0, 0.35)",
                    border: "1px solid rgba(255, 255, 255, 0.06)",
                    fontSize: "11.5px",
                    color: "#f1f5f9",
                    fontFamily: "monospace",
                    overflow: "hidden",
                    textOverflow: "ellipsis",
                    whiteSpace: "nowrap",
                  }}
                >
                  {lastCopied}
                </div>
              </div>

              <button
                onClick={() => handleCopyOtp(lastCopied)}
                style={{
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "center",
                  gap: "6px",
                  width: "100%",
                  padding: "8px 12px",
                  borderRadius: "9px",
                  border: "none",
                  background: hasCopied ? "rgba(16, 185, 129, 0.2)" : "rgba(255, 255, 255, 0.08)",
                  color: hasCopied ? "#10b981" : "#ffffff",
                  fontSize: "11.5px",
                  fontWeight: 700,
                  cursor: "pointer",
                }}
              >
                {hasCopied ? <Check size={14} /> : <Copy size={14} />}
                <span>{hasCopied ? "Copied to Desktop!" : "Copy OTP to Desktop"}</span>
              </button>
            </div>
          </div>
        )}

        {activeSubTab === "activities" && (
          <div style={{ display: "flex", flexDirection: "column", gap: "8px" }}>
            {activities.map((act) => (
              <div
                key={act.id}
                style={{
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "space-between",
                  padding: "10px 14px",
                  borderRadius: "12px",
                  background: "rgba(255, 255, 255, 0.03)",
                  border: "1px solid rgba(255, 255, 255, 0.08)",
                }}
              >
                <div style={{ display: "flex", alignItems: "center", gap: "12px" }}>
                  <div
                    style={{
                      width: "30px",
                      height: "30px",
                      borderRadius: "8px",
                      background: act.activityType === "otp" ? "rgba(245, 158, 11, 0.15)" : "rgba(56, 189, 248, 0.15)",
                      display: "flex",
                      alignItems: "center",
                      justifyContent: "center",
                      color: act.activityType === "otp" ? "#f59e0b" : "#38bdf8",
                    }}
                  >
                    {act.activityType === "otp" ? <Shield size={16} /> : <Clock size={16} />}
                  </div>
                  <div>
                    <div style={{ fontSize: "12px", fontWeight: 700, color: "#ffffff" }}>
                      {act.title}
                    </div>
                    <div style={{ fontSize: "11px", color: "rgba(255, 255, 255, 0.5)" }}>
                      {act.subtitle}
                    </div>
                  </div>
                </div>

                {act.activityType === "otp" && (
                  <button
                    onClick={() => handleCopyOtp("849201")}
                    style={{
                      padding: "4px 10px",
                      borderRadius: "6px",
                      border: "none",
                      background: "#38bdf8",
                      color: "#000000",
                      fontSize: "11px",
                      fontWeight: 700,
                      cursor: "pointer",
                    }}
                  >
                    Copy OTP
                  </button>
                )}
              </div>
            ))}
          </div>
        )}

        {activeSubTab === "files" && (
          <div style={{ display: "flex", flexDirection: "column", gap: "8px" }}>
            <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "2px 4px" }}>
              <span style={{ fontSize: "11.5px", color: "rgba(255, 255, 255, 0.6)" }}>
                Recent Cross-Device File Beam History
              </span>
              <button
                onClick={handleOpenFolder}
                style={{
                  display: "flex",
                  alignItems: "center",
                  gap: "4px",
                  background: "rgba(255, 255, 255, 0.08)",
                  border: "none",
                  borderRadius: "6px",
                  padding: "3px 8px",
                  color: "#38bdf8",
                  fontSize: "10.5px",
                  cursor: "pointer",
                  fontWeight: 600,
                }}
              >
                <FolderOpen size={12} />
                <span>Open Downloads Folder</span>
              </button>
            </div>
            {transfers.length === 0 ? (
              <div
                style={{
                  padding: "20px",
                  textAlign: "center",
                  fontSize: "11px",
                  color: "rgba(255, 255, 255, 0.4)",
                  background: "rgba(255, 255, 255, 0.02)",
                  borderRadius: "10px",
                }}
              >
                No active transfers. Drag & drop files onto the island to beam.
              </div>
            ) : (
              transfers.map((t) => (
                <div
                  key={t.id}
                  style={{
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "space-between",
                    padding: "8px 12px",
                    borderRadius: "10px",
                    background: "rgba(255, 255, 255, 0.03)",
                    border: "1px solid rgba(255, 255, 255, 0.06)",
                  }}
                >
                  <div style={{ display: "flex", alignItems: "center", gap: "10px" }}>
                    <FileText size={16} color="#38bdf8" />
                    <div>
                      <div style={{ fontSize: "12px", fontWeight: 600, color: "#ffffff" }}>
                        {t.fileName}
                      </div>
                      <div style={{ fontSize: "10px", color: "rgba(255, 255, 255, 0.4)" }}>
                        {(t.fileSize / (1024 * 1024)).toFixed(1)} MB • {t.deviceName}
                      </div>
                    </div>
                  </div>
                  <span style={{ fontSize: "10px", fontWeight: 700, color: "#10b981" }}>
                    COMPLETED
                  </span>
                </div>
              ))
            )}
          </div>
        )}
      </div>
    </div>
  );
};
