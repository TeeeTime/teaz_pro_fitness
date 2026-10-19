<script setup lang="ts">
import { dayTime, duration, km, pace, SPORT_LABELS } from '@/format'
import type { Run } from '@/types'

defineProps<{ runs: Run[] }>()
</script>

<template>
  <div class="runs">
    <table v-if="runs.length" class="runs__table">
      <thead>
        <tr>
          <th scope="col">Date</th>
          <th scope="col">Run</th>
          <th scope="col" class="num">Distance</th>
          <th scope="col" class="num runs__optional">Time</th>
          <th scope="col" class="num">Pace</th>
          <th scope="col" class="num runs__wide">Elevation</th>
          <th scope="col" class="num runs__wide">Heart rate</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="run in runs" :key="run.id">
          <td class="runs__date">{{ dayTime(run.startTime) }}</td>
          <td class="runs__name">
            <a :href="run.stravaUrl" target="_blank" rel="noopener" title="View on Strava">{{ run.name }}</a>
            <span v-if="run.sportType !== 'Run'" class="runs__sport">{{ SPORT_LABELS[run.sportType] }}</span>
          </td>
          <td class="num">{{ km(run.distanceKm) }} km</td>
          <td class="num runs__optional">{{ duration(run.movingTimeSeconds) }}</td>
          <td class="num">{{ pace(run.paceSecondsPerKm) }}</td>
          <td class="num runs__wide">
            {{ run.elevationGainMeters === null ? '–' : `${Math.round(run.elevationGainMeters)} m` }}
          </td>
          <td class="num runs__wide">
            {{ run.averageHeartrate === null ? '–' : `${Math.round(run.averageHeartrate)} bpm` }}
          </td>
        </tr>
      </tbody>
    </table>
    <p v-else class="runs__empty">No runs synced yet.</p>
  </div>
</template>

<style scoped>
.runs {
  container-type: inline-size;
  background-color: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
}

.runs__table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.9rem;
}

.runs__table th,
.runs__table td {
  padding: 10px 12px;
  text-align: left;
  vertical-align: top;
  border-bottom: 1px solid var(--color-border);
}

.runs__table tbody tr:last-child td {
  border-bottom: none;
}

.runs__table th {
  font-size: 0.72rem;
  font-weight: 600;
  letter-spacing: 0.02em;
  text-transform: uppercase;
  color: var(--color-text-secondary);
}

.runs__table .num {
  text-align: right;
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}

.runs__date {
  white-space: nowrap;
  color: var(--color-text-secondary);
}

.runs__name a {
  font-weight: 500;
  color: var(--color-text-primary);
  text-decoration: none;
  overflow-wrap: anywhere;
}

.runs__name a:hover {
  color: var(--color-accent);
  text-decoration: underline;
}

.runs__sport {
  display: block;
  font-size: 0.8rem;
  color: var(--color-text-secondary);
}

.runs__empty {
  padding: 16px;
  color: var(--color-text-secondary);
}

@container (max-width: 720px) {
  .runs__wide {
    display: none;
  }
}

/* Phones: date above the name, distance and pace only. */
@container (max-width: 480px) {
  .runs__optional,
  .runs__table thead {
    display: none;
  }

  .runs__table th,
  .runs__table td {
    padding: 8px;
  }

  .runs__table tbody tr {
    display: grid;
    grid-template-columns: 1fr auto auto;
    column-gap: 12px;
    border-bottom: 1px solid var(--color-border);
  }

  .runs__table tbody tr:last-child {
    border-bottom: none;
  }

  .runs__table tbody td {
    border-bottom: none;
  }

  .runs__table tbody .runs__date {
    grid-column: 1 / -1;
    padding-bottom: 0;
    font-size: 0.8rem;
  }
}
</style>
