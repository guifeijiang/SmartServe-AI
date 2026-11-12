<script setup>
import { computed, onMounted, ref } from 'vue'
import { listMyNotifications } from '../api/notifications.js'
import { formatTime } from '../ticket/labels.js'
import { push } from '../router.js'

const loading = ref(false)
const error = ref('')
const page = ref(1)
const size = ref(10)
const total = ref(0)
const items = ref([])

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size.value)))

async function load() {
  loading.value = true
  error.value = ''
  try {
    const data = await listMyNotifications(page.value, size.value)
    items.value = data.items || []
    total.value = data.total || 0
    page.value = data.page || page.value
    size.value = data.size || size.value
  } catch (err) {
    items.value = []
    error.value = err.message || '加载通知失败'
  } finally {
    loading.value = false
  }
}

function prevPage() {
  if (page.value > 1) {
    page.value -= 1
    load()
  }
}

function nextPage() {
  if (page.value < totalPages.value) {
    page.value += 1
    load()
  }
}

onMounted(load)
</script>

<template>
  <section>
    <header class="page-head">
      <div>
        <h1>我的通知</h1>
        <p class="subtitle">仅显示当前登录用户的工单创建通知</p>
      </div>
    </header>

    <p v-if="loading" class="hint">正在加载通知…</p>
    <p v-else-if="error" class="error">{{ error }}</p>
    <p v-else-if="items.length === 0" class="empty">暂无通知</p>

    <table v-else class="table">
      <thead>
        <tr>
          <th>内容</th>
          <th>工单</th>
          <th>时间</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="item in items" :key="item.id">
          <td>{{ item.content }}</td>
          <td>
            <button v-if="item.ticketId" type="button" @click="push(`/tickets/${item.ticketId}`)">查看工单</button>
            <span v-else>-</span>
          </td>
          <td>{{ formatTime(item.createdAt) }}</td>
        </tr>
      </tbody>
    </table>

    <div class="pager">
      <button type="button" :disabled="loading || page <= 1" @click="prevPage">上一页</button>
      <span>第 {{ page }} / {{ totalPages }} 页，共 {{ total }} 条</span>
      <button type="button" :disabled="loading || page >= totalPages" @click="nextPage">下一页</button>
    </div>
  </section>
</template>
