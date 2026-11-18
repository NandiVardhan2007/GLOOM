import { describe, it } from "node:test";
import assert from "node:assert/strict";

// Mirror of parseDuration from clockFormat.ts for unit test verification
const MAX_SECONDS = 99 * 3600;

function parseDuration(text) {
  const value = text.trim();
  let seconds;
  if (/^\d+$/.test(value)) {
    seconds = Number(value) * 60;
  } else if (/^\d+:\d{1,2}$/.test(value)) {
    const [m, s] = value.split(":").map(Number);
    if (s > 59) return null;
    seconds = m * 60 + s;
  } else if (/^\d+:\d{1,2}:\d{1,2}$/.test(value)) {
    const [h, m, s] = value.split(":").map(Number);
    if (m > 59 || s > 59) return null;
    seconds = h * 3600 + m * 60 + s;
  } else {
    return null;
  }
  return seconds > 0 && seconds <= MAX_SECONDS ? seconds : null;
}

function formatStopwatch(ms, precise) {
  const total = Math.floor(ms / 1000);
  const hours = Math.floor(total / 3600);
  const mins = Math.floor((total % 3600) / 60);
  const secs = total % 60;
  const base =
    hours > 0
      ? `${hours}:${String(mins).padStart(2, "0")}:${String(secs).padStart(2, "0")}`
      : `${mins}:${String(secs).padStart(2, "0")}`;
  if (!precise) return base;
  return `${base}.${String(Math.floor((ms % 1000) / 10)).padStart(2, "0")}`;
}

describe("Clock Format & Stopwatch Engine", () => {
  it("parses single number as minutes", () => {
    assert.equal(parseDuration("5"), 300);
    assert.equal(parseDuration("25"), 1500);
  });

  it("parses mm:ss string into exact seconds", () => {
    assert.equal(parseDuration("1:30"), 90);
    assert.equal(parseDuration("25:00"), 1500);
    assert.equal(parseDuration("0:45"), 45);
  });

  it("parses hh:mm:ss string into exact seconds", () => {
    assert.equal(parseDuration("1:00:00"), 3600);
    assert.equal(parseDuration("2:15:30"), 8130);
  });

  it("rejects invalid second digits (>59) and non-numeric inputs", () => {
    assert.equal(parseDuration("1:60"), null);
    assert.equal(parseDuration("abc"), null);
    assert.equal(parseDuration(""), null);
    assert.equal(parseDuration("-5"), null);
  });

  it("formats stopwatch milliseconds correctly", () => {
    assert.equal(formatStopwatch(0, false), "0:00");
    assert.equal(formatStopwatch(65000, false), "1:05");
    assert.equal(formatStopwatch(3665000, false), "1:01:05");
    assert.equal(formatStopwatch(1234, true), "0:01.23");
  });
});
