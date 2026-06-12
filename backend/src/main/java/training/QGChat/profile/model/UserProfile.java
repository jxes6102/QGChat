package training.QGChat.profile.model;

import org.springframework.jdbc.core.RowMapper;
import training.QGChat.profile.dto.UserProfileResponse;

import java.time.OffsetDateTime;
import java.util.UUID;

// 個人資料流程使用的完整使用者模型，內含 passwordHash 供改密碼驗證。
public record UserProfile(
        UUID id,
        String username,
        String email,
        String displayName,
        String avatarUrl,
        String status,
        OffsetDateTime lastSeenAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        String passwordHash
) {
    public static RowMapper<UserProfile> rowMapper() {
        // 將 users 查詢結果映射成 UserProfile record。
        return (rs, rowNum) -> new UserProfile(
                rs.getObject("id", UUID.class),
                rs.getString("username"),
                rs.getString("email"),
                rs.getString("display_name"),
                rs.getString("avatar_url"),
                rs.getString("status"),
                rs.getObject("last_seen_at", OffsetDateTime.class),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class),
                rs.getString("password_hash")
        );
    }

    public UserProfileResponse toResponse() {
        // 對外回應時排除 passwordHash。
        return new UserProfileResponse(
                id,
                username,
                email,
                displayName,
                avatarUrl,
                status,
                lastSeenAt,
                createdAt,
                updatedAt
        );
    }
}
