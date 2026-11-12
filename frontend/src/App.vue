<script setup>
import { computed, onMounted, watch } from 'vue'
import {
  bootstrapSession,
  currentUser,
  isAuthenticated,
  logout,
  session,
  userRole,
} from './auth/session.js'
import { parseHash, push, route } from './router.js'
import { roleHomeTitle } from './ticket/labels.js'
import LoginView from './views/LoginView.vue'
import RegisterView from './views/RegisterView.vue'
import NotificationListView from './views/NotificationListView.vue'
import TicketListView from './views/TicketListView.vue'
import TicketCreateView from './views/TicketCreateView.vue'
import TicketDetailView from './views/TicketDetailView.vue'
import KnowledgeListView from './views/KnowledgeListView.vue'
import KnowledgeDetailView from './views/KnowledgeDetailView.vue'
import KnowledgeChatView from './views/KnowledgeChatView.vue'

const view = computed(() => route.name)
const workbench = computed(() => roleHomeTitle(userRole.value))

function guard() {
  if (!session.ready) {
    return
  }
  if (!isAuthenticated.value && !route.public) {
    push('/login')
    return
  }
  if (isAuthenticated.value && route.public) {
    push('/tickets')
    return
  }
  if (route.name === 'ticket-create' && userRole.value !== 'CUSTOMER') {
    push('/tickets')
  }
}

onMounted(async () => {
  parseHash()
  await bootstrapSession()
  guard()
})

watch(() => route.path, guard)
</script>

<template>
  <div v-if="!session.ready" class="boot">正在恢复登录状态…</div>

  <main v-else-if="route.public" class="auth-page">
    <LoginView v-if="view === 'login'" />
    <RegisterView v-else />
  </main>

  <div v-else class="shell">
    <aside class="nav">
      <p class="brand">SmartServe AI</p>
      <p class="role">{{ workbench }}</p>
      <nav>
        <a href="#/tickets">售后工单</a>
        <a href="#/notifications">我的通知</a>
        <a v-if="userRole === 'CUSTOMER'" href="#/tickets/new">创建工单</a>
        <a href="#/knowledge">知识库</a>
      </nav>
      <div class="account">
        <strong>{{ currentUser?.displayName || currentUser?.username }}</strong>
        <span>{{ currentUser?.role }}</span>
        <button type="button" @click="logout">退出</button>
      </div>
    </aside>
    <main class="content">
      <NotificationListView v-if="view === 'notifications'" />
      <TicketListView v-else-if="view === 'tickets'" />
      <TicketCreateView v-else-if="view === 'ticket-create'" />
      <TicketDetailView v-else-if="view === 'ticket-detail'" />
      <KnowledgeListView v-else-if="view === 'knowledge'" />
      <KnowledgeDetailView v-else-if="view === 'knowledge-detail'" />
      <KnowledgeChatView v-else-if="view === 'knowledge-chat'" />
    </main>
  </div>
</template>
