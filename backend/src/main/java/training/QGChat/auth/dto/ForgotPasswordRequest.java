package training.QGChat.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

// 忘記密碼請求，只需要使用者註冊 email。
public record ForgotPasswordRequest(
        @NotBlank
        @Email
        String email
) {
}
