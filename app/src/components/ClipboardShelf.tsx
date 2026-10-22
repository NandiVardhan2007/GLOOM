import React, { useEffect, useState } from "react";
import { listen } from "@tauri-apps/api/event";
import { invoke } from "@tauri-apps/api/core";
import { ClipboardItem } from "../types";
import { CheckIcon, ClipboardIcon, CopyIcon } from "./IslandIcons";

export const ClipboardShelf: React.FC = () => {
  const [items, setItems] = useState<ClipboardItem[]>([]);
  const [copiedId, setCopiedId] = useState<number | null>(null);

  useEffect(() => {
    // Initial fetch
    invoke<ClipboardItem[]>("jarvis_get_clipboard")
      .then(setItems)
      .catch(console.error);

    // Event updates
    let unlisten: (() => void) | undefined;
    listen<ClipboardItem[]>("jarvis://clipboard-update", (event) => {
      setItems(event.payload);
    }).then((fn) => {
      unlisten = fn;
    }).catch(console.error);

    return () => {
      if (unlisten) unlisten();
    };
  }, []);

  const handleCopy = async (item: ClipboardItem) => {
    try {
      await navigator.clipboard.writeText(item.text);
      setCopiedId(item.id);
      setTimeout(() => setCopiedId(null), 1500);
    } catch (e) {
      console.error("Clipboard copy error:", e);
    }
  };

  return (
    <div
      style={{
        display: "flex",
        flexDirection: "column",
        gap: "8px",
        width: "100%",
        maxHeight: "140px",
        overflowY: "auto",
        paddingRight: "4px",
      }}
    >
      {items.length === 0 ? (
        <div
          style={{
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            gap: "8px",
            padding: "24px 0",
            color: "rgba(255, 255, 255, 0.4)",
            fontSize: "12px",
          }}
        >
          <ClipboardIcon size={16} />
          <span>Clipboard history is empty. Copy text anywhere.</span>
        </div>
      ) : (
        items.map((item) => (
          <div
            key={item.id}
            onClick={() => handleCopy(item)}
            style={{
              display: "flex",
              alignItems: "center",
              justifyContent: "space-between",
              background: "rgba(255, 255, 255, 0.055)",
              border: "0.5px solid rgba(255, 255, 255, 0.08)",
              borderRadius: "10px",
              padding: "8px 12px",
              cursor: "pointer",
              transition: "background 0.15s ease",
            }}
          >
            <span
              style={{
                fontSize: "12px",
                color: "rgba(255, 255, 255, 0.9)",
                whiteSpace: "nowrap",
                overflow: "hidden",
                textOverflow: "ellipsis",
                maxWidth: "420px",
              }}
            >
              {item.preview}
            </span>
            <div style={{ display: "flex", alignItems: "center", gap: "6px" }}>
              {copiedId === item.id ? (
                <CheckIcon size={14} color="#34d399" />
              ) : (
                <CopyIcon size={13} color="rgba(255, 255, 255, 0.4)" />
              )}
            </div>
          </div>
        ))
      )}
    </div>
  );
};
