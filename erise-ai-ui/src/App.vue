<template>
  <RouterView />
  <AppRouteLoading
    :visible="routeLoadingVisible"
    :title="routeLoadingState.title"
    :description="routeLoadingState.description"
  />
</template>

<script setup lang="ts">
import { nextTick, watch } from 'vue'
import { RouterView, useRoute } from 'vue-router'
import AppRouteLoading from '@/components/common/AppRouteLoading.vue'
import { useRouteLoading } from '@/composables/useRouteLoading'

const route = useRoute()
const routeLoading = useRouteLoading()
const routeLoadingState = routeLoading.state
const routeLoadingVisible = routeLoading.visible

watch(
  () => route.fullPath,
  async (fullPath) => {
    await nextTick()
    requestAnimationFrame(() => {
      routeLoading.resolve(fullPath)
    })
  },
  { immediate: true },
)
</script>
