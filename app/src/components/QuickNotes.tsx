import React, { useState, useEffect } from "react";
import { invoke } from "@tauri-apps/api/core";
import { Copy, Check, Trash2, Calendar, ListPlus } from "lucide-react";

export const QuickNotes: React.FC = () => {
  const [note, setNote] = useState<string>(() => {
    return localStorage.getItem("jarvis_quick_notes") || "";
  });
  const [copied, setCopied] = useState<boolean>(false);

  useEffect(() => {
    localStorage.setItem("jarvis_quick_notes", note);
  }, [note]);

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(note);
      setCopied(true);
      window.setTimeout(() => setCopied(false), 2000);
    } catch (err) {
      console.error("Clipboard write error:", err);
    }
  };

  const handleClear = () => {
    if (note.trim().length === 0) return;
    if (window.confirm("Clear all notes?")) {
      setNote("");
    }
  };

  const insertTimestamp = () => {
    const now = new Date();
    const stamp = `[${now.toLocaleDateString(undefined, { month: "short", day: "numeric" })} ${now.toLocaleTimeString(undefined, { hour: "2-digit", minute: "2-digit" })}] `;
    setNote((prev) => (prev ? `${prev}\n${stamp}` : stamp));
  };

  const insertBullet = () => {
    setNote((prev) => (prev ? `${prev}\n• ` : "• "));
  };

  const charCount = note.length;
  const wordCount = note.trim() ? note.trim().split(/\s+/).length : 0;

  return (
    <div
      style={{
        display: "flex",
        flexDirection: "column",
        gap: "8px",
        height: "100%",
        paddingRight: "2px",
      }}
    >
      {/* Action Toolbar */}
      <div
        style={{
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
          padding: "0 2px",
        }}
      >
        <div style={{ display: "flex", alignItems: "center", gap: "6px" }}>
          <button
            onClick={insertTimestamp}
            style={{
              display: "flex",
              alignItems: "center",
              gap: "4px",
              padding: "5px 10px",
              borderRadius: "8px",
              border: "1px solid rgba(255, 255, 255, 0.12)",
              background: "#1e1e28",
              color: "#ffffff",
              fontSize: "11px",
              fontWeight: 600,
              cursor: "pointer",
            }}
            title="Insert timestamp"
          >
            <Calendar size={12} color="#0a84ff" />
            <span>Time</span>
          </button>

          <button
            onClick={insertBullet}
            style={{
              display: "flex",
              alignItems: "center",
              gap: "4px",
              padding: "5px 10px",
              borderRadius: "8px",
              border: "1px solid rgba(255, 255, 255, 0.12)",
              background: "#1e1e28",
              color: "#ffffff",
              fontSize: "11px",
              fontWeight: 600,
              cursor: "pointer",
            }}
            title="Insert bullet"
          >
            <ListPlus size={12} color="#34d399" />
            <span>Bullet</span>
          </button>
        </div>

        <div style={{ display: "flex", alignItems: "center", gap: "6px" }}>
          <span
            style={{
              fontSize: "11px",
              fontWeight: 600,
              color: "#94a3b8",
              marginRight: "4px",
            }}
          >
            {wordCount}w • {charCount}c
          </span>

          <button
            onClick={handleCopy}
            disabled={!note}
            style={{
              display: "flex",
              alignItems: "center",
              gap: "4px",
              padding: "5px 11px",
              borderRadius: "8px",
              border: "none",
              background: copied ? "#059669" : "#1e1e28",
              color: "#ffffff",
              fontSize: "11px",
              fontWeight: 700,
              cursor: note ? "pointer" : "default",
              transition: "all 0.15s ease",
            }}
          >
            {copied ? <Check size={12} /> : <Copy size={12} />}
            <span>{copied ? "Copied" : "Copy"}</span>
          </button>

          <button
            onClick={handleClear}
            disabled={!note}
            style={{
              display: "flex",
              alignItems: "center",
              padding: "5px 9px",
              borderRadius: "8px",
              border: "none",
              background: "#1e1e28",
              color: note ? "#f87171" : "#64748b",
              fontSize: "11px",
              cursor: note ? "pointer" : "default",
            }}
            title="Clear note"
          >
            <Trash2 size={12} />
          </button>
        </div>
      </div>

      {/* Notepad Textarea */}
      <textarea
        value={note}
        onChange={(e) => setNote(e.target.value)}
        onFocus={() => {
          invoke("jarvis_focus_window").catch(console.error);
        }}
        onClick={() => {
          invoke("jarvis_focus_window").catch(console.error);
        }}
        placeholder="Type a quick note, idea, or reminder... (auto-saved)"
        style={{
          flex: 1,
          width: "100%",
          resize: "none",
          background: "#181820",
          border: "1px solid rgba(255, 255, 255, 0.12)",
          borderRadius: "12px",
          padding: "10px 14px",
          color: "#ffffff",
          fontSize: "13px",
          lineHeight: "1.5",
          fontFamily: "inherit",
          outline: "none",
          boxSizing: "border-box",
          userSelect: "text",
          cursor: "text",
        }}
      />
    </div>
  );
};
