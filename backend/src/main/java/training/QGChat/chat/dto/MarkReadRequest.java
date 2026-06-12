package training.QGChat.chat.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

// 將某一則訊息標記為目前使用者在聊天室中的最後已讀。
public record MarkReadRequest(
        @NotNull UUID messageId
) {
}
