<script setup lang="ts">
import { reactive, ref } from 'vue'
import { qgChatSession } from '../composables/useQGChatSession'
import { useQGChatApi } from '../composables/useQGChatApi'
import type { RegisterRequest } from '../types/qgchat'

const api = useQGChatApi()
const isRegistering = ref(false)
const isLoading = ref(false)
const errorMessage = ref('')
const authForm = reactive<RegisterRequest & { account: string }>({
  account: '',
  username: '',
  email: '',
  password: '',
  displayName: ''
})

const showError = (error: unknown) => {
  const fetchError = error as { data?: { message?: string }, message?: string }
  errorMessage.value = fetchError.data?.message || fetchError.message || '操作失敗，請稍後再試'
}

const submitAuth = async () => {
  errorMessage.value = ''
  isLoading.value = true
  try {
    const response = isRegistering.value
      ? await api.register({
          username: authForm.username,
          email: authForm.email,
          password: authForm.password,
          displayName: authForm.displayName
        })
      : await api.login({
          account: authForm.account,
          password: authForm.password
        })

    qgChatSession.setToken(response.accessToken)
    qgChatSession.goTo('/')
  } catch (error) {
    showError(error)
  } finally {
    isLoading.value = false
  }
}
</script>

<template>
  <main class="grid min-h-screen place-items-center bg-neutral-950 px-4 py-10 text-neutral-100">
    <section class="w-full max-w-md rounded-lg border border-neutral-800 bg-neutral-900 p-6 shadow-2xl shadow-black/40">
      <div class="mb-7">
        <a href="/" class="text-sm font-medium text-cyan-300">QGChat</a>
        <h1 class="mt-2 text-3xl font-semibold tracking-normal text-white">登入 / 註冊</h1>
        <p class="mt-3 text-sm leading-6 text-neutral-400">登入後即可進入聊天室工作台。</p>
      </div>

      <div class="mb-5 grid grid-cols-2 rounded-md bg-neutral-800 p-1">
        <button class="rounded px-3 py-2 text-sm font-medium transition" :class="!isRegistering ? 'bg-white text-neutral-950' : 'text-neutral-300 hover:text-white'" @click="isRegistering = false">登入</button>
        <button class="rounded px-3 py-2 text-sm font-medium transition" :class="isRegistering ? 'bg-white text-neutral-950' : 'text-neutral-300 hover:text-white'" @click="isRegistering = true">註冊</button>
      </div>

      <form class="space-y-4" @submit.prevent="submitAuth">
        <label v-if="!isRegistering" class="block text-sm">
          <span class="mb-1 block text-neutral-300">帳號或電子郵件</span>
          <input v-model="authForm.account" class="w-full rounded-md border border-neutral-700 bg-neutral-950 px-3 py-2 text-white outline-none transition focus:border-cyan-400" autocomplete="username">
        </label>

        <div v-else class="space-y-4">
          <label class="block text-sm">
            <span class="mb-1 block text-neutral-300">使用者名稱</span>
            <input v-model="authForm.username" class="w-full rounded-md border border-neutral-700 bg-neutral-950 px-3 py-2 text-white outline-none transition focus:border-cyan-400" autocomplete="username">
          </label>
          <label class="block text-sm">
            <span class="mb-1 block text-neutral-300">電子郵件</span>
            <input v-model="authForm.email" type="email" class="w-full rounded-md border border-neutral-700 bg-neutral-950 px-3 py-2 text-white outline-none transition focus:border-cyan-400" autocomplete="email">
          </label>
          <label class="block text-sm">
            <span class="mb-1 block text-neutral-300">顯示名稱</span>
            <input v-model="authForm.displayName" class="w-full rounded-md border border-neutral-700 bg-neutral-950 px-3 py-2 text-white outline-none transition focus:border-cyan-400">
          </label>
        </div>

        <label class="block text-sm">
          <span class="mb-1 block text-neutral-300">密碼</span>
          <input v-model="authForm.password" type="password" class="w-full rounded-md border border-neutral-700 bg-neutral-950 px-3 py-2 text-white outline-none transition focus:border-cyan-400" autocomplete="current-password">
        </label>

        <p v-if="errorMessage" class="rounded-md border border-rose-500/30 bg-rose-500/10 px-3 py-2 text-sm text-rose-200">{{ errorMessage }}</p>

        <button class="flex w-full items-center justify-center rounded-md bg-cyan-400 px-4 py-2.5 text-sm font-semibold text-neutral-950 transition hover:bg-cyan-300 disabled:cursor-not-allowed disabled:opacity-60" :disabled="isLoading">
          {{ isLoading ? '處理中...' : isRegistering ? '建立帳號' : '登入 QGChat' }}
        </button>
      </form>
    </section>
  </main>
</template>
