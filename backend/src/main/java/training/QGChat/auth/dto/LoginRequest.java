package training.QGChat.auth.dto;

import jakarta.validation.constraints.NotBlank;

// 登入請求；account 可是 username 或 email。
public record LoginRequest(
        @NotBlank
        String account,

        @NotBlank
        String password
) {
}
