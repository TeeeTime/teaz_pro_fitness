// Everything in the backend's time zone, wherever the viewer is (like the times it sends).
const ZONE = 'Europe/Berlin'

const DAY_TIME = new Intl.DateTimeFormat('en-GB', {
  weekday: 'short',
  day: 'numeric',
  month: 'short',
  hour: '2-digit',
  minute: '2-digit',
  timeZone: ZONE,
})
const CLOCK = new Intl.DateTimeFormat('en-GB', { hour: '2-digit', minute: '2-digit', timeZone: ZONE })
// Week starts are plain dates; formatted in UTC so no zone shifts them by a day.
const DAY_MONTH = new Intl.DateTimeFormat('en-GB', { day: 'numeric', month: 'short', timeZone: 'UTC' })
const ONE_DECIMAL = new Intl.NumberFormat('en-GB', { minimumFractionDigits: 1, maximumFractionDigits: 1 })

/** "Tue 20 Oct, 07:00" */
export function dayTime(iso: string): string {
  return DAY_TIME.format(new Date(iso))
}

/** "07:00" */
export function clock(iso: string): string {
  return CLOCK.format(new Date(iso))
}

/** "12 Oct", for a week's Monday such as "2026-10-12". */
export function weekLabel(date: string): string {
  return DAY_MONTH.format(new Date(`${date}T00:00:00Z`))
}

/** "10.3" */
export function km(value: number): string {
  return ONE_DECIMAL.format(value)
}

/** "52:10" or "1:45:03" */
export function duration(seconds: number): string {
  const h = Math.floor(seconds / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  const s = seconds % 60
  const mm = h > 0 ? String(m).padStart(2, '0') : String(m)
  return `${h > 0 ? `${h}:` : ''}${mm}:${String(s).padStart(2, '0')}`
}

/** "5:12 /km", or an en dash without a pace. */
export function pace(secondsPerKm: number | null): string {
  if (secondsPerKm === null) return '–'
  return `${Math.floor(secondsPerKm / 60)}:${String(secondsPerKm % 60).padStart(2, '0')} /km`
}

export const SPORT_LABELS: Record<string, string> = {
  Run: 'Run',
  TrailRun: 'Trail run',
  VirtualRun: 'Virtual run',
}
