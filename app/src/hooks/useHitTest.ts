import { useEffect, RefObject, useRef } from "react";
import { invoke } from "@tauri-apps/api/core";
import { HitRect } from "../types";
import { isTauriRuntime } from "../lib/tauri";

export function useHitTest(
  ref: RefObject<HTMLElement | null>,
  isVisible: boolean,
  extraRef?: RefObject<HTMLElement | null>
) {
  const lastRectsRef = useRef<HitRect[]>([]);
  const rafIdRef = useRef<number | null>(null);

  useEffect(() => {
    if (!isTauriRuntime()) return;
    if (!isVisible || !ref.current) {
      invoke("jarvis_update_hit_rects", { rects: [] }).catch(console.error);
      lastRectsRef.current = [];
      return;
    }

    const areRectsEqual = (a: HitRect[], b: HitRect[]) => {
      if (a.length !== b.length) return false;
      for (let i = 0; i < a.length; i++) {
        if (
          Math.abs(a[i].x - b[i].x) > 0.5 ||
          Math.abs(a[i].y - b[i].y) > 0.5 ||
          Math.abs(a[i].width - b[i].width) > 0.5 ||
          Math.abs(a[i].height - b[i].height) > 0.5
        ) {
          return false;
        }
      }
      return true;
    };

    const scheduleUpdate = () => {
      if (rafIdRef.current !== null) return;
      rafIdRef.current = window.requestAnimationFrame(() => {
        rafIdRef.current = null;
        if (!ref.current) return;

        const b = ref.current.getBoundingClientRect();
        const nextRects: HitRect[] = [
          {
            x: Math.round(b.left),
            y: Math.round(b.top),
            width: Math.round(b.width),
            height: Math.round(b.height),
          },
        ];

        if (extraRef?.current) {
          const eb = extraRef.current.getBoundingClientRect();
          nextRects.push({
            x: Math.round(eb.left),
            y: Math.round(eb.top),
            width: Math.round(eb.width),
            height: Math.round(eb.height),
          });
        }

        // Always query for active context menu in the DOM
        const menuEl = document.querySelector(".jarvis-context-menu") as HTMLElement | null;
        if (menuEl) {
          const eb = menuEl.getBoundingClientRect();
          if (eb.width > 0 && eb.height > 0) {
            nextRects.push({
              x: Math.round(eb.left),
              y: Math.round(eb.top),
              width: Math.round(eb.width),
              height: Math.round(eb.height),
            });
          }
        }

        if (!areRectsEqual(nextRects, lastRectsRef.current)) {
          lastRectsRef.current = nextRects;
          invoke("jarvis_update_hit_rects", { rects: nextRects }).catch(console.error);
        }
      });
    };

    scheduleUpdate();

    const observer = new ResizeObserver(() => {
      scheduleUpdate();
    });

    observer.observe(ref.current);
    if (extraRef?.current) {
      observer.observe(extraRef.current);
    }

    const mutationObserver = new MutationObserver(() => {
      scheduleUpdate();
    });
    mutationObserver.observe(document.body, { childList: true, subtree: true });

    // Interval during initial transition (16ms rAF-synced)
    const interval = window.setInterval(scheduleUpdate, 24);
    const timeout = window.setTimeout(() => window.clearInterval(interval), 400);

    return () => {
      observer.disconnect();
      mutationObserver.disconnect();
      window.clearInterval(interval);
      window.clearTimeout(timeout);
      if (rafIdRef.current !== null) {
        window.cancelAnimationFrame(rafIdRef.current);
        rafIdRef.current = null;
      }
    };
  }, [ref, isVisible, extraRef?.current]);
}
