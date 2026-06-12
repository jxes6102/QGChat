package training.QGChat.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Set;

// 建立群組對話的輸入資料；memberUsernames 不含 owner，service 會自動加入建立者。
public record CreateGroupConversationRequest(
        @NotBlank @Size(max = 120) String name,
        String description,
        String avatarUrl,
        Boolean isPrivate,
        Set<@NotBlank @Size(max = 50) String> memberUsernames
) {
}
