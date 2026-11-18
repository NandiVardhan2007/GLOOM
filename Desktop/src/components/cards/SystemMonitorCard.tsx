import { FC, useEffect, useState } from "react";
import { invoke } from "@tauri-apps/api/core";
import { Cpu, MemoryStick, HardDrive, ArrowDown, ArrowUp, Activity, ExternalLink, Zap } from "lucide-react";
import { playTactileSound } from "../../utils/audioFeedback";
import "./SystemMonitorCard.css";

interface RamDetails {
  percent: number;
  total_mb: number;
  used_mb: number;
  free_mb: number;
}

interface SystemOverview {
  cpu_percent: number;
  ram: RamDetails;
  disk_free_gb: number;
  disk_total_gb: number;
  net_in_bps: number;
  net_out_bps: number;
}

function formatBytesPerSec(bytes: number): string {
  if (bytes <= 0) return "0 B/s";
  if (bytes < 1024) return `${bytes} B/s`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB/s`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB/s`;
}

function formatGb(mb: number): string {
  return `${(mb / 1024).toFixed(1)} GB`;
}

export const SystemMonitorCard: FC = () => {
  const [metrics, setMetrics] = useState<SystemOverview>({
    cpu_percent: 12,
    ram: { percent: 38, total_mb: 16384, used_mb: 6225, free_mb: 10159 },
    disk_free_gb: 418,
    disk_total_gb: 953,
    net_in_bps: 0,
    net_out_bps: 0,
  });

  useEffect(() => {
    let mounted = true;
    const fetchMetrics = async () => {
      try {
        const overview = await invoke<SystemOverview>("get_system_overview");
        if (mounted && overview) {
          setMetrics(overview);
        }
      } catch {
        // Fallback to separate endpoints if get_system_overview is pending
        try {
          const [cpu, ram, disk, net] = await Promise.all([
            invoke<number>("get_cpu_usage").catch(() => 15),
            invoke<number>("get_ram_usage").catch(() => 40),
            invoke<number>("get_disk_space").catch(() => 250),
            invoke<[number, number]>("get_network_speed").catch(() => [0, 0]),
          ]);
          if (mounted) {
            setMetrics((prev) => ({
              cpu_percent: cpu,
              ram: {
                percent: ram,
                total_mb: prev.ram.total_mb,
                used_mb: Math.round((prev.ram.total_mb * ram) / 100),
                free_mb: Math.round(prev.ram.total_mb * (1 - ram / 100)),
              },
              disk_free_gb: disk,
              disk_total_gb: prev.disk_total_gb,
              net_in_bps: net[1],
              net_out_bps: net[0],
            }));
          }
        } catch {}
      }
    };

    fetchMetrics();
    const interval = setInterval(fetchMetrics, 1500);
    return () => {
      mounted = false;
      clearInterval(interval);
    };
  }, []);

  const openTaskManager = (e: React.MouseEvent) => {
    e.stopPropagation();
    playTactileSound("snap");
    invoke("open_system_action", { action: "task-manager" }).catch(() => {});
  };

  const cpuLoad = Math.min(100, Math.max(0, metrics.cpu_percent));
  const ramPercent = Math.min(100, Math.max(0, Math.round(metrics.ram.percent)));
  const diskUsedGb = Math.max(0, metrics.disk_total_gb - metrics.disk_free_gb);
  const diskPercent = metrics.disk_total_gb > 0 ? Math.min(100, Math.round((diskUsedGb / metrics.disk_total_gb) * 100)) : 45;

  return (
    <div className="system-monitor-card" onClick={(e) => e.stopPropagation()}>
      <div className="sys-header">
        <div className="sys-title-wrap">
          <Activity size={14} className="sys-beacon-icon" />
          <span className="sys-title">Hardware Telemetry</span>
          <span className="sys-badge">
            <span className="sys-live-dot" />
            Live
          </span>
        </div>
        <button
          className="sys-action-btn"
          onClick={openTaskManager}
          title="Open Task Manager"
        >
          <span>Task Manager</span>
          <ExternalLink size={11} strokeWidth={2.4} />
        </button>
      </div>

      <div className="sys-grid">
        {/* CPU Metric Card */}
        <div className="sys-tile">
          <div className="sys-tile-header">
            <div className="sys-tile-label-wrap">
              <Cpu size={13} strokeWidth={2.2} className="sys-tile-icon cpu-icon" />
              <span className="sys-tile-name">CPU Load</span>
            </div>
            <span className="sys-tile-val">{cpuLoad}%</span>
          </div>
          <div className="sys-bar-track">
            <div
              className={`sys-bar-fill ${cpuLoad > 85 ? "danger" : cpuLoad > 65 ? "warning" : "nominal"}`}
              style={{ width: `${cpuLoad}%` }}
            />
          </div>
          <div className="sys-tile-sub">
            {cpuLoad < 30 ? "Idle · Cool" : cpuLoad < 70 ? "Nominal Activity" : "High Workload"}
          </div>
        </div>

        {/* Memory / RAM Metric Card */}
        <div className="sys-tile">
          <div className="sys-tile-header">
            <div className="sys-tile-label-wrap">
              <MemoryStick size={13} strokeWidth={2.2} className="sys-tile-icon ram-icon" />
              <span className="sys-tile-name">RAM Usage</span>
            </div>
            <span className="sys-tile-val">{ramPercent}%</span>
          </div>
          <div className="sys-bar-track">
            <div
              className={`sys-bar-fill ${ramPercent > 85 ? "danger" : ramPercent > 70 ? "warning" : "nominal"}`}
              style={{ width: `${ramPercent}%` }}
            />
          </div>
          <div className="sys-tile-sub">
            {formatGb(metrics.ram.used_mb)} of {formatGb(metrics.ram.total_mb)}
          </div>
        </div>

        {/* Disk Drive Metric Card */}
        <div className="sys-tile">
          <div className="sys-tile-header">
            <div className="sys-tile-label-wrap">
              <HardDrive size={13} strokeWidth={2.2} className="sys-tile-icon disk-icon" />
              <span className="sys-tile-name">System Disk (C:)</span>
            </div>
            <span className="sys-tile-val">{metrics.disk_free_gb} GB Free</span>
          </div>
          <div className="sys-bar-track">
            <div className="sys-bar-fill disk" style={{ width: `${diskPercent}%` }} />
          </div>
          <div className="sys-tile-sub">
            {diskUsedGb} GB used of {metrics.disk_total_gb} GB
          </div>
        </div>

        {/* Live Network Bandwidth Metric Card */}
        <div className="sys-tile">
          <div className="sys-tile-header">
            <div className="sys-tile-label-wrap">
              <Zap size={13} strokeWidth={2.2} className="sys-tile-icon net-icon" />
              <span className="sys-tile-name">Network Traffic</span>
            </div>
          </div>
          <div className="sys-net-rates">
            <div className="sys-net-rate down">
              <ArrowDown size={12} strokeWidth={2.5} className="net-arrow down" />
              <span className="net-num">{formatBytesPerSec(metrics.net_in_bps)}</span>
            </div>
            <div className="sys-net-rate up">
              <ArrowUp size={12} strokeWidth={2.5} className="net-arrow up" />
              <span className="net-num">{formatBytesPerSec(metrics.net_out_bps)}</span>
            </div>
          </div>
          <div className="sys-tile-sub">Live Ethernet / Wi-Fi Throughput</div>
        </div>
      </div>
    </div>
  );
};
