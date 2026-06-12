package training.QGChat.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 建立一對一私聊時，前端只需要傳入目標使用者的 username。
public record CreateDirectConversationRequest(
        @NotBlank @Size(max = 50) String targetUsername
) {
}
