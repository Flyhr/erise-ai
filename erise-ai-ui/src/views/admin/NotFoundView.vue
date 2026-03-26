<template>
  <div class="shell" style="display: grid; place-items: center">
    <div class="glass-card" style="padding: 48px; text-align: center">
      <h1>404</h1>
      <p class="page-subtitle">页面不存在。</p>
      <el-button type="primary" @click="$router.push('/workspace')">返回首页</el-button>
    </div>
  </div>
</template>
