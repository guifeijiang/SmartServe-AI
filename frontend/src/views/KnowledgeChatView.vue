<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import {
  askKnowledgeBase,
  createChatSession,
  getKnowledgeBase,
  listChatMessages,
  listChatSessions,
} from '../api/knowledge.js'
import { formatTime } from '../ticket/labels.js'
import { push, route } from '../router.js'

const kbId = computed(() => route.params.id)
const kb = ref(null)
const sessions = ref([])
const currentSessionId = ref(null)
const messages = ref([])
const draft = ref('')
const loadingKb = ref(false)
const loadingSessions = ref(false)
const loadingMessages = ref(false)
const sending = ref(false)
const error = ref('')
const logEl = ref(null)
let requestSeq = 0

const currentSession = computed(() => sessions.value.find((item) => item.id === currentSessionId.value) || null)

function isUser(role) {
  return role === 'USER'
}

function scoreText(score) {
  if (score == null || Number.isNaN(Number(score))) {
    return '-'
  }
  return Number(score).toFixed(4)
}

function modeText(diagnostics) {
  if (!diagnostics) {
    return ''
  }
  const mode = diagnostics.retrievalMode === 'FULLTEXT'
    ? 'MySQL 全文检索'
    : diagnostics.retrievalMode === 'VECTOR'
      ? '向量检索'
      : '无召回'
  const index = diagnostics.indexType ? `（${diagnostics.indexType}）` : ''
  return mode + index
}

function degradeText(diagnostics) {
  if (!diagnostics?.degraded) {
    return '未降级'
  }
  const reasons = {
    LOW_SIMILARITY: '相似度低于阈值',
    VECTOR_EMPTY: '向量无召回',
    VECTOR_UNAVAILABLE: '向量服务不可用',
  }
  return '已降级：' + (reasons[diagnostics.degradeReason] || diagnostics.degradeReason || '未知原因')
}

function answerStatusText(diagnostics) {
  if (!diagnostics?.answerStatus) {
    return ''
  }
  const labels = {
    ANSWERED: '模型已回答',
    NO_CONTEXT: '知识库无相关内容',
    EMPTY_FALLBACK: '模型空内容兜底',
    HANDOFF: '已转人工客服',
  }
  return labels[diagnostics.answerStatus] || diagnostics.answerStatus
}

async function scrollToBottom() {
  await nextTick()
  if (logEl.value) {
    logEl.value.scrollTop = logEl.value.scrollHeight
  }
}

async function loadKnowledgeBase(id) {
  loadingKb.value = true
  try {
    kb.value = await getKnowledgeBase(id)
  } catch (err) {
    kb.value = null
    throw err
  } finally {
    loadingKb.value = false
  }
}

async function loadSessions(id) {
  loadingSessions.value = true
  try {
    sessions.value = (await listChatSessions(id)) || []
  } finally {
    loadingSessions.value = false
  }
}

function toViewMessages(items, sessionId) {
  return (items || []).map((item) => ({
    key: item.id != null ? `msg-${item.id}` : `local-${item.role}-${item.createdAt || ''}`,
    id: item.id,
    sessionId: item.sessionId || sessionId,
    role: item.role,
    content: item.content,
    createdAt: item.createdAt,
    citations: (item.citations || []).length
      ? item.citations
      : (item.citedChunkIds || []).map((chunkId) => ({ chunkId })),
    diagnostics: item.retrieval || null,
  }))
}

async function loadMessages(sessionId) {
  const seq = ++requestSeq
  loadingMessages.value = true
  error.value = ''
  try {
    const items = await listChatMessages(sessionId)
    if (seq !== requestSeq || currentSessionId.value !== sessionId) {
      return
    }
    messages.value = toViewMessages(items, sessionId)
    await scrollToBottom()
  } catch (err) {
    if (seq !== requestSeq) {
      return
    }
    messages.value = []
    error.value = err.message || '加载聊天记录失败'
  } finally {
    if (seq === requestSeq) {
      loadingMessages.value = false
    }
  }
}

async function selectSession(session) {
  if (!session || sending.value) {
    return
  }
  currentSessionId.value = session.id
  await loadMessages(session.id)
}

async function createSession(title) {
  const created = await createChatSession(kbId.value, title)
  await loadSessions(kbId.value)
  currentSessionId.value = created.id
  messages.value = []
  return created
}

async function startNewSession() {
  if (sending.value) {
    return
  }
  error.value = ''
  try {
    await createSession()
  } catch (err) {
    error.value = err.message || '创建会话失败'
  }
}

async function send() {
  const text = draft.value.trim()
  if (!text || sending.value || !kbId.value) {
    return
  }
  sending.value = true
  error.value = ''
  const seq = ++requestSeq
  const targetKb = kbId.value
  try {
    let sessionId = currentSessionId.value
    if (sessionId == null) {
      const created = await createSession(text.slice(0, 255))
      sessionId = created.id
    }
    const response = await askKnowledgeBase(targetKb, text, sessionId)
    if (seq !== requestSeq || kbId.value !== targetKb || currentSessionId.value !== sessionId) {
      return
    }
    draft.value = ''
    messages.value = [
      ...messages.value,
      {
        key: `local-user-${Date.now()}`,
        role: 'USER',
        content: text,
        sessionId,
        citations: [],
      },
      {
        key: `local-ai-${Date.now()}`,
        role: 'ASSISTANT',
        content: response.answer,
        sessionId: response.sessionId || sessionId,
        citations: response.citations || [],
        diagnostics: response.diagnostics || null,
      },
    ]
    await loadSessions(targetKb)
    await scrollToBottom()
  } catch (err) {
    if (seq === requestSeq) {
      error.value = err.message || '提问失败'
    }
  } finally {
    if (seq === requestSeq) {
      sending.value = false
    }
  }
}

function onEditorKeydown(event) {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    send()
  }
}

watch(
  kbId,
  async (id) => {
    requestSeq += 1
    currentSessionId.value = null
    messages.value = []
    draft.value = ''
    sessions.value = []
    error.value = ''
    if (!id) {
      return
    }
    try {
      await loadKnowledgeBase(id)
      await loadSessions(id)
    } catch (err) {
      error.value = err.message || '加载知识库会话失败'
    }
  },
  { immediate: true },
)
</script>

<template>
  <section class="chat-page">
    <header class="page-head">
      <div>
        <h1>AI 智能问答</h1>
        <p class="subtitle">
          {{ kb?.name || (loadingKb ? '正在加载知识库…' : '知识库问答') }} · 仅当前用户在本知识库下的会话
        </p>
      </div>
      <div class="actions">
        <button type="button" @click="push(`/knowledge/${kbId}`)">返回文档</button>
        <button type="button" @click="push('/knowledge')">知识库列表</button>
      </div>
    </header>

    <p v-if="error" class="error">{{ error }}</p>

    <div class="chat-shell">
      <aside class="chat-sidebar panel">
        <div class="chat-sidebar-head">
          <h2>历史会话</h2>
          <button type="button" class="primary" :disabled="sending" @click="startNewSession">新建会话</button>
        </div>
        <p v-if="loadingSessions" class="hint">正在加载会话…</p>
        <p v-else-if="sessions.length === 0" class="empty">暂无会话，可直接提问或点击新建</p>
        <ul v-else class="session-list">
          <li v-for="item in sessions" :key="item.id">
            <button
              type="button"
              class="session-item"
              :class="{ active: item.id === currentSessionId }"
              :disabled="sending"
              @click="selectSession(item)"
            >
              <strong>{{ item.title || '新对话' }}</strong>
              <span>{{ formatTime(item.updatedAt || item.createdAt) }}</span>
            </button>
          </li>
        </ul>
      </aside>

      <div class="chat-main panel">
        <p class="chat-current">
          {{ currentSession ? currentSession.title || '新对话' : '未选择会话，发送后将自动创建' }}
        </p>
        <div ref="logEl" class="chat-log">
          <p v-if="loadingMessages" class="hint">正在加载聊天记录…</p>
          <p v-else-if="messages.length === 0" class="empty">还没有消息。请输入售后问题，系统将检索当前知识库后作答。</p>
          <article
            v-for="message in messages"
            :key="message.key"
            class="bubble"
            :class="isUser(message.role) ? 'bubble-user' : 'bubble-ai'"
          >
            <div class="bubble-meta">
              <span>{{ isUser(message.role) ? '我' : 'SmartServe AI' }}</span>
              <span>{{ formatTime(message.createdAt) }}</span>
            </div>
            <p class="bubble-content">{{ message.content }}</p>
            <div v-if="!isUser(message.role) && message.diagnostics" class="diagnostics">
              <span :class="['diag-chip', message.diagnostics.degraded ? 'diag-warn' : 'diag-ok']">
                {{ degradeText(message.diagnostics) }}
              </span>
              <span class="diag-chip">{{ modeText(message.diagnostics) }}</span>
              <span class="diag-chip">召回 {{ message.diagnostics.recalledCount }} → 引用 {{ message.diagnostics.rerankedCount }}</span>
              <span class="diag-chip">精排 {{ message.diagnostics.rerankStrategy }}</span>
              <span class="diag-chip">{{ answerStatusText(message.diagnostics) }}</span>
              <span class="diag-chip">向量模型 {{ message.diagnostics.embeddingModel || '-' }}</span>
            </div>
            <div v-if="!isUser(message.role) && message.citations?.length" class="citations">
              <h3>知识来源</h3>
              <div v-for="(cite, index) in message.citations" :key="cite.chunkId || index" class="citation">
                <p v-if="cite.documentName || cite.chunkIndex != null">
                  {{ cite.documentName || '文档' }}
                  · 分块 {{ cite.chunkIndex ?? '-' }}
                  <template v-if="cite.score != null"> · 相关度 {{ scoreText(cite.score) }}</template>
                  <template v-if="cite.vectorScore != null"> · 向量 {{ scoreText(cite.vectorScore) }}</template>
                  <template v-if="cite.rerankScore != null"> · 精排 {{ scoreText(cite.rerankScore) }}</template>
                </p>
                <p v-else>引用分块 ID {{ cite.chunkId }}</p>
                <p v-if="cite.content" class="citation-content">{{ cite.content }}</p>
              </div>
            </div>
          </article>
        </div>
        <form class="composer" @submit.prevent="send">
          <textarea
            v-model="draft"
            rows="3"
            maxlength="4000"
            placeholder="输入问题，Enter 发送，Shift+Enter 换行"
            :disabled="sending"
            @keydown="onEditorKeydown"
          />
          <button type="submit" class="primary" :disabled="sending || !draft.trim()">
            {{ sending ? '检索回答中…' : '发送' }}
          </button>
        </form>
      </div>
    </div>
  </section>
</template>
