<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { Icon } from '@teeetime/teaz-pro-ui'
import ConnectStrava from '@/components/ConnectStrava.vue'
import LatestRun from '@/components/LatestRun.vue'
import RunList from '@/components/RunList.vue'
import WeeklyChart from '@/components/WeeklyChart.vue'
import { api, ApiError, errorMessage } from '@/api'
import { clock, dayTime } from '@/format'
import type { RunList as RunListResponse, StravaStatus, WeeklyDistance } from '@/types'

// The backend syncs every 15 minutes; checking a few times in between is plenty.
const REFRESH_MS = 5 * 60_000
const RECENT_RUNS = 20

const status = ref<StravaStatus | null>(null)
const runs = ref<RunListResponse | null>(null)
const weekly = ref<WeeklyDistance | null>(null)
const weeks = ref(12)
const loadingWeeks = ref(false)
const busy = ref(false)
const confirmingDisconnect = ref(false)
// Failed actions (connect, sync, …) and failed loading are shown separately,
// so the periodic refresh doesn't hide an action's error.
const error = ref<string | null>(null)
const loadError = ref<string | null>(null)
const info = ref<string | null>(null)

/**
 * Strava sends the browser back to /strava/callback?code=…&state=…&scope=…
 * (or ?error=access_denied). Hand that to the backend, then show the app at /.
 */
async function finishStravaCallback() {
  if (window.location.pathname !== '/strava/callback') return
  const params = new URLSearchParams(window.location.search)
  window.history.replaceState(null, '', '/')
  if (params.has('error')) {
    info.value = 'Connecting Strava was cancelled.'
    return
  }
  busy.value = true
  info.value = 'Fetching your runs from Strava…'
  try {
    status.value = await api.post<StravaStatus>('/api/strava/callback', {
      code: params.get('code'),
      state: params.get('state'),
      scope: params.get('scope'),
    })
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    info.value = null
    busy.value = false
  }
}

async function loadWeekly() {
  loadingWeeks.value = true
  try {
    weekly.value = await api.get<WeeklyDistance>(`/api/runs/weekly?weeks=${weeks.value}`)
  } finally {
    loadingWeeks.value = false
  }
}

async function refresh() {
  try {
    status.value = await api.get<StravaStatus>('/api/strava/connection')
    if (status.value.connected) {
      const [list] = await Promise.all([api.get<RunListResponse>(`/api/runs?limit=${RECENT_RUNS}`), loadWeekly()])
      runs.value = list
    } else {
      runs.value = null
      weekly.value = null
    }
    loadError.value = null
  } catch (e) {
    loadError.value = errorMessage(e)
  }
}

async function connect() {
  busy.value = true
  error.value = null
  try {
    const { url } = await api.post<{ url: string }>('/api/strava/connect')
    window.location.href = url
  } catch (e) {
    error.value = errorMessage(e)
    busy.value = false
  }
}

async function syncNow() {
  busy.value = true
  error.value = null
  try {
    await api.post<StravaStatus>('/api/strava/sync')
  } catch (e) {
    // 409: access was revoked on Strava; the refresh below shows "connect" again.
    if (e instanceof ApiError && e.status === 409) info.value = e.message
    else error.value = errorMessage(e)
  }
  await refresh()
  busy.value = false
}

async function disconnect() {
  busy.value = true
  error.value = null
  try {
    await api.delete('/api/strava/connection')
    confirmingDisconnect.value = false
    await refresh()
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    busy.value = false
  }
}

watch(weeks, () => loadWeekly().catch((e) => (error.value = errorMessage(e))))

let timer: ReturnType<typeof setInterval> | undefined
onMounted(async () => {
  await finishStravaCallback()
  await refresh()
  timer = setInterval(refresh, REFRESH_MS)
})
onBeforeUnmount(() => clearInterval(timer))
</script>

<template>
  <div class="view fitness">
    <p v-if="info" class="notice notice--working" role="status">{{ info }}</p>
    <p v-if="error" class="notice notice--error" role="alert">{{ error }}</p>
    <p v-if="loadError" class="notice notice--error" role="alert">{{ loadError }}</p>

    <ConnectStrava v-if="status && !status.connected" :sample="status.sample" :busy="busy" @connect="connect" />

    <template v-else-if="status">
      <header class="fitness__head">
        <div>
          <h1 class="view__title">Your runs</h1>
          <p class="fitness__status">
            Strava<template v-if="status.athleteName">: {{ status.athleteName }}</template>
            <template v-if="status.sample"> (sample data)</template>
            <template v-if="status.syncedAt"> · synced {{ dayTime(status.syncedAt) }}</template>
          </p>
        </div>
        <div class="fitness__actions">
          <template v-if="confirmingDisconnect">
            <span class="fitness__confirm">Removes your runs here and revokes access.</span>
            <button type="button" class="button" :disabled="busy" @click="disconnect">
              <Icon name="unlink" class="button__icon" />
              Disconnect
            </button>
            <button type="button" class="button" :disabled="busy" @click="confirmingDisconnect = false">Cancel</button>
          </template>
          <template v-else>
            <button type="button" class="button button--primary" :disabled="busy" @click="syncNow">
              <Icon name="refresh" class="button__icon" :class="{ 'button__icon--spinning': busy }" />
              {{ busy ? 'Syncing…' : 'Sync now' }}
            </button>
            <button type="button" class="button" :disabled="busy" @click="confirmingDisconnect = true">
              <Icon name="unlink" class="button__icon" />
              Disconnect Strava
            </button>
          </template>
        </div>
      </header>

      <p v-if="status.syncError" class="notice notice--error" role="alert">
        Last sync failed: {{ status.syncError }}
        <template v-if="status.syncedAt"> Showing runs as of {{ clock(status.syncedAt) }}.</template>
      </p>

      <div class="fitness__overview">
        <section class="card">
          <h2 class="label">Latest run</h2>
          <LatestRun :run="runs?.runs[0] ?? null" />
        </section>

        <section class="card">
          <div class="card__head">
            <h2 class="label">Weekly distance, km</h2>
            <div class="segmented" role="group" aria-label="Weeks shown">
              <button
                v-for="option in [12, 26, 52]"
                :key="option"
                type="button"
                class="segmented__option"
                :class="{ 'segmented__option--active': weeks === option }"
                :aria-pressed="weeks === option"
                @click="weeks = option"
              >
                {{ option }} wk
              </button>
            </div>
          </div>
          <WeeklyChart v-if="weekly" :weekly="weekly" :loading="loadingWeeks" />
        </section>
      </div>

      <section class="fitness__recent">
        <h2 class="fitness__subtitle">Recent runs</h2>
        <RunList v-if="runs" :runs="runs.runs" />
      </section>

      <p class="fitness__attribution">{{ runs?.attribution ?? 'Powered by Strava' }}</p>
    </template>

    <p v-else-if="!error && !loadError" class="fitness__loading">Loading…</p>
  </div>
</template>

<style scoped>
.fitness {
  container-type: inline-size;
}

.fitness__head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 24px;
}

.fitness__status {
  margin-top: 4px;
  font-size: 0.85rem;
  color: var(--color-text-secondary);
}

.fitness__actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.fitness__confirm {
  font-size: 0.85rem;
  color: var(--color-text-secondary);
}

.fitness__overview {
  display: grid;
  grid-template-columns: 1fr;
  gap: 16px;
}

@container (min-width: 820px) {
  .fitness__overview {
    grid-template-columns: minmax(260px, 1fr) 2fr;
  }
}

.card {
  min-width: 0;
  padding: 16px;
  background-color: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
}

.card__head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.segmented {
  display: inline-flex;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
}

.segmented__option {
  padding: 4px 10px;
  font: inherit;
  font-size: 0.8rem;
  color: var(--color-text-secondary);
  background: none;
  border: none;
  cursor: pointer;
}

.segmented__option + .segmented__option {
  border-left: 1px solid var(--color-border);
}

.segmented__option:hover {
  background-color: var(--color-surface-hover);
}

.segmented__option:focus-visible {
  outline: 2px solid var(--color-accent);
  outline-offset: -2px;
}

.segmented__option--active {
  font-weight: 600;
  color: var(--color-text-primary);
  background-color: var(--color-surface-active);
}

.fitness__recent {
  margin-top: 32px;
}

.fitness__subtitle {
  margin-bottom: 12px;
  font-size: 1.1rem;
  font-weight: 600;
}

.fitness__attribution {
  margin-top: 16px;
  font-size: 0.75rem;
  color: var(--color-text-secondary);
}

.fitness__loading {
  color: var(--color-text-secondary);
}
</style>
