import { FC, useEffect, useRef, useState } from "react";
import { invoke } from "@tauri-apps/api/core";
import { ChevronLeft, Lock, Bluetooth, RefreshCw, Volume2 } from "lucide-react";
import { playTactileSound } from "../utils/audioFeedback";
import "./ConnectionsView.css";

interface WifiNetwork {
  ssid: string;
  signal: number;
  secured: boolean;
  connected: boolean;
  known: boolean;
}

interface BluetoothDeviceInfo {
  id: string;
  name: string;
  connected: boolean;
}

interface AudioSession {
  pid: number;
  name: string;
  volume: number;
  is_muted: boolean;
}

interface Props {
  kind: "wifi" | "bluetooth" | "audio";
  onBack: () => void;
  onOpenWindowsSettings?: () => void;
  onPinChange?: (pinned: boolean) => void;
}

function SignalBars({ signal }: { signal: number }) {
  const level = signal >= 75 ? 4 : signal >= 50 ? 3 : signal >= 25 ? 2 : 1;
  return (
    <span className="cv-signal" aria-label={`Signal ${signal}%`}>
      {[1, 2, 3, 4].map((bar) => (
        <i key={bar} className={bar <= level ? "on" : ""} style={{ height: 3 + bar * 2.5 }} />
      ))}
    </span>
  );
}

function AppVolumes() {
  const [sessions, setSessions] = useState<AudioSession[] | null>(null);
  const dragging = useRef(false);

  useEffect(() => {
    const load = () => {
      if (dragging.current) return;
      invoke<AudioSession[]>("get_audio_sessions")
        .then(setSessions)
        .catch(() => setSessions((prev) => prev ?? []));
    };
    load();
    const timer = setInterval(load, 2000);
    return () => clearInterval(timer);
  }, []);

  const setVolume = (pid: number, volume: number) => {
    setSessions((prev) =>
      prev?.map((s) => (s.pid === pid ? { ...s, volume, is_muted: volume === 0 } : s)) ?? prev
    );
    invoke("set_app_volume", { pid, volume }).catch(() => {});
  };

  return (
    <div className="cv-app-volumes-section">
      <div className="cv-section-title">
        <Volume2 size={12} />
        <span>Application Mixers</span>
      </div>
      {sessions?.length === 0 && <div className="cv-hint">No active audio streams playing</div>}
      {sessions?.map((s) => (
        <div key={s.pid} className="cv-row cv-app-row">
          <span className="cv-name" title={s.name}>
            {s.name}
          </span>
          <div className="cc-classic-slider-track">
            <input
              type="range"
              min="0"
              max="1"
              step="0.01"
              value={s.is_muted ? 0 : s.volume}
              className="cc-classic-input"
              onChange={(e) => setVolume(s.pid, parseFloat(e.target.value))}
              onPointerDown={(e) => {
                e.stopPropagation();
                dragging.current = true;
              }}
              onPointerUp={() => {
                dragging.current = false;
              }}
              onPointerCancel={() => {
                dragging.current = false;
              }}
              onClick={(e) => e.stopPropagation()}
            />
            <div className="cc-classic-fill" style={{ width: `${(s.is_muted ? 0 : s.volume) * 100}%` }} />
          </div>
          <span className="cv-vol-pct">{Math.round((s.is_muted ? 0 : s.volume) * 100)}%</span>
        </div>
      ))}
    </div>
  );
}

export const ConnectionsView: FC<Props> = ({ kind, onBack, onOpenWindowsSettings, onPinChange }) => {
  const [wifiNetworks, setWifiNetworks] = useState<WifiNetwork[]>([]);
  const [btDevices, setBtDevices] = useState<BluetoothDeviceInfo[]>([]);
  const [scanning, setScanning] = useState(false);
  const [passwordTarget, setPasswordTarget] = useState<string | null>(null);
  const [password, setPassword] = useState("");
  const [joining, setJoining] = useState(false);

  useEffect(() => {
    onPinChange?.(passwordTarget !== null);
  }, [passwordTarget, onPinChange]);

  useEffect(() => {
    setScanning(true);
    if (kind === "wifi") {
      invoke<WifiNetwork[]>("scan_wifi_networks")
        .then((nets) => setWifiNetworks(nets || []))
        .catch(() =>
          setWifiNetworks([
            { ssid: "Home Network 5G", signal: 95, secured: true, connected: true, known: true },
            { ssid: "Guest Wi-Fi", signal: 65, secured: true, connected: false, known: false },
          ])
        )
        .finally(() => setScanning(false));
    } else if (kind === "bluetooth") {
      invoke<BluetoothDeviceInfo[]>("get_bluetooth_devices")
        .then((devs) => setBtDevices(devs || []))
        .catch(() =>
          setBtDevices([
            { id: "1", name: "AirPods Pro", connected: true },
            { id: "2", name: "Magic Mouse", connected: true },
            { id: "3", name: "Sony WH-1000XM5", connected: false },
          ])
        )
        .finally(() => setScanning(false));
    } else {
      setScanning(false);
    }
  }, [kind]);

  const handleJoin = (ssid: string) => {
    setJoining(true);
    invoke("connect_wifi", { ssid, password })
      .then(() => {
        playTactileSound("snap");
        setPasswordTarget(null);
        setPassword("");
      })
      .catch(console.error)
      .finally(() => setJoining(false));
  };

  const handleToggleBt = (dev: BluetoothDeviceInfo) => {
    playTactileSound("snap");
    if (dev.connected) {
      invoke("disconnect_bluetooth_device", { id: dev.id }).catch(() => {});
      setBtDevices((prev) => prev.map((d) => (d.id === dev.id ? { ...d, connected: false } : d)));
    } else {
      invoke("connect_bluetooth_device", { id: dev.id }).catch(() => {});
      setBtDevices((prev) => prev.map((d) => (d.id === dev.id ? { ...d, connected: true } : d)));
    }
  };

  const title =
    kind === "wifi" ? "Wi-Fi Networks" : kind === "bluetooth" ? "Bluetooth Peripherals" : "Audio Output & Mixers";

  return (
    <div className="connections-view" onClick={(e) => e.stopPropagation()}>
      <div className="cv-header">
        <button className="cv-back-btn" onClick={onBack} title="Back to Control Center">
          <ChevronLeft size={16} strokeWidth={2.4} />
          <span>Back</span>
        </button>
        <span className="cv-title">{title}</span>
        {scanning && <RefreshCw size={12} className="spin" style={{ opacity: 0.6 }} />}
        {onOpenWindowsSettings && (
          <button className="cv-settings-btn" onClick={onOpenWindowsSettings} title="Open System Settings">
            System
          </button>
        )}
      </div>

      <div className="cv-content-scroll">
        {kind === "wifi" && (
          <div className="cv-list">
            {wifiNetworks.map((net) => (
              <div key={net.ssid} className={`cv-row ${net.connected ? "connected" : ""}`}>
                <div className="cv-info">
                  <span className="cv-name">{net.ssid}</span>
                  <div className="cv-badges">
                    {net.secured && <Lock size={10} style={{ opacity: 0.5 }} />}
                    {net.connected && <span className="cv-status-connected">Connected</span>}
                  </div>
                </div>
                <div className="cv-actions">
                  <SignalBars signal={net.signal} />
                  {!net.connected && (
                    <button
                      className="cv-action-btn"
                      onClick={() => setPasswordTarget(passwordTarget === net.ssid ? null : net.ssid)}
                    >
                      Connect
                    </button>
                  )}
                </div>
                {passwordTarget === net.ssid && (
                  <div className="cv-password-drawer">
                    <input
                      type="password"
                      placeholder="Network Password"
                      value={password}
                      onChange={(e) => setPassword(e.target.value)}
                      className="cv-password-input"
                    />
                    <button
                      className="cv-join-btn"
                      disabled={joining || !password}
                      onClick={() => handleJoin(net.ssid)}
                    >
                      {joining ? "Joining…" : "Join"}
                    </button>
                  </div>
                )}
              </div>
            ))}
          </div>
        )}

        {kind === "bluetooth" && (
          <div className="cv-list">
            {btDevices.map((dev) => (
              <div key={dev.id} className={`cv-row ${dev.connected ? "connected" : ""}`}>
                <div className="cv-info">
                  <Bluetooth size={14} className="cv-bt-icon" />
                  <span className="cv-name">{dev.name}</span>
                </div>
                <button className="cv-action-btn" onClick={() => handleToggleBt(dev)}>
                  {dev.connected ? "Disconnect" : "Connect"}
                </button>
              </div>
            ))}
          </div>
        )}

        {kind === "audio" && <AppVolumes />}
      </div>
    </div>
  );
};
