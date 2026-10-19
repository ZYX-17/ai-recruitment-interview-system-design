<template>
  <div class="login-page">
    <el-card class="login-card">
      <h2>招聘面试系统</h2>
      <el-form :model="form" label-width="0">
        <el-form-item>
          <el-input v-model="form.username" placeholder="用户名" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="form.password" type="password" placeholder="密码" show-password />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" style="width: 100%" :loading="loading" @click="onLogin">
            登录
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()
const loading = ref(false)
const form = reactive({ username: '', password: '' })

// TODO(W2)：POST /api/auth/login → 双令牌 → 动态菜单注册 → 按角色跳首页
async function onLogin() {
  loading.value = true
  try {
    localStorage.setItem('access_token', 'skeleton-token') // 骨架占位
    router.push('/')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100vh;
  background: #f5f7fa;
}
.login-card {
  width: 380px;
}
</style>
