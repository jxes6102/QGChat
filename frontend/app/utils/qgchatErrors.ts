type FetchLikeError = {
  data?: {
    code?: string
    message?: string
  }
  message?: string
}

const codeMessages: Record<string, string> = {
  CANNOT_CREATE_DIRECT_WITH_SELF: '不能和自己建立私人對話',
  CANNOT_REMOVE_GROUP_MEMBER: '你不能移除此群組成員',
  CONVERSATION_NOT_FOUND: '找不到對話',
  DISPLAY_NAME_BLANK: '顯示名稱不能空白',
  EMAIL_ALREADY_EXISTS: '電子郵件已被使用',
  EMAIL_BLANK: '電子郵件不能空白',
  GROUP_ADD_MEMBERS_REQUIRED: '只有群組擁有者或管理員可以加入成員',
  GROUP_REMOVE_MEMBERS_REQUIRED: '只有群組擁有者或管理員可以移除成員',
  GROUP_MEMBER_NOT_FOUND: '找不到群組成員',
  GROUP_NOT_FOUND: '找不到群組',
  GROUP_OWNER_REQUIRED: '只有群組擁有者可以執行此操作',
  INVALID_ACCOUNT_OR_PASSWORD: '帳號或密碼錯誤',
  INVALID_CURRENT_PASSWORD: '目前密碼錯誤',
  INVALID_RESET_TOKEN: '重設密碼連結無效或已過期',
  INVALID_TOKEN: '登入狀態已失效，請重新登入',
  MESSAGE_NOT_FOUND: '找不到訊息',
  METADATA_INVALID_JSON: '訊息附加資料必須是有效 JSON',
  MISSING_TOKEN: '請先登入',
  NEW_OWNER_MUST_BE_ANOTHER_MEMBER: '請選擇其他成員作為新的擁有者',
  NO_PROFILE_FIELDS: '沒有可更新的個人資料欄位',
  NOT_ACTIVE_GROUP_MEMBER: '你不是這個群組的有效成員',
  NOT_CONVERSATION_PARTICIPANT: '你不是這個對話的成員',
  NOT_GROUP_MEMBER: '你不是這個群組的成員',
  REPLY_MESSAGE_WRONG_CONVERSATION: '回覆的訊息不屬於這個對話',
  TARGET_USER_NOT_FOUND: '找不到指定使用者',
  TRANSFER_OWNER_BEFORE_LEAVING: '請先轉移群組擁有者再離開',
  UNSUPPORTED_MESSAGE_TYPE: '不支援的訊息類型',
  USE_OWNER_TRANSFER: '請使用轉移擁有者功能變更群組擁有者',
  USER_NOT_FOUND: '找不到使用者',
  USERNAME_ALREADY_EXISTS: '使用者名稱已被使用',
  VALIDATION_FAILED: '輸入資料格式不正確'
}

const exactMessages: Record<string, string> = {
  // 後端目前以英文訊息描述錯誤；前端集中在這裡轉成使用者看得懂的中文。
  'Cannot create a direct conversation with yourself': '不能和自己建立私人對話',
  'Display name cannot be blank': '顯示名稱不能空白',
  'Email already exists': '電子郵件已被使用',
  'Email cannot be blank': '電子郵件不能空白',
  'Group member not found': '找不到群組成員',
  'Group not found': '找不到群組',
  'Invalid account or password': '帳號或密碼錯誤',
  'Invalid current password': '目前密碼錯誤',
  'Invalid or expired bearer token': '登入狀態已失效，請重新登入',
  'Invalid or expired reset token': '重設密碼連結無效或已過期',
  'Metadata must be valid JSON': '訊息附加資料必須是有效 JSON',
  'Missing bearer token': '請先登入',
  'New owner must be another group member': '請選擇其他成員作為新的擁有者',
  'No profile fields to update': '沒有可更新的個人資料欄位',
  'Only group owners or admins can add members': '只有群組擁有者或管理員可以加入成員',
  'Only group owners or admins can remove members': '只有群組擁有者或管理員可以移除成員',
  'Only the group owner can perform this action': '只有群組擁有者可以執行此操作',
  'Reply message does not belong to this conversation': '回覆的訊息不屬於這個對話',
  'Target user not found': '找不到指定使用者',
  'Transfer ownership before leaving the group': '請先轉移群組擁有者再離開',
  'Unsupported message type': '不支援的訊息類型',
  'Use owner transfer to change the group owner': '請使用轉移擁有者功能變更群組擁有者',
  'User not found': '找不到使用者',
  'Username already exists': '使用者名稱已被使用',
  'You are not a group member': '你不是這個群組的成員',
  'You are not a participant in this conversation': '你不是這個對話的成員',
  'You are not an active group member': '你不是這個群組的有效成員',
  'You cannot remove this group member': '你不能移除此群組成員'
}

const prefixMessages: Array<[string, (message: string) => string]> = [
  // 部分錯誤會在固定前綴後帶入動態值，需保留原本的細節。
  ['Group member not found: ', (message) => `找不到群組成員：${message.slice('Group member not found: '.length)}`]
]

export const localizeQGChatError = (error: unknown, fallback = '操作失敗，請稍後再試') => {
  const fetchError = error as FetchLikeError
  const code = fetchError.data?.code || ''
  const message = fetchError.data?.message || fetchError.message || ''

  if (codeMessages[code]) {
    return codeMessages[code]
  }

  // 先比對完整訊息，再比對前綴；都沒有命中時才回傳原訊息或預設文案。
  if (exactMessages[message]) {
    return exactMessages[message]
  }

  const prefixMatch = prefixMessages.find(([prefix]) => message.startsWith(prefix))
  if (prefixMatch) {
    return prefixMatch[1](message)
  }

  return message || fallback
}
