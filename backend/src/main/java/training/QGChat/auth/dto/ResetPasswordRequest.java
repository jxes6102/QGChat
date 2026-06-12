package training.QGChat.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 使用重設 token 設定新密碼。
public record ResetPasswordRequest(
        @NotBlank
        String token,

        @NotBlank
        @Size(min = 8, max = 72)
        String newPassword
) {
}
