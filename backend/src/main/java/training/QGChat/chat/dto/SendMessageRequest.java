package training.QGChat.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

// REST API 發送訊息的請求資料；metadata 必須是 JSON 字串。
public record SendMessageRequest(
        String type,
        @NotBlank @Size(max = 10000) String content,
        String metadata,
        UUID replyToMessageId
) {
}
