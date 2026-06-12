package training.QGChat.profile.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

// 對外回傳的個人資料，不包含 passwordHash 等敏感欄位。
public record UserProfileResponse(
        UUID id,
        String username,
        String email,
        String displayName,
        String avatarUrl,
        String status,
        OffsetDateTime lastSeenAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
