package training.QGChat.chat.model;

import org.springframework.jdbc.core.RowMapper;
import training.QGChat.chat.dto.ChatMessageResponse;

import java.time.OffsetDateTime;
import java.util.UUID;

// 對應 messages 查詢結果的內部資料模型。
public record ChatMessage(
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

    public static RowMapper<ChatMessage> rowMapper() {
        // JdbcTemplate 共用 mapper，確保 REST 與 Socket 取得一致的訊息欄位。
        return (rs, rowNum) -> new ChatMessage(
                rs.getObject("id", UUID.class),
                rs.getObject("conversation_id", UUID.class),
                rs.getObject("sender_id", UUID.class),
                rs.getString("sender_display_name"),
                rs.getString("type"),
                rs.getString("content"),
                rs.getString("metadata"),
                rs.getObject("reply_to_message_id", UUID.class),
                rs.getObject("sent_at", OffsetDateTime.class),
                rs.getObject("edited_at", OffsetDateTime.class),
                rs.getObject("deleted_at", OffsetDateTime.class)
        );
    }

    public ChatMessageResponse toResponse() {
        // 將資料庫模型轉成 API 回應，避免 controller 直接依賴 RowMapper 結構。
        return new ChatMessageResponse(
                id,
                conversationId,
                senderId,
                senderDisplayName,
                type,
                content,
                metadata,
                replyToMessageId,
                sentAt,
                editedAt,
                deletedAt
        );
    }
}
