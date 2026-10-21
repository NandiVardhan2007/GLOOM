import type { Event, UnlistenFn } from "@tauri-apps/api/event";
import { listen } from "@tauri-apps/api/event";
import { invoke } from "@tauri-apps/api/core";

/** True only inside the Tauri webview, false in a plain browser preview. */
export function isTauriRuntime(): boolean {
  try {
    return typeof window !== "undefined" && !!(window as any).__TAURI_INTERNALS__;
  } catch {
    return false;
  }
}

/**
 * `listen` throws synchronously when the Tauri IPC bridge is missing.
 * Returning a resolved unlisten keeps browser-based design iteration alive.
 */
export function safeListen<T>(
  event: string,
  handler: (event: Event<T>) => void
): Promise<UnlistenFn> {
  if (!isTauriRuntime()) return Promise.resolve(() => {});
  try {
    return listen<T>(event, handler);
  } catch (error) {
    console.warn(`Tauri listen("${event}") unavailable`, error);
    return Promise.resolve(() => {});
  }
}

export async function safeInvoke<T>(
  command: string,
  args?: Record<string, unknown>
): Promise<T | null> {
  if (!isTauriRuntime()) return null;
  try {
    return await invoke<T>(command, args);
  } catch (error) {
    console.error(`Tauri invoke("${command}") failed`, error);
    return null;
  }
}
