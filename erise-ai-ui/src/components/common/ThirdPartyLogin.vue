<template>
  <div class="login-third">
    <span class="third-title">其他登录方式</span>
    <div class="third-icons">
      <div class="icon qq" @click="handleThirdLogin('QQ')">
        <img src="@/assets/picture/icon/QQ_login.svg" alt="QQ登录" class="icon-img" />
      </div>
      <div class="icon wechat" @click="handleThirdLogin('微信')">
        <img src="@/assets/picture/icon/weixin.svg" alt="微信登录" class="icon-img" />
      </div>
    </div>
    <div class="login-privacy">
      <a href="/privacy" target="_blank" class="privacy-link">《隐私协议》</a>
    </div>
  </div>
</template>

<script setup>
import { ElMessageBox } from 'element-plus'

const handleThirdLogin = (type) => {
  ElMessageBox.alert(`${type}登录功能暂未实现，敬请期待！`, '提示', {
    confirmButtonText: '确定',
    type: 'info',
  })
}
</script>

<style scoped>
.login-third {
  margin: 25px 0;
  text-align: center;
  position: relative;
}

.third-title {
  color: var(--text-secondary, #909399);
  font-size: 14px;
  position: relative;
  display: inline-block;
  padding: 0 10px;
  background-color: #fff;
  z-index: 1;
}

.third-title::before {
  content: "";
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translateX(-50%);
  width: 200px;
  height: 1px;
  background-color: var(--border-color, #e6e6e6);
  z-index: -1;
}

.third-icons {
  margin-top: 15px;
  display: flex;
  justify-content: center;
  gap: 30px;
}

.icon {
  width: 50px;
  height: 50px;
  cursor: pointer;
  transition: all 0.2s ease;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background-color: #f5f5f5;
}

.icon-img {
  width: 80%;
  height: 80%;
  object-fit: contain;
}

.qq:hover {
  background-color: #e8f3ff;
  transform: scale(1.05);
}

.wechat:hover {
  background-color: #eaf8ef;
  transform: scale(1.05);
}

.icon:hover {
  opacity: 0.8;
}

.login-privacy {
  text-align: center;
  font-size: 12px;
  margin-top: 12px;
}

.privacy-link {
  color: var(--primary-color, #409eff);
  text-decoration: none;
}

.privacy-link:hover {
  text-decoration: underline;
}
</style>
