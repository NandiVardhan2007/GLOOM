export const DEFAULT_DUAL_CLOCK_ZONE = "UTC";

export interface ZoneOption {
  label: string;
  zone: string;
  detail: string;
}

const MINUTE = 60_000;
const DAY = 24 * 60 * MINUTE;

const formatters = new Map<string, Intl.DateTimeFormat>();

function formatter(key: string, make: () => Intl.DateTimeFormat): Intl.DateTimeFormat {
  let f = formatters.get(key);
  if (!f) {
    f = make();
    formatters.set(key, f);
  }
  return f;
}

export function isValidZone(zone: string): boolean {
  try {
    new Intl.DateTimeFormat("en-US", { timeZone: zone });
    return true;
  } catch {
    return false;
  }
}

function wallClock(date: Date, zone?: string): number {
  if (!zone) return Date.UTC(date.getFullYear(), date.getMonth(), date.getDate(), date.getHours(), date.getMinutes());
  const parts = formatter(`wall|${zone}`, () =>
    new Intl.DateTimeFormat("en-US", {
      timeZone: zone,
      hourCycle: "h23",
      year: "numeric",
      month: "numeric",
      day: "numeric",
      hour: "numeric",
      minute: "numeric",
    })
  ).formatToParts(date);
  const get = (type: string) => Number(parts.find((p) => p.type === type)?.value);
  return Date.UTC(get("year"), get("month") - 1, get("day"), get("hour"), get("minute"));
}

export function formatZoneTime(date: Date, zone: string, hour12: boolean): string {
  try {
    return formatter(`time|${zone}|${hour12}`, () =>
      new Intl.DateTimeFormat([], { timeZone: zone, hour: "2-digit", minute: "2-digit", hour12 })
    ).format(date);
  } catch {
    return "";
  }
}

export function zoneDayShift(date: Date, zone: string): number {
  try {
    return Math.floor(wallClock(date, zone) / DAY) - Math.floor(wallClock(date) / DAY);
  } catch {
    return 0;
  }
}

function zoneOffsetMinutes(date: Date, zone?: string): number {
  return Math.round((wallClock(date, zone) - wallClock(date, "UTC")) / MINUTE);
}

function formatHours(minutes: number): string {
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  return m ? `${h}:${String(m).padStart(2, "0")}` : String(h);
}

export function zoneOffsetLabel(zone: string, date = new Date()): string {
  try {
    const offset = zoneOffsetMinutes(date, zone);
    if (offset === 0) return "UTC";
    return `UTC${offset > 0 ? "+" : "−"}${formatHours(Math.abs(offset))}`;
  } catch {
    return "";
  }
}

export function zoneCityName(zone: string): string {
  return zone.split("/").pop()!.replace(/_/g, " ");
}

let zoneList: string[] | null = null;

function allZones(): string[] {
  if (!zoneList) {
    let zones: string[] = [];
    try {
      zones = (Intl as unknown as { supportedValuesOf: (key: string) => string[] }).supportedValuesOf("timeZone");
    } catch {
      zones = [];
    }
    zoneList = zones.includes("UTC") ? zones : ["UTC", ...zones];
  }
  return zoneList;
}

export function searchZones(query: string, limit = 6): ZoneOption[] {
  const q = query.trim().toLowerCase();
  if (!q) return [];
  return allZones()
    .filter((zone) => zone.toLowerCase().replace(/_/g, " ").includes(q))
    .sort(
      (a, b) =>
        Number(!zoneCityName(a).toLowerCase().startsWith(q)) - Number(!zoneCityName(b).toLowerCase().startsWith(q))
    )
    .slice(0, limit)
    .map((zone) => ({ label: zoneCityName(zone), zone, detail: zone.includes("/") ? zone.split("/")[0] : "" }));
}
