package training.QGChat.profile.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 修改密碼時需要同時提供目前密碼與新密碼。
public record ChangePasswordRequest(
        @NotBlank
        String currentPassword,

        @NotBlank
        @Size(min = 8, max = 72)
        String newPassword
) {
}
