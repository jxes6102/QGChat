<script setup lang="ts">
import { reactive, ref } from 'vue'
import { qgChatSession } from '../../composables/useQGChatSession'
import { useQGChatApi } from '../../composables/useQGChatApi'

const api = useQGChatApi()
const mode = ref<'direct' | 'group'>('direct')
const isLoading = ref(false)
const errorMessage = ref('')
const targetUsername = ref('')
const groupDraft = reactive({
  name: '',
  description: '',
  avatarUrl: '',
  members: '',
  isPrivate: false
})

const showError = (error: unknown) => {
  const fetchError = error as { data?: { message?: string }, message?: string }
  errorMessage.value = fetchError.data?.message || fetchError.message || '操作失敗，請稍後再試'
}

const ensureSignedIn = () => {
  if (qgChatSession.getToken()) return true

  qgChatSession.goTo('/login')
  return false
}

const createDirect = async () => {
  if (!ensureSignedIn()) return

  const username = targetUsername.value.trim()
  if (!username) return

  errorMessage.value = ''
  isLoading.value = true
  try {
    const conversation = await api.createDirect(username)
    qgChatSession.goTo(`/?conversationId=${conversation.id}`)
  } catch (error) {
    showError(error)
  } finally {
    isLoading.value = false
  }
}

const createGroup = async () => {
  if (!ensureSignedIn()) return

  const name = groupDraft.name.trim()
  if (!name) return

  const memberUsernames = groupDraft.members
    .split(',')
    .map((member) => member.trim())
    .filter(Boolean)

  errorMessage.value = ''
  isLoading.value = true
  try {
    const conversation = await api.createGroup({
      name,
      description: groupDraft.description.trim() || undefined,
      avatarUrl: groupDraft.avatarUrl.trim() || undefined,
      isPrivate: groupDraft.isPrivate,
      memberUsernames
    })
    qgChatSession.goTo(`/?conversationId=${conversation.id}`)
  } catch (error) {
    showError(error)
  } finally {
    isLoading.value = false
  }
}
</script>

<template>
  <main class="min-h-screen bg-neutral-950 px-4 py-10 text-neutral-100">
    <section class="mx-auto w-full max-w-xl rounded-lg border border-neutral-800 bg-neutral-900 p-6 shadow-2xl shadow-black/40">
      <a href="/" class="text-sm font-medium text-cyan-300">返回聊天室</a>
      <h1 class="mt-2 text-3xl font-semibold tracking-normal text-white">建立對話</h1>
      <p class="mt-3 text-sm leading-6 text-neutral-400">建立私人對話或群組聊天室。</p>

      <div class="mt-7 grid grid-cols-2 rounded-md bg-neutral-800 p-1">
        <button class="rounded px-3 py-2 text-sm font-medium transition" :class="mode === 'direct' ? 'bg-white text-neutral-950' : 'text-neutral-300 hover:text-white'" @click="mode = 'direct'; errorMessage = ''">私人對話</button>
        <button class="rounded px-3 py-2 text-sm font-medium transition" :class="mode === 'group' ? 'bg-white text-neutral-950' : 'text-neutral-300 hover:text-white'" @click="mode = 'group'; errorMessage = ''">群組</button>
      </div>

      <form v-if="mode === 'direct'" class="mt-5 space-y-4" @submit.prevent="createDirect">
        <label class="block text-sm">
          <span class="mb-1 block text-neutral-300">對方使用者名稱</span>
          <input v-model="targetUsername" class="w-full rounded-md border border-neutral-700 bg-neutral-950 px-3 py-2 text-white outline-none transition focus:border-cyan-400" autocomplete="off">
        </label>

        <p v-if="errorMessage" class="rounded-md border border-rose-500/30 bg-rose-500/10 px-3 py-2 text-sm text-rose-200">{{ errorMessage }}</p>

        <button class="w-full rounded-md bg-cyan-400 px-4 py-2.5 text-sm font-semibold text-neutral-950 transition hover:bg-cyan-300 disabled:cursor-not-allowed disabled:opacity-60" :disabled="isLoading || !targetUsername.trim()">
          {{ isLoading ? '建立中...' : '建立私人對話' }}
        </button>
      </form>

      <form v-else class="mt-5 space-y-4" @submit.prevent="createGroup">
        <label class="block text-sm">
          <span class="mb-1 block text-neutral-300">群組名稱</span>
          <input v-model="groupDraft.name" class="w-full rounded-md border border-neutral-700 bg-neutral-950 px-3 py-2 text-white outline-none transition focus:border-cyan-400">
        </label>
        <label class="block text-sm">
          <span class="mb-1 block text-neutral-300">描述</span>
          <textarea v-model="groupDraft.description" rows="3" class="w-full resize-none rounded-md border border-neutral-700 bg-neutral-950 px-3 py-2 text-white outline-none transition focus:border-cyan-400" />
        </label>
        <label class="block text-sm">
          <span class="mb-1 block text-neutral-300">頭像 URL</span>
          <input v-model="groupDraft.avatarUrl" class="w-full rounded-md border border-neutral-700 bg-neutral-950 px-3 py-2 text-white outline-none transition focus:border-cyan-400">
        </label>
        <label class="block text-sm">
          <span class="mb-1 block text-neutral-300">成員使用者名稱</span>
          <input v-model="groupDraft.members" class="w-full rounded-md border border-neutral-700 bg-neutral-950 px-3 py-2 text-white outline-none transition focus:border-cyan-400" placeholder="alice, bob">
        </label>
        <label class="flex items-center gap-2 text-sm text-neutral-300">
          <input v-model="groupDraft.isPrivate" type="checkbox" class="h-4 w-4 rounded border-neutral-700 bg-neutral-900 text-cyan-400">
          私密群組
        </label>

        <p v-if="errorMessage" class="rounded-md border border-rose-500/30 bg-rose-500/10 px-3 py-2 text-sm text-rose-200">{{ errorMessage }}</p>

        <button class="w-full rounded-md bg-cyan-400 px-4 py-2.5 text-sm font-semibold text-neutral-950 transition hover:bg-cyan-300 disabled:cursor-not-allowed disabled:opacity-60" :disabled="isLoading || !groupDraft.name.trim()">
          {{ isLoading ? '建立中...' : '建立群組' }}
        </button>
      </form>
    </section>
  </main>
</template>
