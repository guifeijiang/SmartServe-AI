<script setup>
import { onMounted, ref } from 'vue'
import { createKnowledgeBase, listKnowledgeBases } from '../api/knowledge.js'
import { push } from '../router.js'

const loading = ref(false)
const error = ref('')
const items = ref([])
const name = ref('')
const description = ref('')
const submitting = ref(false)

async function load() {
  loading.value = true
  error.value = ''
  try {
    items.value = (await listKnowledgeBases()) || []
  } catch (err) {
    items.value = []
    error.value = err.message || '加载知识库失败'
  } finally {
    loading.value = false
  }
}

async function submit() {
  if (submitting.value) {
    return
  }
  error.value = ''
  submitting.value = true
  try {
    await createKnowledgeBase({
      name: name.value.trim(),
      description: description.value.trim(),
    })
    name.value = ''
    description.value = ''
    await load()
  } catch (err) {
    error.value = err.message || '创建失败'
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<template>
  <section>
    <header class="page-head">
      <div>
        <h1>知识库</h1>
        <p class="subtitle">沿用现有知识库接口，仅显示当前用户自己的知识库</p>
      </div>
    </header>

    <form class="form panel" @submit.prevent="submit">
      <label>
        名称
        <input v-model="name" required maxlength="128" />
      </label>
      <label>
        描述
        <input v-model="description" maxlength="512" />
      </label>
      <button type="submit" class="primary" :disabled="submitting">
        {{ submitting ? '创建中…' : '创建知识库' }}
      </button>
    </form>

    <p v-if="loading" class="hint">正在加载知识库…</p>
    <p v-else-if="error" class="error">{{ error }}</p>
    <p v-else-if="items.length === 0" class="empty">暂无知识库</p>
    <table v-else class="table">
      <thead>
        <tr>
          <th>名称</th>
          <th>状态</th>
          <th>描述</th>
          <th></th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="item in items" :key="item.id">
          <td>{{ item.name }}</td>
          <td>{{ item.status }}</td>
          <td>{{ item.description || '-' }}</td>
          <td>
            <div class="actions">
              <button type="button" @click="push(`/knowledge/${item.id}`)">文档</button>
              <button type="button" class="primary" @click="push(`/knowledge/${item.id}/chat`)">问答</button>
            </div>
          </td>
        </tr>
      </tbody>
    </table>
  </section>
</template>
