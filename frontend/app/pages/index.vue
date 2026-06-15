<script setup lang="ts">
import type { Client as StompClient } from '@stomp/stompjs'
import { formatDistanceToNowStrict } from 'date-fns/formatDistanceToNowStrict'
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { sourceUrlDefaults } from '../config/sourceUrls'
import { qgChatSession } from '../composables/useQGChatSession'
import { useQGChatApi } from '../composables/useQGChatApi'
import type { ChatMessageResponse, ConversationResponse, UserProfileResponse } from '../types/qgchat'

type NuxtClientWindow = Window & {
  __NUXT__?: {
    config?: {
      public?: {
        wsBase?: string
      }
    }
  }
}

const api = useQGChatApi()
const token = ref('')
const profile = ref<UserProfileResponse | null>(null)
const conversations = ref<ConversationResponse[]>([])
const selectedConversationId = ref('')
const messages = ref<ChatMessageResponse[]>([])
const messageDraft = ref('')
const isBooting = ref(false)
const errorMessage = ref('')
let stompClient: StompClient | null = null
let activeSubscription: { unsubscribe: () => void } | null = null

const selectedConversation = computed(() =>
  conversations.value.find((conversation) => conversation.id === selectedConversationId.value) || null
)

const publicWsBase = () => {
  if (typeof window === 'undefined') return sourceUrlDefaults.wsBase

  return (window as NuxtClientWindow).__NUXT__?.config?.public?.wsBase || sourceUrlDefaults.wsBase
}

const preferredConversationId = () => {
  if (typeof window === 'undefined') return ''

  return new URLSearchParams(window.location.search).get('conversationId') || ''
}

const conversationTitle = (conversation: ConversationResponse) =>
  conversation.type === 'GROUP' ? conversation.groupName || '未命名群組' : conversation.directDisplayName || '私人對話'

const conversationAvatar = (conversation: ConversationResponse) =>
  conversation.type === 'GROUP' ? conversation.groupAvatarUrl : conversation.directAvatarUrl

const initials = (name?: string | null) => (name || 'QG').trim().slice(0, 2).toUpperCase()

const relativeTime = (date?: string | null) => {
  if (!date) return ''

  try {
    return formatDistanceToNowStrict(new Date(date), { addSuffix: true })
  } catch {
    return ''
  }
}

const showError = (error: unknown) => {
  const fetchError = error as { data?: { message?: string }, message?: string }
  errorMessage.value = fetchError.data?.message || fetchError.message || '操作失敗，請稍後再試'
}

const loadConversations = async () => {
  conversations.value = await api.conversations()
  const preferredId = preferredConversationId()
  const preferred = conversations.value.find((conversation) => conversation.id === preferredId)
  const firstConversation = conversations.value[0]
  const nextConversation = preferred || firstConversation

  if (!selectedConversationId.value && nextConversation) {
    await selectConversation(nextConversation.id)
  }
}

const bootSession = async () => {
  const savedToken = qgChatSession.getToken()
  if (!savedToken) return

  isBooting.value = true
  token.value = savedToken
  try {
    profile.value = await api.profile()
    await connectSocket()
    await loadConversations()
  } catch (error) {
    qgChatSession.clearToken()
    token.value = ''
    showError(error)
  } finally {
    isBooting.value = false
  }
}

const signOut = async () => {
  try {
    await api.logout()
  } catch {
    // Local cleanup is enough when the token is already invalid.
  }

  qgChatSession.clearToken()
  disconnectSocket()
  qgChatSession.goTo('/login')
}

const selectConversation = async (conversationId: string) => {
  selectedConversationId.value = conversationId
  errorMessage.value = ''
  try {
    messages.value = await api.messages(conversationId)
    subscribeConversation(conversationId)
    const lastMessage = messages.value[messages.value.length - 1]
    if (lastMessage) {
      await api.markRead(conversationId, lastMessage.id)
      const conversation = conversations.value.find((item) => item.id === conversationId)
      if (conversation) conversation.unreadCount = 0
    }
    await nextTick()
    scrollMessagesToBottom()
  } catch (error) {
    showError(error)
  }
}

const sendMessage = async () => {
  const content = messageDraft.value.trim()
  if (!content || !selectedConversationId.value) return

  errorMessage.value = ''
  messageDraft.value = ''
  try {
    const message = await api.sendMessage(selectedConversationId.value, { type: 'TEXT', content })
    appendMessage(message)
  } catch (error) {
    messageDraft.value = content
    showError(error)
  }
}

const appendMessage = (message: ChatMessageResponse) => {
  if (!messages.value.some((item) => item.id === message.id)) {
    messages.value.push(message)
  }

  const conversation = conversations.value.find((item) => item.id === message.conversationId)
  if (conversation) {
    conversation.lastMessage = message
    conversation.updatedAt = message.sentAt
  }

  void nextTick(scrollMessagesToBottom)
}

const connectSocket = async () => {
  disconnectSocket()
  if (!token.value || typeof window === 'undefined') return

  const { Client } = await import('@stomp/stompjs')
  stompClient = new Client({
    brokerURL: publicWsBase(),
    reconnectDelay: 4000,
    debug: () => {}
  })
  stompClient.onConnect = () => {
    if (selectedConversationId.value) subscribeConversation(selectedConversationId.value)
  }
  stompClient.activate()
}

const subscribeConversation = (conversationId: string) => {
  if (!stompClient?.connected) return

  activeSubscription?.unsubscribe()
  activeSubscription = stompClient.subscribe(`/topic/conversations/${conversationId}`, (frame) => {
    appendMessage(JSON.parse(frame.body) as ChatMessageResponse)
  })
}

const disconnectSocket = () => {
  activeSubscription?.unsubscribe()
  activeSubscription = null
  void stompClient?.deactivate()
  stompClient = null
}

const scrollMessagesToBottom = () => {
  const panel = document.getElementById('message-panel')
  if (panel) panel.scrollTop = panel.scrollHeight
}

onMounted(bootSession)
onBeforeUnmount(disconnectSocket)
</script>

<template>
  <main class="min-h-screen bg-neutral-950 text-neutral-100">
    <div v-if="isBooting" class="grid min-h-screen place-items-center">
      <div class="h-10 w-10 animate-spin rounded-full border-2 border-cyan-400 border-t-transparent" />
    </div>

    <section v-else-if="!token" class="grid min-h-screen place-items-center px-4 py-10">
      <div class="w-full max-w-md rounded-lg border border-neutral-800 bg-neutral-900 p-6 shadow-2xl shadow-black/40">
        <p class="text-sm font-medium text-cyan-300">QGChat</p>
        <h1 class="mt-2 text-3xl font-semibold tracking-normal text-white">請先登入</h1>
        <p class="mt-3 text-sm leading-6 text-neutral-400">登入後即可查看對話、傳送訊息與管理聊天室。</p>
        <p v-if="errorMessage" class="mt-4 rounded-md border border-rose-500/30 bg-rose-500/10 px-3 py-2 text-sm text-rose-200">{{ errorMessage }}</p>
        <a href="/login" class="mt-6 flex w-full items-center justify-center rounded-md bg-cyan-400 px-4 py-2.5 text-sm font-semibold text-neutral-950 transition hover:bg-cyan-300">前往登入 / 註冊</a>
      </div>
    </section>

    <section v-else class="grid h-screen grid-cols-1 overflow-hidden lg:grid-cols-[320px_minmax(0,1fr)_300px]">
      <aside class="flex min-h-0 flex-col border-b border-neutral-800 bg-neutral-950 lg:border-b-0 lg:border-r">
        <div class="border-b border-neutral-800 p-4">
          <div class="flex items-center justify-between gap-3">
            <div>
              <p class="text-sm font-medium text-cyan-300">QGChat</p>
              <h1 class="text-xl font-semibold">聊天室</h1>
            </div>
            <button class="rounded-md border border-neutral-700 px-3 py-2 text-sm text-neutral-200 transition hover:border-rose-300 hover:text-rose-200" @click="signOut">登出</button>
          </div>
          <p class="mt-3 truncate text-sm text-neutral-400">{{ profile?.displayName }} · {{ profile?.username }}</p>
        </div>

        <div class="min-h-0 flex-1 overflow-y-auto p-2">
          <button
            v-for="conversation in conversations"
            :key="conversation.id"
            class="mb-1 flex w-full items-center gap-3 rounded-md px-3 py-3 text-left transition hover:bg-neutral-900"
            :class="selectedConversationId === conversation.id ? 'bg-neutral-800' : ''"
            @click="selectConversation(conversation.id)"
          >
            <img v-if="conversationAvatar(conversation)" :src="conversationAvatar(conversation) || ''" class="h-10 w-10 rounded-full object-cover" alt="">
            <div v-else class="grid h-10 w-10 shrink-0 place-items-center rounded-full bg-cyan-400 text-sm font-bold text-neutral-950">{{ initials(conversationTitle(conversation)) }}</div>
            <div class="min-w-0 flex-1">
              <div class="flex items-center justify-between gap-3">
                <p class="truncate text-sm font-semibold text-white">{{ conversationTitle(conversation) }}</p>
                <span class="shrink-0 text-xs text-neutral-500">{{ relativeTime(conversation.updatedAt) }}</span>
              </div>
              <p class="mt-1 truncate text-sm text-neutral-400">{{ conversation.lastMessage?.content || '還沒有訊息' }}</p>
            </div>
            <span v-if="conversation.unreadCount" class="grid h-6 min-w-6 place-items-center rounded-full bg-cyan-400 px-2 text-xs font-bold text-neutral-950">{{ conversation.unreadCount }}</span>
          </button>

          <div v-if="conversations.length === 0" class="px-4 py-10 text-center text-sm text-neutral-500">尚無對話，請先建立私人對話或群組。</div>
        </div>
      </aside>

      <section class="flex min-h-0 flex-col bg-neutral-900">
        <header class="flex items-center justify-between border-b border-neutral-800 px-5 py-4">
          <div v-if="selectedConversation">
            <h2 class="text-lg font-semibold">{{ conversationTitle(selectedConversation) }}</h2>
            <p class="text-sm text-neutral-400">{{ selectedConversation.type === 'GROUP' ? '群組對話' : '私人對話' }}</p>
          </div>
          <div v-else>
            <h2 class="text-lg font-semibold">選擇聊天室</h2>
            <p class="text-sm text-neutral-400">訊息會顯示在這裡</p>
          </div>
          <button class="rounded-md border border-neutral-700 p-2 text-neutral-300 transition hover:border-cyan-300 hover:text-cyan-200" title="重新整理" @click="loadConversations">
            <UIcon name="i-heroicons-arrow-path" class="h-5 w-5" />
          </button>
        </header>

        <div id="message-panel" class="min-h-0 flex-1 space-y-4 overflow-y-auto px-5 py-6">
          <div v-if="!selectedConversation" class="grid h-full place-items-center text-center text-neutral-500">請先選擇聊天室</div>

          <div
            v-for="message in messages"
            :key="message.id"
            class="flex"
            :class="message.senderId === profile?.id ? 'justify-end' : 'justify-start'"
          >
            <div class="max-w-[78%] rounded-lg px-4 py-3" :class="message.senderId === profile?.id ? 'bg-cyan-400 text-neutral-950' : 'bg-neutral-800 text-neutral-100'">
              <div class="mb-1 flex items-center gap-2 text-xs" :class="message.senderId === profile?.id ? 'text-neutral-700' : 'text-neutral-400'">
                <span class="font-semibold">{{ message.senderDisplayName }}</span>
                <span>{{ relativeTime(message.sentAt) }}</span>
              </div>
              <p class="whitespace-pre-wrap break-words text-sm leading-6">{{ message.content }}</p>
            </div>
          </div>
        </div>

        <form class="border-t border-neutral-800 bg-neutral-950 p-4" @submit.prevent="sendMessage">
          <p v-if="errorMessage" class="mb-3 rounded-md border border-rose-500/30 bg-rose-500/10 px-3 py-2 text-sm text-rose-200">{{ errorMessage }}</p>
          <div class="flex gap-3">
            <textarea v-model="messageDraft" :disabled="!selectedConversation" rows="1" class="max-h-32 min-h-11 flex-1 resize-none rounded-md border border-neutral-700 bg-neutral-900 px-3 py-2.5 text-sm text-white outline-none transition focus:border-cyan-400 disabled:opacity-50" placeholder="輸入訊息..." @keydown.enter.exact.prevent="sendMessage" />
            <button class="grid h-11 w-11 shrink-0 place-items-center rounded-md bg-cyan-400 text-neutral-950 transition hover:bg-cyan-300 disabled:cursor-not-allowed disabled:opacity-50" :disabled="!selectedConversation || !messageDraft.trim()" title="送出">
              <UIcon name="i-heroicons-paper-airplane-solid" class="h-5 w-5" />
            </button>
          </div>
        </form>
      </section>

      <aside class="min-h-0 overflow-y-auto border-t border-neutral-800 bg-neutral-950 p-4 lg:border-l lg:border-t-0">
        <nav class="space-y-2">
          <a href="/conversations/new" class="flex items-center justify-between rounded-md border border-neutral-800 px-3 py-3 text-sm font-medium text-neutral-100 transition hover:border-cyan-300 hover:text-cyan-200">
            建立對話
            <UIcon name="i-heroicons-arrow-right" class="h-4 w-4" />
          </a>
          <a href="/profile" class="flex items-center justify-between rounded-md border border-neutral-800 px-3 py-3 text-sm font-medium text-neutral-100 transition hover:border-cyan-300 hover:text-cyan-200">
            個人資料
            <UIcon name="i-heroicons-arrow-right" class="h-4 w-4" />
          </a>
        </nav>
      </aside>
    </section>
  </main>
</template>
