<script setup>
import { computed, ref, watch } from 'vue'
import { deleteDocument, getKnowledgeBase, listDocuments, processDocument, uploadDocument } from '../api/knowledge.js'
import { formatTime } from '../ticket/labels.js'
import { route, push } from '../router.js'

const loading = ref(false)
const error = ref('')
const kb = ref(null)
const documents = ref([])
const file = ref(null)
const submitting = ref('')

const kbId = computed(() => route.params.id)

async function load() {
  if (!kbId.value) {
    return
  }
  loading.value = true
  error.value = ''
  try {
    const [detail, docs] = await Promise.all([
      getKnowledgeBase(kbId.value),
      listDocuments(kbId.value),
    ])
    kb.value = detail
    documents.value = docs || []
  } catch (err) {
    kb.value = null
    documents.value = []
    error.value = err.message || '加载文档失败'
  } finally {
    loading.value = false
  }
}

function onFile(event) {
  file.value = event.target.files?.[0] || null
}

async function upload() {
  if (submitting.value || !file.value) {
    return
  }
  submitting.value = 'upload'
  error.value = ''
  try {
    await uploadDocument(kbId.value, file.value)
    file.value = null
    await load()
  } catch (err) {
    error.value = err.message || '上传失败'
  } finally {
    submitting.value = ''
  }
}

async function process(doc) {
  if (submitting.value) {
    return
  }
  submitting.value = `process-${doc.id}`
  error.value = ''
  try {
    await processDocument(kbId.value, doc.id)
    await load()
  } catch (err) {
    error.value = err.message || '解析失败'
  } finally {
    submitting.value = ''
  }
}

async function remove(doc) {
  if (submitting.value) {
    return
  }
  submitting.value = `delete-${doc.id}`
  error.value = ''
  try {
    await deleteDocument(kbId.value, doc.id)
    await load()
  } catch (err) {
    error.value = err.message || '删除失败'
  } finally {
    submitting.value = ''
  }
}

watch(kbId, load, { immediate: true })
</script>

<template>
  <section>
    <header class="page-head">
      <div>
        <h1>{{ kb?.name || '知识库文档' }}</h1>
        <p class="subtitle">{{ kb?.description || '上传、解析与删除走现有文档接口' }}</p>
      </div>
      <div class="actions">
        <button type="button" class="primary" @click="push(`/knowledge/${kbId}/chat`)">AI 智能问答</button>
        <button type="button" @click="push('/knowledge')">返回知识库</button>
      </div>
    </header>

    <form class="form panel" @submit.prevent="upload">
      <label>
        选择文件
        <input type="file" @change="onFile" />
      </label>
      <button type="submit" class="primary" :disabled="!file || Boolean(submitting)">
        {{ submitting === 'upload' ? '上传中…' : '上传文档' }}
      </button>
    </form>

    <p v-if="loading" class="hint">正在加载文档…</p>
    <p v-else-if="error" class="error">{{ error }}</p>
    <p v-else-if="documents.length === 0" class="empty">暂无文档</p>
    <table v-else class="table">
      <thead>
        <tr>
          <th>文件名</th>
          <th>状态</th>
          <th>分块</th>
          <th>时间</th>
          <th></th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="doc in documents" :key="doc.id">
          <td>{{ doc.originalFilename }}</td>
          <td>{{ doc.processStatus }}</td>
          <td>{{ doc.chunkCount }}</td>
          <td>{{ formatTime(doc.createdAt) }}</td>
          <td class="actions">
            <button type="button" :disabled="Boolean(submitting)" @click="process(doc)">
              {{ submitting === `process-${doc.id}` ? '解析中…' : '解析分块' }}
            </button>
            <button type="button" :disabled="Boolean(submitting)" @click="remove(doc)">删除</button>
          </td>
        </tr>
      </tbody>
    </table>
  </section>
</template>
