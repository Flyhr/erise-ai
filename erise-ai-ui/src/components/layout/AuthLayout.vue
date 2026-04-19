<template>
  <div class="auth-shell">
    <section class="auth-shell__brand">
      <div class="app-brand-mark">E</div>
      <div class="app-eyebrow">Erise </div>
      <h1 class="auth-shell__title">把项目、文件、文档与 AI 协作汇聚到一个可靠入口</h1>
      <p class="auth-shell__copy">
        登录后即可创建项目空间、上传资料、在线编辑文档与 txt 文件，并基于知识库发起可追溯引用的 AI 对话。
      </p>
      <div class="auth-shell__highlights">
        <div class="auth-shell__highlight">全局文件与文档</div>
        <div class="auth-shell__highlight">项目二级导航</div>
        <div class="auth-shell__highlight">可追溯 AI 引用</div>
      </div>
    </section>

    <section class="auth-shell__card app-card">
      <div class="app-card__body auth-shell__card-body">
        <router-view />
      </div>
    </section>
  </div>
</template>

<style scoped>
.auth-shell {
  min-height: 100vh;
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(420px, 460px);
  gap: 28px;
  padding: 32px;
  align-items: center;
}

.auth-shell__brand {
  padding: 24px 8px 24px 24px;
}

.auth-shell__title {
  margin: 16px 0 0;
  font-size: clamp(34px, 4vw, 56px);
  line-height: 1.06;
  letter-spacing: -0.05em;
}

.auth-shell__copy {
  max-width: 680px;
  margin-top: 18px;
  color: var(--muted);
  font-size: 16px;
  line-height: 1.8;
}

.auth-shell__highlights {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  margin-top: 28px;
}

.auth-shell__highlight {
  min-height: 40px;
  padding: 0 14px;
  display: inline-flex;
  align-items: center;
  border-radius: 999px;
  background: rgba(35, 88, 214, 0.08);
  border: 1px solid rgba(35, 88, 214, 0.12);
  color: var(--accent);
  font-weight: 600;
}

.auth-shell__card {
  width: min(460px, 100%);
  justify-self: end;
}

.auth-shell__card-body {
  padding: 30px;
}

@media (max-width: 1100px) {
  .auth-shell {
    grid-template-columns: 1fr;
  }

  .auth-shell__brand {
    padding: 0;
  }

  .auth-shell__card {
    width: 100%;
    justify-self: stretch;
  }
}

@media (max-width: 720px) {
  .auth-shell {
    padding: 18px;
  }

  .auth-shell__card-body {
    padding: 22px;
  }
}
</style>
