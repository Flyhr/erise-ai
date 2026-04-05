<template>
  <div class="app-data-table">
    <el-table :data="data" class="app-data-table__table" v-bind="$attrs">
      <slot />
    </el-table>
  </div>
</template>

<script setup lang="ts">
defineOptions({ inheritAttrs: false })

defineProps<{
  data: unknown[]
}>()
</script>
