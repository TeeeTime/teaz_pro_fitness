// Shapes of the backend's JSON responses (see the records in backend/.../run and strava).

export interface Run {
  /** Strava activity id */
  id: number
  name: string
  sportType: 'Run' | 'TrailRun' | 'VirtualRun'
  /** ISO 8601 with offset, e.g. 2026-10-20T07:00:00+02:00 */
  startTime: string
  distanceKm: number
  movingTimeSeconds: number
  elapsedTimeSeconds: number
  /** Null without distance. */
  paceSecondsPerKm: number | null
  elevationGainMeters: number | null
  averageHeartrate: number | null
  stravaUrl: string
}

export interface RunList {
  connected: boolean
  syncedAt: string | null
  runs: Run[]
  attribution: string
}

export interface Week {
  /** The week's Monday, e.g. 2026-10-19 */
  weekStart: string
  distanceKm: number
  runs: number
  movingTimeSeconds: number
}

export interface WeeklyDistance {
  connected: boolean
  syncedAt: string | null
  /** Oldest first; the last one is the current week so far. */
  weeks: Week[]
  thisWeekKm: number
  lastWeekKm: number
  averageKm: number | null
  attribution: string
}

export interface StravaStatus {
  connected: boolean
  /** Made-up runs, no real Strava (local dev). */
  sample: boolean
  athleteName: string | null
  connectedAt: string | null
  syncedAt: string | null
  syncing: boolean
  syncError: string | null
}
