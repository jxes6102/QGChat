<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { qgChatSession } from '../composables/useQGChatSession'
import { useQGChatApi } from '../composables/useQGChatApi'
import type { UserProfileResponse } from '../types/qgchat'
import { localizeQGChatError } from '../utils/qgchatErrors'

const api = useQGChatApi()
const profile = ref<UserProfileResponse | null>(null)
const isLoading = ref(false)
const errorMessage = ref('')
const successMessage = ref('')
const profileDraft = reactive({
  displayName: '',
  email: '',
  avatarUrl: ''
})

const showError = (error: unknown) => {
  errorMessage.value = localizeQGChatError(error)
}

const syncProfileDraft = () => {
  profileDraft.displayName = profile.value?.displayName || ''
  profileDraft.email = profile.value?.email || ''
  profileDraft.avatarUrl = profile.value?.avatarUrl || ''
}

const loadProfile = async () => {
  if (!qgChatSession.getToken()) {
    qgChatSession.goTo('/login')
    return
  }

  isLoading.value = true
  try {
    profile.value = await api.profile()
    syncProfileDraft()
  } catch (error) {
    showError(error)
  } finally {
    isLoading.value = false
  }
}

const updateProfile = async () => {
  errorMessage.value = ''
  successMessage.value = ''
  isLoading.value = true
  try {
    profile.value = await api.updateProfile({
      displayName: profileDraft.displayName,
      email: profileDraft.email,
      avatarUrl: profileDraft.avatarUrl
    })
    syncProfileDraft()
    successMessage.value = '個人資料已更新'
  } catch (error) {
    showError(error)
  } finally {
    isLoading.value = false
  }
}

onMounted(loadProfile)
</script>

<template>
  <main class="min-h-screen bg-neutral-950 px-4 py-10 text-neutral-100">
    <section class="mx-auto w-full max-w-xl rounded-lg border border-neutral-800 bg-neutral-900 p-6 shadow-2xl shadow-black/40">
      <a href="/" class="text-sm font-medium text-cyan-300">返回聊天室</a>
      <h1 class="mt-2 text-3xl font-semibold tracking-normal text-white">個人資料</h1>
      <p class="mt-3 text-sm leading-6 text-neutral-400">更新顯示名稱、電子郵件與頭像 URL。</p>

      <form class="mt-7 space-y-4" @submit.prevent="updateProfile">
        <label class="block text-sm">
          <span class="mb-1 block text-neutral-300">顯示名稱</span>
          <input v-model="profileDraft.displayName" class="w-full rounded-md border border-neutral-700 bg-neutral-950 px-3 py-2 text-white outline-none transition focus:border-cyan-400">
        </label>
        <label class="block text-sm">
          <span class="mb-1 block text-neutral-300">電子郵件</span>
          <input v-model="profileDraft.email" type="email" class="w-full rounded-md border border-neutral-700 bg-neutral-950 px-3 py-2 text-white outline-none transition focus:border-cyan-400">
        </label>
        <label class="block text-sm">
          <span class="mb-1 block text-neutral-300">頭像 URL</span>
          <input v-model="profileDraft.avatarUrl" class="w-full rounded-md border border-neutral-700 bg-neutral-950 px-3 py-2 text-white outline-none transition focus:border-cyan-400">
        </label>

        <p v-if="errorMessage" class="rounded-md border border-rose-500/30 bg-rose-500/10 px-3 py-2 text-sm text-rose-200">{{ errorMessage }}</p>
        <p v-if="successMessage" class="rounded-md border border-emerald-500/30 bg-emerald-500/10 px-3 py-2 text-sm text-emerald-200">{{ successMessage }}</p>

        <button class="w-full rounded-md bg-cyan-400 px-4 py-2.5 text-sm font-semibold text-neutral-950 transition hover:bg-cyan-300 disabled:cursor-not-allowed disabled:opacity-60" :disabled="isLoading">
          {{ isLoading ? '處理中...' : '儲存資料' }}
        </button>
      </form>
    </section>
  </main>
</template>
