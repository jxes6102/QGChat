package training.QGChat.auth.model;

import org.springframework.jdbc.core.RowMapper;

import java.util.UUID;

// 認證流程使用的帳號資料，只查登入與 session 建立所需欄位。
public record UserAccount(
        UUID id,
        String username,
        String email,
        String displayName,
        String passwordHash,
        String status
) {
    public static RowMapper<UserAccount> rowMapper() {
        // 將 users 查詢結果映射成 UserAccount record。
        return (rs, rowNum) -> new UserAccount(
                rs.getObject("id", UUID.class),
                rs.getString("username"),
                rs.getString("email"),
                rs.getString("display_name"),
                rs.getString("password_hash"),
                rs.getString("status")
        );
    }
}
