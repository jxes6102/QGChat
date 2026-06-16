export interface AuthUser {
  id: string
  username: string
  email: string
  displayName: string
}

export interface AuthResponse {
  tokenType: string
  accessToken: string
  expiresAt: string
  user: AuthUser
}

export interface UserProfileResponse {
  id: string
  username: string
  email: string
  displayName: string
  avatarUrl?: string | null
  status?: string | null
  lastSeenAt?: string | null
  createdAt: string
  updatedAt: string
}

export interface ChatMessageResponse {
  id: string
  conversationId: string
  senderId: string
  senderDisplayName: string
  type: string
  content: string
  metadata?: string | null
  replyToMessageId?: string | null
  sentAt: string
  editedAt?: string | null
  deletedAt?: string | null
}

export interface ConversationResponse {
  id: string
  type: 'DIRECT' | 'GROUP' | string
  groupId?: string | null
  groupName?: string | null
  groupAvatarUrl?: string | null
  currentUserGroupRole?: 'OWNER' | 'ADMIN' | 'MEMBER' | string | null
  directUserId?: string | null
  directDisplayName?: string | null
  directAvatarUrl?: string | null
  lastMessage?: ChatMessageResponse | null
  unreadCount: number
  updatedAt: string
}

export interface GroupMemberResponse {
  userId: string
  username: string
  displayName: string
  avatarUrl?: string | null
  role: 'OWNER' | 'ADMIN' | 'MEMBER' | string
  joinedAt?: string | null
  currentUser: boolean
}

export interface LoginRequest {
  account: string
  password: string
}

export interface RegisterRequest {
  username: string
  email: string
  password: string
  displayName: string
}

export interface SendMessageRequest {
  type?: string
  content: string
  metadata?: string | null
  replyToMessageId?: string | null
}

export interface UpdateProfileRequest {
  displayName?: string
  avatarUrl?: string
  email?: string
}
