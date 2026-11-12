<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { listTickets } from '../api/tickets.js'
import { isAdmin, isCustomer, userRole } from '../auth/session.js'
import {
  formatTime,
  PRIORITY_OPTIONS,
  priorityLabel,
  roleHomeTitle,
  STATUS_OPTIONS,
  statusLabel,
} from '../ticket/labels.js'
import { push } from '../router.js'

const loading = ref(false)
const error = ref('')
const page = ref(1)
const size = ref(10)
const total = ref(0)
const items = ref([])
const statusFilter = ref('')
const priorityFilter = ref('')

const title = computed(() => roleHomeTitle(userRole.value))
const subtitle = computed(() => {
  if (isAdmin.value) {
    return '查看全部工单，可按状态和优先级筛选当前页结果'
  }
  if (userRole.value === 'AGENT') {
    return '仅显示分配给当前客服的工单'
  }
  return '仅显示当前客户自己的工单'
})
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size.value)))
const visibleItems = computed(() => {
  return items.value.filter((ticket) => {
    if (statusFilter.value && ticket.status !== statusFilter.value) {
      return false
    }
    if (priorityFilter.value && ticket.priority !== priorityFilter.value) {
      return false
    }
    return true
  })
})

async function load() {
  loading.value = true
  error.value = ''
  try {
    const data = await listTickets(page.value, size.value)
    items.value = data.items || []
    total.value = data.total || 0
    page.value = data.page || page.value
    size.value = data.size || size.value
  } catch (err) {
    items.value = []
    error.value = err.message || '加载工单失败'
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
watch(userRole, load)
</script>

<template>
  <section>
    <header class="page-head">
      <div>
        <h1>{{ title }}</h1>
        <p class="subtitle">{{ subtitle }}</p>
      </div>
      <button v-if="isCustomer" class="primary" type="button" @click="push('/tickets/new')">创建工单</button>
    </header>

    <div v-if="isAdmin" class="filters">
      <label>
        状态
        <select v-model="statusFilter">
          <option v-for="option in STATUS_OPTIONS" :key="option.value || 'all-status'" :value="option.value">
            {{ option.label }}
          </option>
        </select>
      </label>
      <label>
        优先级
        <select v-model="priorityFilter">
          <option v-for="option in PRIORITY_OPTIONS" :key="option.value || 'all-priority'" :value="option.value">
            {{ option.label }}
          </option>
        </select>
      </label>
    </div>

    <p v-if="loading" class="hint">正在加载工单…</p>
    <p v-else-if="error" class="error">{{ error }}</p>
    <p v-else-if="visibleItems.length === 0" class="empty">暂无工单</p>

    <table v-else class="table">
      <thead>
        <tr>
          <th>工单编号</th>
          <th>标题</th>
          <th>状态</th>
          <th>优先级</th>
          <th>创建时间</th>
          <th></th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="ticket in visibleItems" :key="ticket.id">
          <td>{{ ticket.ticketNo }}</td>
          <td>{{ ticket.title }}</td>
          <td>{{ statusLabel(ticket.status) }}</td>
          <td>{{ priorityLabel(ticket.priority) }}</td>
          <td>{{ formatTime(ticket.createdAt) }}</td>
          <td>
            <button type="button" @click="push(`/tickets/${ticket.id}`)">详情</button>
          </td>
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
