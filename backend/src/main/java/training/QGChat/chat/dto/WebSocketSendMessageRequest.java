package training.QGChat.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

// WebSocket 送訊息格式；authorization 放在 payload，方便 STOMP client 傳遞 token。
public record WebSocketSendMessageRequest(
        @NotBlank String authorization,
        @NotNull UUID conversationId,
        String type,
        @NotBlank String content,
        String metadata,
        UUID replyToMessageId
) {

    public SendMessageRequest toSendMessageRequest() {
        // 轉成 REST 共用的 request，讓 Socket 與 REST 使用同一套送訊息邏輯。
        return new SendMessageRequest(type, content, metadata, replyToMessageId);
    }
}
