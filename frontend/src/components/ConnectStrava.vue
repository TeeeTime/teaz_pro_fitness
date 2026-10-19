<script setup lang="ts">
import { Icon } from '@teeetime/teaz-pro-ui'

defineProps<{
  /** Local dev: the backend makes up runs instead of asking Strava. */
  sample: boolean
  busy: boolean
}>()

defineEmits<{ connect: [] }>()
</script>

<template>
  <section class="connect">
    <h1 class="view__title connect__title">
      <Icon name="run" class="connect__icon" />
      Connect Strava
    </h1>
    <p class="connect__text">
      Fitness shows your latest runs and your weekly distance from Strava, here and in Portal. Strava asks
      you to allow read access to your activities; rides and other sports are left out.
    </p>
    <p v-if="sample" class="connect__text">
      No Strava app is configured on this backend, so connecting uses made-up sample runs.
    </p>
    <button type="button" class="button button--primary" :disabled="busy" @click="$emit('connect')">
      Connect with Strava
    </button>
  </section>
</template>

<style scoped>
.connect__title {
  display: flex;
  align-items: center;
  gap: 10px;
}

.connect__icon {
  width: 28px;
  height: 28px;
  color: var(--color-text-secondary);
}

.connect {
  max-width: 560px;
  padding: 24px;
  background-color: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
}

.connect__text {
  margin: 8px 0 16px;
  color: var(--color-text-secondary);
}

.connect__text + .connect__text {
  margin-top: -8px;
}
</style>
