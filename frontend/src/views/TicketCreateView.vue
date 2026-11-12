<script setup>
import { ref } from 'vue'
import { createTicket } from '../api/tickets.js'
import { isCustomer } from '../auth/session.js'
import { CATEGORY_OPTIONS, PRIORITY_OPTIONS } from '../ticket/labels.js'
import { push } from '../router.js'

const title = ref('')
const description = ref('')
const category = ref('HARDWARE')
const priority = ref('MEDIUM')
const error = ref('')
const submitting = ref(false)

async function submit() {
  if (submitting.value || !isCustomer.value) {
    return
  }
  error.value = ''
  submitting.value = true
  try {
    const ticket = await createTicket({
      title: title.value.trim(),
      description: description.value.trim(),
      category: category.value,
      priority: priority.value,
    })
    push(`/tickets/${ticket.id}`)
  } catch (err) {
    error.value = err.message || '创建失败'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <section>
    <header class="page-head">
      <div>
        <h1>创建售后工单</h1>
        <p class="subtitle">提交后状态为新建，等待管理员分配客服</p>
      </div>
      <button type="button" @click="push('/tickets')">返回列表</button>
    </header>

    <p v-if="!isCustomer" class="error">仅客户可以创建工单</p>
    <form v-else class="form panel" @submit.prevent="submit">
      <label>
        标题
        <input v-model="title" required maxlength="128" />
      </label>
      <label>
        问题类型
        <select v-model="category" required>
          <option v-for="option in CATEGORY_OPTIONS" :key="option.value" :value="option.value">
            {{ option.label }}
          </option>
        </select>
      </label>
      <label>
        优先级
        <select v-model="priority" required>
          <option v-for="option in PRIORITY_OPTIONS.filter((item) => item.value)" :key="option.value" :value="option.value">
            {{ option.label }}
          </option>
        </select>
      </label>
      <label>
        问题描述
        <textarea v-model="description" rows="6" required></textarea>
      </label>
      <p v-if="error" class="error">{{ error }}</p>
      <button type="submit" class="primary" :disabled="submitting">
        {{ submitting ? '提交中…' : '提交工单' }}
      </button>
    </form>
  </section>
</template>
