<template>
  <Transition name="route-loading-fade">
    <div
      v-if="visible"
      class="route-loading"
      role="status"
      aria-live="polite"
      aria-busy="true"
    >
      <div class="route-loading__backdrop" />
      <div class="route-loading__panel">
        <div class="route-loading__spinner" aria-hidden="true">
          <span />
          <span />
          <span />
        </div>
        <div class="route-loading__copy">
          <div class="route-loading__eyebrow">{{ eyebrowLabel }}</div>
          <h2 class="route-loading__title">{{ title }}</h2>
          <p class="route-loading__description">{{ description }}</p>
        </div>
      </div>
    </div>
  </Transition>
</template>

<script setup lang="ts">
const eyebrowLabel = '\u8bf7\u7a0d\u5019'
const defaultTitle = '\u6b63\u5728\u8fdb\u5165\u9875\u9762'
const defaultDescription = '\u754c\u9762\u5185\u5bb9\u6b63\u5728\u51c6\u5907\u4e2d\uff0c\u8bf7\u7a0d\u5019\u3002'

withDefaults(
  defineProps<{
    visible: boolean
    title?: string
    description?: string
  }>(),
  {
    title: defaultTitle,
    description: defaultDescription,
  },
)
</script>

<style scoped>
.route-loading {
  position: fixed;
  inset: 0;
  z-index: 2200;
  display: grid;
  place-items: center;
  padding: 24px;
}

.route-loading__backdrop {
  position: absolute;
  inset: 0;
  background:
    linear-gradient(180deg, rgba(15, 23, 42, 0.14), rgba(15, 23, 42, 0.22)),
    rgba(255, 255, 255, 0.36);
  backdrop-filter: blur(10px);
}

.route-loading__panel {
  position: relative;
  width: min(100%, 420px);
  display: flex;
  align-items: center;
  gap: 18px;
  padding: 22px 24px;
  border-radius: var(--radius-lg, 24px);
  border: 1px solid var(--line);
  background: var(--card);
  box-shadow: 0 28px 60px rgba(15, 23, 42, 0.16);
}

.route-loading__spinner {
  position: relative;
  width: 56px;
  height: 56px;
  flex: none;
}

.route-loading__spinner span {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  border: 3px solid transparent;
  border-top-color: var(--accent);
  animation: route-loading-spin 1s linear infinite;
}

.route-loading__spinner span:nth-child(2) {
  inset: 7px;
  border-top-color: rgba(37, 99, 235, 0.42);
  animation-duration: 1.25s;
  animation-direction: reverse;
}

.route-loading__spinner span:nth-child(3) {
  inset: 16px;
  border-width: 4px;
  border-top-color: rgba(37, 99, 235, 0.18);
  animation-duration: 1.6s;
}

.route-loading__copy {
  min-width: 0;
}

.route-loading__eyebrow {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: var(--muted);
}

.route-loading__title {
  margin: 8px 0 0;
  font-size: 22px;
  font-weight: 700;
  letter-spacing: -0.03em;
  color: var(--text);
}

.route-loading__description {
  margin: 8px 0 0;
  line-height: 1.7;
  color: var(--muted);
}

.route-loading-fade-enter-active,
.route-loading-fade-leave-active {
  transition: opacity 0.2s ease;
}

.route-loading-fade-enter-from,
.route-loading-fade-leave-to {
  opacity: 0;
}

@keyframes route-loading-spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 640px) {
  .route-loading__panel {
    align-items: flex-start;
    gap: 14px;
    padding: 18px;
  }

  .route-loading__spinner {
    width: 48px;
    height: 48px;
  }

  .route-loading__title {
    font-size: 18px;
  }
}
</style>
