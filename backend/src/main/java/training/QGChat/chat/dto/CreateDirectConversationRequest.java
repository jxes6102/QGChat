package training.QGChat.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 建立一對一聊天室時，前端只需要傳對方 username。
public record CreateDirectConversationRequest(
        @NotBlank @Size(max = 50) String targetUsername
) {
}
