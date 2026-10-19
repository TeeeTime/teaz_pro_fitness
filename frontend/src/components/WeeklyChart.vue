<script setup lang="ts">
import { computed, ref } from 'vue'
import { duration, km, weekLabel } from '@/format'
import type { WeeklyDistance } from '@/types'

const props = defineProps<{
  weekly: WeeklyDistance
  /** A new range is loading: keep showing the old one, dimmed. */
  loading?: boolean
}>()

/*
 * One series (kilometers per week), so no legend: the card's title names it.
 * Per the dataviz skill: thin bars (at most 24px) from one baseline, accent
 * fill, hairline gridlines, ticks on clean numbers, one direct label (this
 * week), a hover/focus tooltip per bar with the value first, and a table
 * view. Bars keep square ends: the house rule (no rounded corners) wins over
 * the skill's rounded data-ends here.
 */

const weeks = computed(() => props.weekly.weeks)

// About four gridlines on 1/2/5 steps.
const scale = computed(() => {
  const max = Math.max(...weeks.value.map((w) => w.distanceKm), 0)
  if (max === 0) return { top: 10, ticks: [0, 5, 10] }
  const rough = max / 4
  const magnitude = 10 ** Math.floor(Math.log10(rough))
  const step = [1, 2, 5, 10].map((m) => m * magnitude).find((s) => s >= rough)!
  const top = Math.ceil(max / step) * step
  const ticks: number[] = []
  for (let t = 0; t <= top + step / 2; t += step) ticks.push(Math.round(t * 100) / 100)
  return { top, ticks }
})

function percent(value: number): string {
  return `${(value / scale.value.top) * 100}%`
}

// Fewer x labels the more weeks are shown; "minor" ones also go at narrow widths. Counted from
// the current week, so it is always labeled.
const labelStep = computed(() => (weeks.value.length <= 12 ? 1 : weeks.value.length <= 26 ? 2 : 4))

function labelClass(index: number): string | null {
  const fromEnd = weeks.value.length - 1 - index
  if (fromEnd % (labelStep.value * 2) === 0) return 'chart__xlabel--major'
  if (fromEnd % labelStep.value === 0) return 'chart__xlabel--minor'
  return null
}

function isCurrent(index: number): boolean {
  return index === weeks.value.length - 1
}

function description(index: number): string {
  const w = weeks.value[index]!
  const when = isCurrent(index) ? 'This week so far' : `Week of ${weekLabel(w.weekStart)}`
  return `${when}: ${km(w.distanceKm)} km, ${w.runs} ${w.runs === 1 ? 'run' : 'runs'}`
}

const active = ref<number | null>(null)
</script>

<template>
  <figure class="chart" :class="{ 'chart--loading': loading }">
    <div class="chart__plot">
      <div class="chart__grid" aria-hidden="true">
        <div v-for="tick in scale.ticks" :key="tick" class="chart__gridline" :style="{ bottom: percent(tick) }">
          <span class="chart__tick">{{ tick }}</span>
        </div>
      </div>

      <div class="chart__bars" @pointerleave="active = null">
        <div
          v-for="(week, i) in weeks"
          :key="week.weekStart"
          class="chart__column"
          :class="{ 'chart__column--active': active === i }"
          tabindex="0"
          :aria-label="description(i)"
          @pointerenter="active = i"
          @focus="active = i"
          @blur="active = null"
        >
          <div class="chart__bar" :style="{ height: percent(week.distanceKm) }" />
          <span v-if="isCurrent(i) && active === null" class="chart__value" :style="{ bottom: percent(week.distanceKm) }">
            {{ km(week.distanceKm) }}
          </span>

          <div
            v-if="active === i"
            class="chart__tooltip"
            :class="{
              'chart__tooltip--start': i < weeks.length / 4,
              'chart__tooltip--end': i >= (weeks.length * 3) / 4,
            }"
            :style="{ bottom: percent(week.distanceKm) }"
            role="presentation"
          >
            <strong class="chart__tooltip-value">{{ km(week.distanceKm) }} km</strong>
            <span>{{ week.runs }} {{ week.runs === 1 ? 'run' : 'runs' }} · {{ duration(week.movingTimeSeconds) }}</span>
            <span>{{ isCurrent(i) ? 'This week so far' : `Week of ${weekLabel(week.weekStart)}` }}</span>
          </div>
        </div>
      </div>
    </div>

    <div class="chart__xaxis" aria-hidden="true">
      <span v-for="(week, i) in weeks" :key="week.weekStart" class="chart__xlabel" :class="labelClass(i)">
        {{ labelClass(i) ? (isCurrent(i) ? 'Now' : weekLabel(week.weekStart)) : '' }}
      </span>
    </div>

    <figcaption class="chart__caption">
      <span>This week <strong>{{ km(weekly.thisWeekKm) }} km</strong></span>
      <span>Last week <strong>{{ km(weekly.lastWeekKm) }} km</strong></span>
      <span v-if="weekly.averageKm !== null">Average <strong>{{ km(weekly.averageKm) }} km</strong></span>
    </figcaption>

    <details class="chart__table">
      <summary>Show as table</summary>
      <table>
        <thead>
          <tr>
            <th scope="col">Week of</th>
            <th scope="col" class="num">Distance</th>
            <th scope="col" class="num">Runs</th>
            <th scope="col" class="num">Time</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="week in [...weeks].reverse()" :key="week.weekStart">
            <td>{{ weekLabel(week.weekStart) }}</td>
            <td class="num">{{ km(week.distanceKm) }} km</td>
            <td class="num">{{ week.runs }}</td>
            <td class="num">{{ duration(week.movingTimeSeconds) }}</td>
          </tr>
        </tbody>
      </table>
    </details>
  </figure>
</template>

<style scoped>
.chart {
  container-type: inline-size;
  margin: 16px 0 0;
  transition: opacity var(--transition-fast);
}

.chart--loading {
  opacity: 0.5;
}

/* Left padding: room for the y tick labels. Top: room for the topmost tick label and value label. */
.chart__plot {
  position: relative;
  height: 220px;
  margin: 12px 0 0 32px;
}

.chart__grid {
  position: absolute;
  inset: 0;
}

.chart__gridline {
  position: absolute;
  left: 0;
  right: 0;
  border-top: 1px solid var(--color-border);
}

.chart__tick {
  position: absolute;
  right: calc(100% + 6px);
  transform: translateY(-50%);
  font-size: 0.72rem;
  font-variant-numeric: tabular-nums;
  color: var(--color-text-secondary);
}

.chart__bars {
  position: absolute;
  inset: 0;
  display: flex;
  gap: 2px;
}

.chart__column {
  position: relative;
  flex: 1 1 0;
  min-width: 0;
  display: flex;
  justify-content: center;
  align-items: flex-end;
  cursor: default;
  outline: none;
}

.chart__column:focus-visible {
  background-color: var(--color-surface-hover);
}

.chart__bar {
  width: min(24px, 70%);
  background-color: var(--color-accent);
  border-radius: var(--radius-sm);
  transition: background-color var(--transition-fast);
}

.chart__column--active .chart__bar {
  background-color: var(--color-accent-hover);
}

.chart__value {
  position: absolute;
  margin-bottom: 4px;
  font-size: 0.75rem;
  font-weight: 600;
  white-space: nowrap;
  color: var(--color-text-primary);
}

.chart__tooltip {
  position: absolute;
  left: 50%;
  z-index: 1;
  display: flex;
  flex-direction: column;
  margin-bottom: 8px;
  padding: 6px 10px;
  font-size: 0.75rem;
  white-space: nowrap;
  color: var(--color-text-secondary);
  background-color: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  box-shadow: 0 4px 12px var(--color-shadow-strong);
  transform: translateX(-50%);
  pointer-events: none;
}

.chart__tooltip--start {
  left: 0;
  transform: none;
}

.chart__tooltip--end {
  left: auto;
  right: 0;
  transform: none;
}

.chart__tooltip-value {
  font-size: 0.9rem;
  font-weight: 600;
  color: var(--color-text-primary);
}

.chart__xaxis {
  display: flex;
  gap: 2px;
  margin: 6px 0 0 32px;
}

.chart__xlabel {
  flex: 1 1 0;
  min-width: 0;
  overflow: visible;
  font-size: 0.72rem;
  text-align: center;
  white-space: nowrap;
  color: var(--color-text-secondary);
}

@container (max-width: 520px) {
  .chart__xlabel--minor {
    visibility: hidden;
  }
}

.chart__caption {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 20px;
  margin-top: 16px;
  font-size: 0.85rem;
  color: var(--color-text-secondary);
}

.chart__caption strong {
  font-weight: 600;
  color: var(--color-text-primary);
}

.chart__table {
  margin-top: 12px;
  font-size: 0.85rem;
}

.chart__table summary {
  cursor: pointer;
  color: var(--color-text-secondary);
}

.chart__table table {
  width: 100%;
  margin-top: 8px;
  border-collapse: collapse;
}

.chart__table th,
.chart__table td {
  padding: 4px 8px 4px 0;
  text-align: left;
  border-bottom: 1px solid var(--color-border);
}

.chart__table th {
  font-weight: 600;
  color: var(--color-text-secondary);
}

.chart__table .num {
  text-align: right;
  font-variant-numeric: tabular-nums;
}
</style>
