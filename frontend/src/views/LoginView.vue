<script setup>
import { ref } from 'vue'
import { fetchCurrentUser, login } from '../api/auth.js'
import { setSessionUser } from '../auth/session.js'
import { push } from '../router.js'

const username = ref('')
const password = ref('')
const error = ref('')
const submitting = ref(false)

async function submit() {
  if (submitting.value) {
    return
  }
  error.value = ''
  submitting.value = true
  try {
    await login({ username: username.value.trim(), password: password.value })
    setSessionUser(await fetchCurrentUser())
    push('/tickets')
  } catch (err) {
    error.value = err.message || '登录失败'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <section class="auth-card">
    <h1>SmartServe AI</h1>
    <p class="subtitle">企业客户服务与售后平台</p>
    <form class="form" @submit.prevent="submit">
      <label>
        用户名
        <input v-model="username" name="username" autocomplete="username" required minlength="3" />
      </label>
      <label>
        密码
        <input v-model="password" type="password" name="password" autocomplete="current-password" required minlength="8" />
      </label>
      <p v-if="error" class="error">{{ error }}</p>
      <button type="submit" class="primary" :disabled="submitting">
        {{ submitting ? '登录中…' : '登录' }}
      </button>
    </form>
    <p class="hint">
      还没有账号？
      <a href="#/register">注册客户账号</a>
    </p>
  </section>
</template>
