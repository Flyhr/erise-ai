<template>
  <div class="shell" style="display: grid; place-items: center; min-height: 100vh; padding: 24px;">
    <section class="app-card" style="width: min(420px, 100%);">
      <div class="app-card__body" style="text-align: center; display: flex; flex-direction: column; gap: 14px;">
        <div class="app-eyebrow">404</div>
        <h1 style="margin: 0; font-size: 42px; letter-spacing: -0.05em;">页面不存在</h1>
        <p class="page-subtitle" style="margin: 0;">你访问的后台页面不存在，可能已经被移动，或者你没有对应权限。</p>
        <div class="table-actions" style="justify-content: center;">
          <el-button plain @click="$router.push('/workspace')">返回工作台</el-button>
          <!-- <el-button type="primary" @click="$router.push('/projects')">前往项目中心</el-button> -->
        </div>
      </div>
    </section>
  </div>
</template>
