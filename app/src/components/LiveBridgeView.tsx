import React, { useState, useEffect } from "react";
import { invoke } from "@tauri-apps/api/core";
import { listen } from "@tauri-apps/api/event";
import QRCode from "qrcode";
import { BridgeActivity, BridgeDevice, BridgeTransfer } from "../types";
import {
  Smartphone,
  QrCode,
  Share2,
  Copy,
  Check,
  Zap,
  UploadCloud,
  FileText,
  Clock,
  Shield,
  FolderOpen,
  MousePointer,
  ChevronLeft,
  Circle,
  Square,
  Bell,
  Lock,
  Volume2,
  VolumeX,
  Play,
  SkipForward,
  SkipBack,
  ArrowUp,
  ArrowDown,
  ArrowLeft,
  ArrowRight,
} from "lucide-react";

export const LiveBridgeView: React.FC = () => {
  const [devices, setDevices] = useState<BridgeDevice[]>([]);
  const [activities, setActivities] = useState<BridgeActivity[]>([]);
  const [transfers, setTransfers] = useState<BridgeTransfer[]>([]);
  const [lastCopied] = useState<string>("Ecosystem Clipboard Sync Active");
  const [hasCopied, setHasCopied] = useState<boolean>(false);
  const [isSendingFile, setIsSendingFile] = useState<boolean>(false);
  const [fileProgress, setFileProgress] = useState<number>(0);
  const [isDragOver, setIsDragOver] = useState<boolean>(false);
  const [activeFileName, setActiveFileName] = useState<string>("Project_Assets.zip");
  const fileInputRef = React.useRef<HTMLInputElement | null>(null);
  const [qrPairing, setQrPairing] = useState<{
    dataUrl: string;
    pin: string;
    expiresAtMs: number;
  } | null>(null);
  const [qrLoading, setQrLoading] = useState<boolean>(false);

  useEffect(() => {
    // Fetch bridge devices & connection status from Rust backend
    invoke<BridgeDevice[]>("jarvis_get_bridge_devices")
      .then((d) => setDevices(d || []))
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
    listen<BridgeTransfer[]>("jarvis://bridge-transfers", (e) => {
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
  const connected =
    !!activeDevice &&
    (activeDevice.status === "connected" || activeDevice.status === "syncing");

  const showQrCode = async () => {
    setQrLoading(true);
    try {
      const result = await invoke<{
        payload: string;
        pin: string;
        expiresAtMs: number;
      }>("jarvis_create_qr_pairing");
      const dataUrl = await QRCode.toDataURL(result.payload, {
        margin: 2,
        width: 520,
        color: { dark: "#000000", light: "#ffffff" },
      });
      setQrPairing({ dataUrl, pin: result.pin, expiresAtMs: result.expiresAtMs });
    } catch (error) {
      console.error("QR pairing error:", error);
    } finally {
      setQrLoading(false);
    }
  };

  useEffect(() => {
    if (!qrPairing) return;
    const remaining = Math.max(0, qrPairing.expiresAtMs - Date.now());
    const timer = window.setTimeout(() => setQrPairing(null), remaining);
    return () => window.clearTimeout(timer);
  }, [qrPairing]);

  useEffect(() => {
    if (connected) setQrPairing(null);
  }, [connected]);

  const sendRealFile = async (file: File) => {
    setActiveFileName(file.name);
    setIsSendingFile(true);
    setFileProgress(20);

    const filePath = (file as any).path;
    if (filePath) {
      setFileProgress(60);
      try {
        await invoke("jarvis_send_bridge_file_path", { filePath });
        setFileProgress(100);
      } catch (err) {
        console.error("Beam error:", err);
      } finally {
        setTimeout(() => setIsSendingFile(false), 500);
      }
      return;
    }

    const reader = new FileReader();
    reader.onload = async () => {
      setFileProgress(70);
      const b64 = (reader.result as string).split(",")[1];
      try {
        await invoke("jarvis_send_bridge_file_bytes", {
          deviceName: activeDevice?.name || "Companion Device",
          fileName: file.name,
          fileSize: file.size,
          dataBase64: b64,
        });
        setFileProgress(100);
      } catch (err) {
        console.error("Beam bytes error:", err);
      } finally {
        setTimeout(() => setIsSendingFile(false), 500);
      }
    };
    reader.readAsDataURL(file);
  };

  const handleOpenFolder = () => {
    invoke("jarvis_open_transfer_folder").catch(console.error);
  };

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      sendRealFile(file);
    }
    // Allow re-selecting the same file in a later transfer
    e.target.value = "";
  };

  const handleCopyOtp = (textToCopy: string = "849201") => {
    navigator.clipboard.writeText(textToCopy);
    setHasCopied(true);
    setTimeout(() => setHasCopied(false), 2000);
  };

  const [controlFeedback, setControlFeedback] = useState<string | null>(null);
  const [trackpadPos, setTrackpadPos] = useState<{ x: number; y: number } | null>(null);

  const handleSendControl = async (
    action: string,
    x?: number,
    y?: number,
    deltaX?: number,
    deltaY?: number
  ) => {
    try {
      setControlFeedback(action.toUpperCase().replace("_", " "));
      setTimeout(() => setControlFeedback(null), 1200);
      await invoke("jarvis_send_mobile_control", {
        action,
        x,
        y,
        deltaX,
        deltaY,
        targetIp: activeDevice?.ip || undefined,
      });
    } catch (e) {
      console.error("Control error:", e);
    }
  };

  const [pairing, setPairing] = useState<{
    pin: string;
    clientDeviceName: string;
    expiresAtMs: number;
  } | null>(null);
  const [tlsFingerprint, setTlsFingerprint] = useState<string>("");

  useEffect(() => {
    invoke<string>("jarvis_get_bridge_tls_fingerprint")
      .then((fp) => setTlsFingerprint((fp || "").slice(0, 8)))
      .catch(console.error);

    invoke<{ pin: string; clientDeviceName: string; expiresAtMs: number } | null>(
      "jarvis_get_pairing_challenge"
    )
      .then((challenge) => {
        if (challenge?.pin) setPairing(challenge);
      })
      .catch(console.error);

    let unlistenPairing: (() => void) | undefined;
    listen<{ pin: string; clientDeviceName: string; expiresAtMs: number }>(
      "jarvis://bridge-pairing-request",
      (e) => {
        if (e.payload?.pin) setPairing(e.payload);
      }
    ).then((fn) => {
      unlistenPairing = fn;
    });
    return () => {
      if (unlistenPairing) unlistenPairing();
    };
  }, []);

  useEffect(() => {
    if (!pairing) return;
    const remaining = Math.max(0, pairing.expiresAtMs - Date.now());
    const timer = window.setTimeout(() => setPairing(null), remaining);
    return () => window.clearTimeout(timer);
  }, [pairing]);

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
      {pairing && !qrPairing && (
        <div
          style={{
            display: "flex",
            alignItems: "center",
            gap: "10px",
            background: "rgba(255, 159, 10, 0.12)",
            border: "1px solid rgba(255, 159, 10, 0.4)",
            borderRadius: "14px",
            padding: "10px 14px",
            flexShrink: 0,
          }}
        >
          <Shield size={18} color="#ff9f0a" />
          <div style={{ lineHeight: 1.35 }}>
            <div style={{ fontSize: "12px", fontWeight: 600, color: "#ff9f0a" }}>
              Pairing request from {pairing.clientDeviceName || "Companion Device"}
            </div>
            <div style={{ fontSize: "11.5px", color: "rgba(255, 255, 255, 0.75)" }}>
              Enter PIN{" "}
              <span style={{ fontWeight: 600, letterSpacing: "3px", color: "#ffffff" }}>
                {pairing.pin}
              </span>{" "}
              on the device within 2 minutes.
              {tlsFingerprint && (
                <span style={{ opacity: 0.6 }}>
                  {" "}
                  Certificate fingerprint: {tlsFingerprint}…
                </span>
              )}
            </div>
          </div>
        </div>
      )}

      {!connected && (
        <div
          style={{
            display: "flex",
            flexDirection: qrPairing ? "column" : "row",
            alignItems: qrPairing ? "stretch" : "center",
            gap: qrPairing ? "10px" : "14px",
            background: "rgba(255, 255, 255, 0.055)",
            borderRadius: "16px",
            padding: "12px 14px",
            flexShrink: 0,
          }}
        >
          {qrPairing ? (
            <>
              <img
                src={qrPairing.dataUrl}
                alt="Pairing QR code"
                style={{
                  width: "188px",
                  height: "188px",
                  alignSelf: "center",
                  borderRadius: "10px",
                  background: "#ffffff",
                  padding: "8px",
                  flexShrink: 0,
                }}
              />
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ fontSize: "13px", fontWeight: 600, color: "#f5f5f7" }}>
                  Scan with the JARVIS app
                </div>
                <div
                  style={{
                    fontSize: "11.5px",
                    color: "rgba(235, 235, 245, 0.6)",
                    lineHeight: 1.4,
                    marginTop: "3px",
                  }}
                >
                  On the phone open Home → Desktop → Scan QR code. The code is valid for 2 minutes.
                </div>
                <div
                  style={{
                    fontSize: "11px",
                    color: "rgba(235, 235, 245, 0.55)",
                    marginTop: "4px",
                  }}
                >
                  Or enter PIN <b style={{ color: "#f5f5f7", letterSpacing: "2px" }}>{qrPairing.pin}</b>
                </div>
                <button
                  onClick={showQrCode}
                  disabled={qrLoading}
                  style={{
                    marginTop: "6px",
                    background: "none",
                    border: "none",
                    padding: 0,
                    color: "#0a84ff",
                    fontSize: "12px",
                    fontWeight: 500,
                    cursor: "pointer",
                  }}
                >
                  {qrLoading ? "Preparing…" : "New code"}
                </button>
              </div>
            </>
          ) : (
            <>
              <div
                style={{
                  width: "30px",
                  height: "30px",
                  borderRadius: "8px",
                  background: "rgba(120, 120, 128, 0.16)",
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "center",
                  color: "#0a84ff",
                  flexShrink: 0,
                }}
              >
                <QrCode size={16} strokeWidth={1.7} />
              </div>
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ fontSize: "13px", fontWeight: 600, color: "#f5f5f7" }}>
                  Pair a phone
                </div>
                <div style={{ fontSize: "11.5px", color: "rgba(235, 235, 245, 0.6)", marginTop: "2px" }}>
                  Show a one-time QR code. Scanning connects without typing a PIN.
                </div>
              </div>
              <button
                onClick={showQrCode}
                disabled={qrLoading}
                style={{
                  height: "30px",
                  padding: "0 12px",
                  borderRadius: "8px",
                  border: "none",
                  background: "#0a84ff",
                  color: "#ffffff",
                  fontSize: "12px",
                  fontWeight: 600,
                  cursor: qrLoading ? "wait" : "pointer",
                  flexShrink: 0,
                }}
              >
                {qrLoading ? "Preparing…" : "Show QR"}
              </button>
            </>
          )}
        </div>
      )}

      {/* ── Top Device Bar ── */}
      <div
        style={{
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
          background: "rgba(255, 255, 255, 0.055)",
          borderRadius: "14px",
          padding: "12px 14px",
          flexShrink: 0,
        }}
      >
        <div style={{ display: "flex", alignItems: "center", gap: "12px" }}>
          <div
            style={{
              width: "30px",
              height: "30px",
              borderRadius: "8px",
              background: "rgba(120, 120, 128, 0.16)",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              color: connected ? "#0a84ff" : "rgba(235, 235, 245, 0.55)",
            }}
          >
            <Smartphone size={16} strokeWidth={1.6} />
          </div>
          <div>
            <div style={{ display: "flex", alignItems: "center", gap: "7px" }}>
              <span style={{ fontSize: "13px", fontWeight: 600, letterSpacing: "-0.01em", color: "#f5f5f7" }}>
                {activeDevice ? activeDevice.name : "No Device Paired"}
              </span>
              <div
                style={{
                  width: "6px",
                  height: "6px",
                  borderRadius: "50%",
                  background: connected ? "#30d158" : "rgba(235, 235, 245, 0.35)",
                }}
              />
              <span
                style={{
                  fontSize: "11px",
                  fontWeight: 500,
                  color: connected ? "#30d158" : "rgba(235, 235, 245, 0.55)",
                }}
              >
                {connected ? "Connected" : "Not connected"}
              </span>
            </div>
            <div style={{ fontSize: "11px", color: "rgba(235, 235, 245, 0.55)", letterSpacing: "0.01em" }}>
              {connected ? "Continuity & AirDrop active" : "Pair from the JARVIS Android app"}
            </div>
          </div>
        </div>

        {/* Right Status Pill */}
        <div
          style={{
            display: "flex",
            alignItems: "center",
            gap: "5px",
            fontSize: "11px",
            fontWeight: 600,
            fontVariantNumeric: "tabular-nums",
            color: "rgba(235, 235, 245, 0.72)",
            background: "rgba(120, 120, 128, 0.16)",
            padding: "4px 9px",
            borderRadius: "7px",
          }}
        >
          <Zap size={11} strokeWidth={1.8} color={connected ? "#30d158" : "rgba(235, 235, 245, 0.45)"} />
          <span>{activeDevice ? `${activeDevice.batteryPercent}%` : "—"}</span>
        </div>
      </div>

      {/* ── Main Ecosystem Cards ── */}
      <div style={{ flex: 1, minHeight: 0, overflowY: "auto", display: "flex", flexDirection: "column", gap: "10px" }}>
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "10px", height: "100%" }}>
          {/* Left Card: AirDrop */}
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
                sendRealFile(e.dataTransfer.files[0]);
              }
            }}
            style={{
              display: "flex",
              flexDirection: "column",
              justifyContent: "space-between",
              background: isDragOver ? "rgba(10, 132, 255, 0.12)" : "rgba(255, 255, 255, 0.055)",
              border: isDragOver ? "1.5px dashed #0a84ff" : "1.5px dashed rgba(120, 120, 128, 0.35)",
              borderRadius: "16px",
              padding: "14px",
              transition: "background var(--duration-quick) var(--ease-out), border-color var(--duration-quick) var(--ease-out)",
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
                  <Share2 size={15} strokeWidth={1.7} color="#0a84ff" />
                  <span style={{ fontSize: "13px", fontWeight: 600, letterSpacing: "-0.01em", color: "#f5f5f7" }}>
                    AirDrop
                  </span>
                </div>
                <button
                  onClick={handleOpenFolder}
                  title="Open Received Folder"
                  style={{
                    background: "rgba(120, 120, 128, 0.16)",
                    border: "none",
                    borderRadius: "6px",
                    padding: "3px 7px",
                    color: "rgba(235, 235, 245, 0.8)",
                    display: "flex",
                    alignItems: "center",
                    gap: "4px",
                    fontSize: "11px",
                    cursor: "pointer",
                    fontWeight: 500,
                  }}
                >
                  <FolderOpen size={11} strokeWidth={1.7} />
                  <span>Received</span>
                </button>
              </div>
              <div style={{ fontSize: "11.5px", color: "rgba(235, 235, 245, 0.6)", lineHeight: 1.45 }}>
                {isDragOver
                  ? "Drop to send to your phone."
                  : "Drag and drop files to beam them to your phone."}
              </div>
            </div>

            {isSendingFile ? (
              <div style={{ display: "flex", flexDirection: "column", gap: "6px", margin: "10px 0" }}>
                <div style={{ display: "flex", justifyContent: "space-between", fontSize: "11px", color: "#0a84ff", fontWeight: 600 }}>
                  <span style={{ maxWidth: "160px", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
                    Sending {activeFileName}...
                  </span>
                  <span>{fileProgress}%</span>
                </div>
                <div style={{ width: "100%", height: "5px", background: "rgba(255, 255, 255, 0.1)", borderRadius: "3px", overflow: "hidden" }}>
                  <div style={{ width: `${fileProgress}%`, height: "100%", background: "#0a84ff", transition: "width 0.15s ease" }} />
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
                    height: "30px",
                    padding: "0 12px",
                    borderRadius: "8px",
                    border: "none",
                    background: "#0a84ff",
                    color: "#ffffff",
                    fontSize: "12px",
                    fontWeight: 600,
                    cursor: "pointer",
                    transition: "transform var(--duration-quick) var(--ease-out), filter var(--duration-quick) var(--ease-out)",
                  }}
                >
                  <UploadCloud size={13} strokeWidth={1.8} />
                  <span>Choose File</span>
                </button>
              </div>
            )}
          </div>

          {/* Right Card: Universal Clipboard */}
          <div
            style={{
              display: "flex",
              flexDirection: "column",
              justifyContent: "space-between",
              background: "rgba(255, 255, 255, 0.055)",
              borderRadius: "16px",
              padding: "14px",
            }}
          >
            <div>
              <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: "6px" }}>
                <div style={{ display: "flex", alignItems: "center", gap: "7px" }}>
                  <Copy size={15} strokeWidth={1.7} color="#30d158" />
                  <span style={{ fontSize: "13px", fontWeight: 600, letterSpacing: "-0.01em", color: "#f5f5f7" }}>
                    Universal Clipboard
                  </span>
                </div>
              </div>
              <div style={{ fontSize: "11.5px", color: "rgba(235, 235, 245, 0.6)", lineHeight: 1.45, marginBottom: "8px" }}>
                Text copied on your phone is automatically available on your PC.
              </div>
              <div
                style={{
                  padding: "8px 10px",
                  borderRadius: "8px",
                  background: "rgba(0, 0, 0, 0.28)",
                  fontSize: "11.5px",
                  color: "rgba(235, 235, 245, 0.85)",
                  fontFamily: "inherit",
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
                background: hasCopied ? "rgba(48, 209, 88, 0.2)" : "rgba(255, 255, 255, 0.08)",
                color: hasCopied ? "#30d158" : "#ffffff",
                fontSize: "11.5px",
                fontWeight: 600,
                cursor: "pointer",
                transition: "all 0.15s ease",
              }}
            >
              {hasCopied ? <Check size={14} /> : <Copy size={14} />}
              <span>{hasCopied ? "Copied!" : "Copy to PC"}</span>
            </button>
          </div>
        </div>

        {/* Universal Control: Remote Pointer, Gestures & Mobile Display Control */}
        <div
          style={{
            display: "flex",
            flexDirection: "column",
            gap: "10px",
            background: "rgba(255, 255, 255, 0.055)",
            border: "0.5px solid rgba(255, 255, 255, 0.08)",
            borderRadius: "14px",
            padding: "12px 14px",
          }}
        >
          <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between" }}>
            <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
              <MousePointer size={15} color="#0a84ff" />
              <span style={{ fontSize: "12.5px", fontWeight: 600, color: "#ffffff" }}>
                Universal Control & Remote Pointer
              </span>
            </div>
            {controlFeedback && (
              <span
                style={{
                  fontSize: "11px",
                  fontWeight: 600,
                  padding: "2px 8px",
                  borderRadius: "9999px",
                  background: "rgba(10, 132, 255, 0.2)",
                  color: "#0a84ff",
                  letterSpacing: "0.5px",
                }}
              >
                {controlFeedback}
              </span>
            )}
          </div>

          <div style={{ display: "grid", gridTemplateColumns: "1.4fr 1fr", gap: "10px" }}>
            {/* Interactive Screen Trackpad / Pointer Area */}
            <div
              onMouseMove={(e) => {
                const rect = e.currentTarget.getBoundingClientRect();
                setTrackpadPos({ x: e.clientX - rect.left, y: e.clientY - rect.top });
              }}
              onMouseLeave={() => setTrackpadPos(null)}
              onClick={(e) => {
                const rect = e.currentTarget.getBoundingClientRect();
                const x = (e.clientX - rect.left) / rect.width;
                const y = (e.clientY - rect.top) / rect.height;
                handleSendControl("tap", x, y);
              }}
              style={{
                position: "relative",
                height: "105px",
                borderRadius: "10px",
                background: "linear-gradient(135deg, rgba(0, 0, 0, 0.6), rgba(15, 23, 42, 0.4))",
                border: "1px solid rgba(255, 255, 255, 0.1)",
                cursor: "crosshair",
                overflow: "hidden",
                display: "flex",
                flexDirection: "column",
                alignItems: "center",
                justifyContent: "center",
                userSelect: "none",
              }}
            >
              {/* Virtual phone notch preview */}
              <div
                style={{
                  position: "absolute",
                  top: "6px",
                  width: "36px",
                  height: "8px",
                  borderRadius: "9999px",
                  background: "#000000",
                  border: "1px solid rgba(255, 255, 255, 0.15)",
                }}
              />

              <span style={{ fontSize: "11px", color: "rgba(255, 255, 255, 0.45)", fontWeight: 500 }}>
                Click or drag to control mobile pointer
              </span>

              {trackpadPos && (
                <div
                  style={{
                    position: "absolute",
                    left: `${trackpadPos.x}px`,
                    top: `${trackpadPos.y}px`,
                    transform: "translate(-2px, -2px)",
                    pointerEvents: "none",
                    filter: "drop-shadow(0 0 6px #0a84ff)",
                  }}
                >
                  <MousePointer size={14} color="#0a84ff" />
                </div>
              )}
            </div>

            {/* Quick Swipe & Navigation Controls */}
            <div style={{ display: "flex", flexDirection: "column", gap: "6px" }}>
              <div style={{ display: "flex", gap: "4px" }}>
                <button
                  onClick={() => handleSendControl("swipe_up")}
                  title="Swipe Up"
                  style={{
                    flex: 1,
                    padding: "6px",
                    borderRadius: "6px",
                    border: "0.5px solid rgba(255, 255, 255, 0.08)",
                    background: "rgba(255, 255, 255, 0.05)",
                    color: "#ffffff",
                    cursor: "pointer",
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                  }}
                >
                  <ArrowUp size={13} />
                </button>
                <button
                  onClick={() => handleSendControl("swipe_down")}
                  title="Swipe Down"
                  style={{
                    flex: 1,
                    padding: "6px",
                    borderRadius: "6px",
                    border: "0.5px solid rgba(255, 255, 255, 0.08)",
                    background: "rgba(255, 255, 255, 0.05)",
                    color: "#ffffff",
                    cursor: "pointer",
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                  }}
                >
                  <ArrowDown size={13} />
                </button>
                <button
                  onClick={() => handleSendControl("swipe_left")}
                  title="Swipe Left"
                  style={{
                    flex: 1,
                    padding: "6px",
                    borderRadius: "6px",
                    border: "0.5px solid rgba(255, 255, 255, 0.08)",
                    background: "rgba(255, 255, 255, 0.05)",
                    color: "#ffffff",
                    cursor: "pointer",
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                  }}
                >
                  <ArrowLeft size={13} />
                </button>
                <button
                  onClick={() => handleSendControl("swipe_right")}
                  title="Swipe Right"
                  style={{
                    flex: 1,
                    padding: "6px",
                    borderRadius: "6px",
                    border: "0.5px solid rgba(255, 255, 255, 0.08)",
                    background: "rgba(255, 255, 255, 0.05)",
                    color: "#ffffff",
                    cursor: "pointer",
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                  }}
                >
                  <ArrowRight size={13} />
                </button>
              </div>

              {/* Hardware / System Buttons */}
              <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: "4px" }}>
                <button
                  onClick={() => handleSendControl("back")}
                  title="Back"
                  style={{
                    padding: "6px 4px",
                    borderRadius: "6px",
                    border: "none",
                    background: "rgba(255, 255, 255, 0.08)",
                    color: "#cbd5e1",
                    fontSize: "11px",
                    fontWeight: 600,
                    cursor: "pointer",
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                    gap: "3px",
                  }}
                >
                  <ChevronLeft size={12} />
                  <span>Back</span>
                </button>

                <button
                  onClick={() => handleSendControl("home")}
                  title="Home"
                  style={{
                    padding: "6px 4px",
                    borderRadius: "6px",
                    border: "none",
                    background: "rgba(255, 255, 255, 0.08)",
                    color: "#cbd5e1",
                    fontSize: "11px",
                    fontWeight: 600,
                    cursor: "pointer",
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                    gap: "3px",
                  }}
                >
                  <Circle size={10} />
                  <span>Home</span>
                </button>

                <button
                  onClick={() => handleSendControl("recents")}
                  title="Recents / Apps"
                  style={{
                    padding: "6px 4px",
                    borderRadius: "6px",
                    border: "none",
                    background: "rgba(255, 255, 255, 0.08)",
                    color: "#cbd5e1",
                    fontSize: "11px",
                    fontWeight: 600,
                    cursor: "pointer",
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                    gap: "3px",
                  }}
                >
                  <Square size={10} />
                  <span>Apps</span>
                </button>
              </div>

              {/* Secondary System Actions */}
              <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "4px" }}>
                <button
                  onClick={() => handleSendControl("notifications")}
                  style={{
                    padding: "5px 6px",
                    borderRadius: "6px",
                    border: "none",
                    background: "rgba(255, 255, 255, 0.06)",
                    color: "#94a3b8",
                    fontSize: "11.5px",
                    cursor: "pointer",
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                    gap: "4px",
                  }}
                >
                  <Bell size={11} />
                  <span>Shade</span>
                </button>

                <button
                  onClick={() => handleSendControl("lock")}
                  style={{
                    padding: "5px 6px",
                    borderRadius: "6px",
                    border: "none",
                    background: "rgba(255, 255, 255, 0.06)",
                    color: "#94a3b8",
                    fontSize: "11.5px",
                    cursor: "pointer",
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                    gap: "4px",
                  }}
                >
                  <Lock size={11} />
                  <span>Lock</span>
                </button>
              </div>

              {/* Media & Volume Quick Remote */}
              <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr 1fr 1fr", gap: "4px" }}>
                <button
                  onClick={() => handleSendControl("vol_down")}
                  title="Volume Down"
                  style={{
                    padding: "5px",
                    borderRadius: "6px",
                    border: "none",
                    background: "rgba(255, 255, 255, 0.06)",
                    color: "#94a3b8",
                    cursor: "pointer",
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                  }}
                >
                  <VolumeX size={12} />
                </button>
                <button
                  onClick={() => handleSendControl("vol_up")}
                  title="Volume Up"
                  style={{
                    padding: "5px",
                    borderRadius: "6px",
                    border: "none",
                    background: "rgba(255, 255, 255, 0.06)",
                    color: "#94a3b8",
                    cursor: "pointer",
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                  }}
                >
                  <Volume2 size={12} />
                </button>
                <button
                  onClick={() => handleSendControl("media_prev")}
                  title="Previous Track"
                  style={{
                    padding: "5px",
                    borderRadius: "6px",
                    border: "none",
                    background: "rgba(255, 255, 255, 0.06)",
                    color: "#94a3b8",
                    cursor: "pointer",
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                  }}
                >
                  <SkipBack size={12} />
                </button>
                <button
                  onClick={() => handleSendControl("media_toggle")}
                  title="Play / Pause"
                  style={{
                    padding: "5px",
                    borderRadius: "6px",
                    border: "none",
                    background: "rgba(10, 132, 255, 0.2)",
                    color: "#0a84ff",
                    cursor: "pointer",
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                  }}
                >
                  <Play size={12} />
                </button>
                <button
                  onClick={() => handleSendControl("media_next")}
                  title="Next Track"
                  style={{
                    padding: "5px",
                    borderRadius: "6px",
                    border: "none",
                    background: "rgba(255, 255, 255, 0.06)",
                    color: "#94a3b8",
                    cursor: "pointer",
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                  }}
                >
                  <SkipForward size={12} />
                </button>
              </div>
            </div>
          </div>
        </div>

        {/* Live Activities / OTP notifications if any */}
        {activities.length > 0 && (
          <div style={{ display: "flex", flexDirection: "column", gap: "6px" }}>
            <span style={{ fontSize: "11px", fontWeight: 600, color: "rgba(255, 255, 255, 0.45)",  }}>
              Live Activities
            </span>
            {activities.map((act) => (
              <div
                key={act.id}
                style={{
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "space-between",
                  padding: "10px 14px",
                  borderRadius: "12px",
                  background: "rgba(255, 255, 255, 0.055)",
                  border: "0.5px solid rgba(255, 255, 255, 0.08)",
                }}
              >
                <div style={{ display: "flex", alignItems: "center", gap: "12px" }}>
                  <div
                    style={{
                      width: "30px",
                      height: "30px",
                      borderRadius: "8px",
                      background: act.activityType === "otp" ? "rgba(255, 159, 10, 0.15)" : "rgba(10, 132, 255, 0.15)",
                      display: "flex",
                      alignItems: "center",
                      justifyContent: "center",
                      color: act.activityType === "otp" ? "#ff9f0a" : "#0a84ff",
                    }}
                  >
                    {act.activityType === "otp" ? <Shield size={16} /> : <Clock size={16} />}
                  </div>
                  <div>
                    <div style={{ fontSize: "12px", fontWeight: 600, color: "#ffffff" }}>
                      {act.title}
                    </div>
                    <div style={{ fontSize: "11px", color: "rgba(255, 255, 255, 0.5)" }}>
                      {act.subtitle}
                    </div>
                  </div>
                </div>

                {act.activityType === "otp" && (
                  <button
                    onClick={() => {
                      const m = act.subtitle.match(/\b\d{4,8}\b/);
                      handleCopyOtp(m ? m[0] : act.subtitle);
                    }}
                    style={{
                      padding: "4px 10px",
                      borderRadius: "6px",
                      border: "none",
                      background: "#0a84ff",
                      color: "#000000",
                      fontSize: "11px",
                      fontWeight: 600,
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

        {/* Recent Transfers */}
        {transfers.length > 0 && (
          <div style={{ display: "flex", flexDirection: "column", gap: "6px" }}>
            <span style={{ fontSize: "11px", fontWeight: 600, color: "rgba(255, 255, 255, 0.45)",  }}>
              Recent Transfers
            </span>
            {transfers.slice(0, 3).map((t) => (
              <div
                key={t.id}
                style={{
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "space-between",
                  padding: "8px 12px",
                  borderRadius: "10px",
                  background: "rgba(255, 255, 255, 0.055)",
                  border: "0.5px solid rgba(255, 255, 255, 0.06)",
                }}
              >
                <div style={{ display: "flex", alignItems: "center", gap: "10px" }}>
                  <FileText size={15} color="#0a84ff" />
                  <div>
                    <div style={{ fontSize: "11.5px", fontWeight: 600, color: "#ffffff" }}>
                      {t.fileName}
                    </div>
                    <div style={{ fontSize: "11px", color: "rgba(255, 255, 255, 0.4)" }}>
                      {(t.fileSize / (1024 * 1024)).toFixed(1)} MB • {t.deviceName}
                    </div>
                  </div>
                </div>
                <span style={{ fontSize: "11.5px", fontWeight: 600, color: "#30d158" }}>
                  Completed
                </span>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
};
