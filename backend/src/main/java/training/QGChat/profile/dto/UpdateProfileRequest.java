package training.QGChat.profile.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

// 個人資料更新請求；所有欄位皆為選填，service 會判斷實際更新項目。
public record UpdateProfileRequest(
        @Size(max = 80)
        String displayName,

        @Size(max = 2048)
        String avatarUrl,

        @Email
        @Size(max = 255)
        String email
) {
}
