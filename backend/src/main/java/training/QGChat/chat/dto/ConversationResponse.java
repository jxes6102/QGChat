package training.QGChat.chat.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

// 聊天室列表與聊天室詳情共用的回應格式。
public record ConversationResponse(
        UUID id,
        String type,
        UUID groupId,
        String groupName,
        String groupAvatarUrl,
        UUID directUserId,
        String directDisplayName,
        String directAvatarUrl,
        ChatMessageResponse lastMessage,
        long unreadCount,
        OffsetDateTime updatedAt
) {
}
