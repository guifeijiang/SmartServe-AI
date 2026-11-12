<script setup>
import { ref } from 'vue'
import { register } from '../api/auth.js'
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
    await register({ username: username.value.trim(), password: password.value })
    push('/login')
  } catch (err) {
    error.value = err.message || '注册失败'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <section class="auth-card">
    <h1>注册客户账号</h1>
    <p class="subtitle">新账号角色固定为 CUSTOMER，不可自行修改</p>
    <form class="form" @submit.prevent="submit">
      <label>
        用户名
        <input v-model="username" required minlength="3" maxlength="64" pattern="^[a-zA-Z0-9_]+$" />
      </label>
      <label>
        密码
        <input v-model="password" type="password" required minlength="8" maxlength="72" />
      </label>
      <p v-if="error" class="error">{{ error }}</p>
      <button type="submit" class="primary" :disabled="submitting">
        {{ submitting ? '提交中…' : '注册' }}
      </button>
    </form>
    <p class="hint">
      已有账号？
      <a href="#/login">返回登录</a>
    </p>
  </section>
</template>
