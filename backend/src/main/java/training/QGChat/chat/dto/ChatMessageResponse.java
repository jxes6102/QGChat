package training.QGChat.chat.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

// 回傳給前端的單一聊天訊息資料。
public record ChatMessageResponse(
        UUID id,
        UUID conversationId,
        UUID senderId,
        String senderDisplayName,
        String type,
        String content,
        String metadata,
        UUID replyToMessageId,
        OffsetDateTime sentAt,
        OffsetDateTime editedAt,
        OffsetDateTime deletedAt
) {
}
