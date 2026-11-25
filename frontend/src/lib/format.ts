// Formatting in the user's language. Times are shown in Vila Ria's time, whatever the device says.

export const VILA_RIA_TIME_ZONE = 'Europe/Lisbon';

/** The UI says "pt"; Intl reads that as Brazil. Vila Ria is in Portugal: "12,50 €", not "€ 12,50". */
export function intlLocale(language: string): string {
  return language.split('-')[0] === 'pt' ? 'pt-PT' : language;
}

export function formatMoney(cents: number, currency: string, language: string): string {
  return new Intl.NumberFormat(intlLocale(language), { style: 'currency', currency }).format(
    cents / 100,
  );
}

export function formatPercent(value: number, language: string, fractionDigits = 0): string {
  return new Intl.NumberFormat(intlLocale(language), {
    style: 'percent',
    maximumFractionDigits: fractionDigits,
    minimumFractionDigits: fractionDigits,
  }).format(value / 100);
}

export function formatDateTime(iso: string, language: string): string {
  return new Intl.DateTimeFormat(intlLocale(language), {
    dateStyle: 'medium',
    timeStyle: 'short',
    timeZone: VILA_RIA_TIME_ZONE,
  }).format(new Date(iso));
}

export function formatTime(iso: string, language: string): string {
  return new Intl.DateTimeFormat(intlLocale(language), {
    timeStyle: 'short',
    timeZone: VILA_RIA_TIME_ZONE,
  }).format(new Date(iso));
}

/** A calendar date ("2026-11-28") shown as a short weekday and day, with no time zone shift. */
export function formatDay(isoDate: string, language: string): string {
  const [year, month, day] = isoDate.split('-').map(Number);
  return new Intl.DateTimeFormat(intlLocale(language), {
    weekday: 'short',
    day: 'numeric',
    month: 'short',
    timeZone: 'UTC',
  }).format(new Date(Date.UTC(year, month - 1, day)));
}

/** A clock for a running trip: 0:42:17. */
export function formatClock(totalSeconds: number): string {
  const seconds = Math.max(0, Math.floor(totalSeconds));
  const h = Math.floor(seconds / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  const s = seconds % 60;
  return `${h}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
}

/** "3h 12m" or "45m" in English, "3 h 12 min" in Portuguese; the caller adds any sign. */
export function formatDuration(totalMinutes: number, language: string): string {
  const minutes = Math.max(0, Math.round(Math.abs(totalMinutes)));
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  const unit = (value: number, kind: 'hour' | 'minute') =>
    new Intl.NumberFormat(intlLocale(language), {
      style: 'unit',
      unit: kind,
      unitDisplay: 'narrow',
    }).format(value);
  if (h === 0) return unit(m, 'minute');
  if (m === 0) return unit(h, 'hour');
  return `${unit(h, 'hour')} ${unit(m, 'minute')}`;
}

export function formatRelative(iso: string, now: number, language: string): string {
  const raw = Math.round((Date.parse(iso) - now) / 1000);
  // A server clock a little ahead of the device would read "in 1 sec." for something just done.
  const seconds = raw > 0 && raw < 60 ? 0 : raw;
  const format = new Intl.RelativeTimeFormat(intlLocale(language), {
    numeric: 'auto',
    style: 'short',
  });
  const abs = Math.abs(seconds);
  if (abs < 60) return format.format(seconds, 'second');
  if (abs < 3600) return format.format(Math.round(seconds / 60), 'minute');
  if (abs < 86_400) return format.format(Math.round(seconds / 3600), 'hour');
  return format.format(Math.round(seconds / 86_400), 'day');
}

/** A rough range for a battery level, from the fleet's 55 km on a full charge. */
export function estimatedRangeKm(batteryPct: number): number {
  return Math.round((batteryPct / 100) * 55);
}
