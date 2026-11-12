<script setup>
import { computed, ref, watch } from 'vue'
import {
  assignTicket,
  claimTicket,
  closeTicket,
  getTicket,
  handleTicket,
  listTicketOperations,
  resolveTicket,
} from '../api/tickets.js'
import { currentUser, isAdmin, isAgent, isCustomer } from '../auth/session.js'
import {
  categoryLabel,
  formatTime,
  operationLabel,
  priorityLabel,
  statusLabel,
} from '../ticket/labels.js'
import { route, push } from '../router.js'

const loading = ref(false)
const error = ref('')
const actionError = ref('')
const ticket = ref(null)
const operations = ref([])
const remark = ref('')
const agentUserId = ref('')
const submitting = ref('')

const ticketId = computed(() => route.params.id)

const canClaim = computed(() => {
  const item = ticket.value
  return isAgent.value && item?.status === 'ASSIGNED' && item.assignedAgentId === currentUser.value?.id
})
const canHandle = computed(() => {
  const item = ticket.value
  return isAgent.value && item?.status === 'IN_PROGRESS' && item.assignedAgentId === currentUser.value?.id
})
const canResolve = computed(() => canHandle.value)
const canClose = computed(() => isCustomer.value && ticket.value?.status === 'RESOLVED')
const canAssign = computed(() => isAdmin.value && ticket.value?.status === 'NEW')

async function load() {
  if (!ticketId.value) {
    return
  }
  loading.value = true
  error.value = ''
  actionError.value = ''
  try {
    const [detail, logs] = await Promise.all([
      getTicket(ticketId.value),
      listTicketOperations(ticketId.value),
    ])
    ticket.value = detail
    operations.value = logs || []
  } catch (err) {
    ticket.value = null
    operations.value = []
    error.value = err.message || '加载工单失败'
  } finally {
    loading.value = false
  }
}

async function runAction(name, task) {
  if (submitting.value) {
    return
  }
  actionError.value = ''
  submitting.value = name
  try {
    await task()
    remark.value = ''
    await load()
  } catch (err) {
    actionError.value = err.message || '操作失败'
  } finally {
    submitting.value = ''
  }
}

function onClaim() {
  return runAction('claim', () => claimTicket(ticketId.value))
}

function onHandle() {
  return runAction('handle', () => handleTicket(ticketId.value, remark.value.trim()))
}

function onResolve() {
  return runAction('resolve', () => resolveTicket(ticketId.value, remark.value.trim()))
}

function onClose() {
  return runAction('close', () => closeTicket(ticketId.value, remark.value.trim() || '确认关闭'))
}

function onAssign() {
  const agentId = Number(agentUserId.value)
  if (!agentId) {
    actionError.value = '请填写客服用户 ID'
    return
  }
  return runAction('assign', () => assignTicket(ticketId.value, agentId))
}

watch(ticketId, load, { immediate: true })
</script>

<template>
  <section>
    <header class="page-head">
      <div>
        <h1>工单详情</h1>
        <p class="subtitle">处理进度与操作时间线来自真实后端数据</p>
      </div>
      <button type="button" @click="push('/tickets')">返回列表</button>
    </header>

    <p v-if="loading" class="hint">正在加载详情…</p>
    <p v-else-if="error" class="error">{{ error }}</p>

    <div v-else-if="ticket" class="detail-grid">
      <article class="panel">
        <h2>{{ ticket.title }}</h2>
        <dl class="meta">
          <div><dt>工单编号</dt><dd>{{ ticket.ticketNo }}</dd></div>
          <div><dt>状态</dt><dd>{{ statusLabel(ticket.status) }}</dd></div>
          <div><dt>优先级</dt><dd>{{ priorityLabel(ticket.priority) }}</dd></div>
          <div><dt>类型</dt><dd>{{ categoryLabel(ticket.category) }}</dd></div>
          <div><dt>客户 ID</dt><dd>{{ ticket.customerUserId }}</dd></div>
          <div><dt>客服 ID</dt><dd>{{ ticket.assignedAgentId || '未分配' }}</dd></div>
          <div><dt>创建时间</dt><dd>{{ formatTime(ticket.createdAt) }}</dd></div>
          <div><dt>更新时间</dt><dd>{{ formatTime(ticket.updatedAt) }}</dd></div>
          <div><dt>解决时间</dt><dd>{{ formatTime(ticket.resolvedAt) }}</dd></div>
        </dl>
        <h3>问题描述</h3>
        <p class="description">{{ ticket.description }}</p>
      </article>

      <article class="panel">
        <h2>处理操作</h2>
        <label v-if="canHandle || canResolve || canClose">
          备注
          <textarea v-model="remark" rows="3" maxlength="512" placeholder="处理说明或关闭确认"></textarea>
        </label>
        <label v-if="canAssign">
          客服用户 ID
          <input v-model="agentUserId" type="number" min="1" placeholder="例如 4" />
        </label>
        <p v-if="actionError" class="error">{{ actionError }}</p>
        <div class="actions">
          <button v-if="canAssign" type="button" class="primary" :disabled="Boolean(submitting)" @click="onAssign">
            {{ submitting === 'assign' ? '分配中…' : '分配给客服' }}
          </button>
          <button v-if="canClaim" type="button" class="primary" :disabled="Boolean(submitting)" @click="onClaim">
            {{ submitting === 'claim' ? '接单中…' : '接单' }}
          </button>
          <button v-if="canHandle" type="button" :disabled="Boolean(submitting)" @click="onHandle">
            {{ submitting === 'handle' ? '提交中…' : '开始处理 / 填写备注' }}
          </button>
          <button v-if="canResolve" type="button" class="primary" :disabled="Boolean(submitting)" @click="onResolve">
            {{ submitting === 'resolve' ? '提交中…' : '标记解决' }}
          </button>
          <button v-if="canClose" type="button" class="primary" :disabled="Boolean(submitting)" @click="onClose">
            {{ submitting === 'close' ? '关闭中…' : '关闭工单' }}
          </button>
          <p v-if="!canAssign && !canClaim && !canHandle && !canClose" class="hint">当前状态无需操作，可查看下方进度。</p>
        </div>
      </article>
    </div>

    <article v-if="ticket" class="panel">
      <h2>处理进度</h2>
      <ol v-if="operations.length" class="timeline">
        <li v-for="item in operations" :key="item.id">
          <strong>{{ operationLabel(item.operationType) }}</strong>
          <span>{{ formatTime(item.createdAt) }}</span>
          <p>
            {{ item.fromStatus ? statusLabel(item.fromStatus) : '无' }}
            →
            {{ item.toStatus ? statusLabel(item.toStatus) : '无' }}
            · 操作人 {{ item.operatorUserId }}
          </p>
          <p v-if="item.remark">{{ item.remark }}</p>
        </li>
      </ol>
      <p v-else class="empty">暂无操作记录</p>
    </article>
  </section>
</template>
