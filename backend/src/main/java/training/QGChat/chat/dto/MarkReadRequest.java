package training.QGChat.chat.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

// 將指定訊息標記為目前使用者在該對話中的最後已讀訊息。
public record MarkReadRequest(
        @NotNull UUID messageId
) {
}
