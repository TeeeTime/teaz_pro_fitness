<script setup lang="ts">
import { dayTime, duration, km, pace, SPORT_LABELS } from '@/format'
import type { Run } from '@/types'

defineProps<{ run: Run | null }>()
</script>

<template>
  <div v-if="run" class="latest">
    <p class="latest__name">{{ run.name }}</p>
    <p class="latest__when">
      {{ dayTime(run.startTime) }}<template v-if="run.sportType !== 'Run'"> · {{ SPORT_LABELS[run.sportType] }}</template>
    </p>

    <p class="latest__distance">{{ km(run.distanceKm) }} <span class="latest__unit">km</span></p>

    <dl class="latest__stats">
      <div>
        <dt class="label">Time</dt>
        <dd>{{ duration(run.movingTimeSeconds) }}</dd>
      </div>
      <div>
        <dt class="label">Pace</dt>
        <dd>{{ pace(run.paceSecondsPerKm) }}</dd>
      </div>
      <div v-if="run.elevationGainMeters !== null">
        <dt class="label">Elevation</dt>
        <dd>{{ Math.round(run.elevationGainMeters) }} m</dd>
      </div>
      <div v-if="run.averageHeartrate !== null">
        <dt class="label">Heart rate</dt>
        <dd>{{ Math.round(run.averageHeartrate) }} bpm</dd>
      </div>
    </dl>

    <a class="latest__link" :href="run.stravaUrl" target="_blank" rel="noopener">View on Strava</a>
  </div>
  <p v-else class="latest__empty">No runs yet. Your next run on Strava shows up here.</p>
</template>

<style scoped>
.latest__name {
  margin-top: 8px;
  font-size: 1.05rem;
  font-weight: 600;
  overflow-wrap: anywhere;
}

.latest__when {
  font-size: 0.85rem;
  color: var(--color-text-secondary);
}

.latest__distance {
  margin-top: 12px;
  font-size: 3rem;
  font-weight: 600;
  line-height: 1.1;
}

.latest__unit {
  font-size: 1.25rem;
  font-weight: 500;
  color: var(--color-text-secondary);
}

.latest__stats {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px 16px;
  margin-top: 16px;
}

.latest__stats dd {
  margin: 0;
  font-weight: 500;
}

.latest__link {
  display: inline-block;
  margin-top: 16px;
  font-size: 0.85rem;
  font-weight: 600;
  color: var(--color-accent);
}

.latest__link:hover {
  color: var(--color-accent-hover);
}

.latest__empty {
  margin-top: 8px;
  color: var(--color-text-secondary);
}
</style>
