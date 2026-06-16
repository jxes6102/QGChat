import { $fetch } from 'ofetch'
import { sourceUrlDefaults } from '../config/sourceUrls'
import type {
  AuthResponse,
  ChatMessageResponse,
  ConversationResponse,
  GroupMemberResponse,
  LoginRequest,
  RegisterRequest,
  SendMessageRequest,
  UpdateProfileRequest,
  UserProfileResponse
} from '../types/qgchat'

type NuxtClientWindow = Window & {
  __NUXT__?: {
    config?: {
      public?: {
        apiBase?: string
      }
    }
  }
}

const publicApiBase = () => {
  if (typeof window === 'undefined') return sourceUrlDefaults.apiBase

  return (window as NuxtClientWindow).__NUXT__?.config?.public?.apiBase || sourceUrlDefaults.apiBase
}

export const useQGChatApi = () => {
  const request = async <T>(path: string, options: Parameters<typeof $fetch<T>>[1] = {}) => {
    const token = typeof window !== 'undefined' ? localStorage.getItem('qgchat.token') : null
    const headers = new Headers(options.headers as HeadersInit | undefined)

    // 所有 REST API 統一在這裡帶入 Bearer token，避免各頁面重複處理授權標頭。
    if (token) {
      headers.set('Authorization', `Bearer ${token}`)
    }

    return await $fetch<T>(path, {
      baseURL: publicApiBase(),
      ...options,
      headers
    })
  }

  return {
    login: (body: LoginRequest) => request<AuthResponse>('/auth/login', { method: 'POST', body }),
    register: (body: RegisterRequest) => request<AuthResponse>('/auth/register', { method: 'POST', body }),
    logout: () => request<{ loggedOut: boolean }>('/auth/logout', { method: 'POST' }),
    profile: () => request<UserProfileResponse>('/profile'),
    updateProfile: (body: UpdateProfileRequest) => request<UserProfileResponse>('/profile', { method: 'PATCH', body }),
    conversations: () => request<ConversationResponse[]>('/chats/conversations'),
    createDirect: (targetUsername: string) =>
      request<ConversationResponse>('/chats/direct', { method: 'POST', body: { targetUsername } }),
    createGroup: (body: { name: string, description?: string, avatarUrl?: string, isPrivate?: boolean, memberUsernames?: string[] }) =>
      request<ConversationResponse>('/chats/groups', { method: 'POST', body }),
    addGroupMembers: (groupId: string, memberUsernames: string[]) =>
      request<ConversationResponse>(`/chats/groups/${groupId}/members`, { method: 'POST', body: { memberUsernames } }),
    groupMembers: (groupId: string) => request<GroupMemberResponse[]>(`/chats/groups/${groupId}/members`),
    updateGroupMemberRole: (groupId: string, memberUserId: string, role: 'ADMIN' | 'MEMBER') =>
      request<GroupMemberResponse>(`/chats/groups/${groupId}/members/${memberUserId}/role`, { method: 'PATCH', body: { role } }),
    removeGroupMember: (groupId: string, memberUserId: string) =>
      request<void>(`/chats/groups/${groupId}/members/${memberUserId}`, { method: 'DELETE' }),
    leaveGroup: (groupId: string) => request<void>(`/chats/groups/${groupId}/leave`, { method: 'POST' }),
    transferGroupOwner: (groupId: string, newOwnerUserId: string) =>
      request<GroupMemberResponse>(`/chats/groups/${groupId}/owner`, { method: 'PATCH', body: { newOwnerUserId } }),
    messages: (conversationId: string, limit = 50) =>
      request<ChatMessageResponse[]>(`/chats/conversations/${conversationId}/messages`, { query: { limit } }),
    sendMessage: (conversationId: string, body: SendMessageRequest) =>
      request<ChatMessageResponse>(`/chats/conversations/${conversationId}/messages`, { method: 'POST', body }),
    markRead: (conversationId: string, messageId: string) =>
      request<ChatMessageResponse>(`/chats/conversations/${conversationId}/read`, { method: 'POST', body: { messageId } })
  }
}
