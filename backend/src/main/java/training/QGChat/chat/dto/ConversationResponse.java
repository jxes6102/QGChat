package training.QGChat.chat.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

// 對話列表與單一對話摘要的回應資料，涵蓋群組與私聊兩種型態。
public record ConversationResponse(
        UUID id,
        String type,
        UUID groupId,
        String groupName,
        String groupAvatarUrl,
        // 目前登入者在此群組中的角色；私聊時為 null。
        String currentUserGroupRole,
        UUID directUserId,
        String directDisplayName,
        String directAvatarUrl,
        ChatMessageResponse lastMessage,
        long unreadCount,
        OffsetDateTime updatedAt
) {
}
