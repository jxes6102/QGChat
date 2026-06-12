package training.QGChat.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Set;

// 建立群組聊天室的請求資料；memberUsernames 不含建立者也可以，service 會自動加入 owner。
public record CreateGroupConversationRequest(
        @NotBlank @Size(max = 120) String name,
        String description,
        String avatarUrl,
        Boolean isPrivate,
        Set<@NotBlank @Size(max = 50) String> memberUsernames
) {
}
