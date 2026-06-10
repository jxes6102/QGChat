package training.QGChat.auth.model;

import org.springframework.jdbc.core.RowMapper;

import java.util.UUID;

public record UserAccount(
        UUID id,
        String username,
        String email,
        String displayName,
        String passwordHash,
        String status
) {
    public static RowMapper<UserAccount> rowMapper() {
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
