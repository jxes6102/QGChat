package training.QGChat.profile.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

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
