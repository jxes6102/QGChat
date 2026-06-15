package training.QGChat.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record WebSocketSendMessageRequest(
        @NotNull UUID conversationId,
        String type,
        @NotBlank String content,
        String metadata,
        UUID replyToMessageId
) {

    public SendMessageRequest toSendMessageRequest() {
        // 轉成 REST 共用 request，讓 Socket 與 REST 走同一套 service 驗證與寫入流程。
        return new SendMessageRequest(type, content, metadata, replyToMessageId);
    }
}
