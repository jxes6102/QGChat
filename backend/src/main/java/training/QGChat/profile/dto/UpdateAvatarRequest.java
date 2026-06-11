package training.QGChat.profile.dto;

import jakarta.validation.constraints.Size;

public record UpdateAvatarRequest(
        @Size(max = 2048)
        String avatarUrl
) {
}
