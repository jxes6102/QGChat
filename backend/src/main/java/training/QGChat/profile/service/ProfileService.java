package training.QGChat.profile.service;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import training.QGChat.auth.exception.AuthException;
import training.QGChat.auth.model.AuthenticatedSession;
import training.QGChat.auth.service.SessionAuthService;
import training.QGChat.profile.dto.ChangePasswordRequest;
import training.QGChat.profile.dto.UpdateProfileRequest;
import training.QGChat.profile.dto.UserProfileResponse;
import training.QGChat.profile.model.UserProfile;

import java.util.UUID;

@Service
public class ProfileService {
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final SessionAuthService sessionAuthService;

    public ProfileService(
            JdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder,
            SessionAuthService sessionAuthService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.sessionAuthService = sessionAuthService;
    }

    public UserProfileResponse getProfile(String authorization) {
        return getCurrentUser(authorization).toResponse();
    }

    @Transactional
    public UserProfileResponse updateProfile(String authorization, UpdateProfileRequest request) {
        UserProfile currentUser = getCurrentUser(authorization);
        String displayName = normalizeRequiredText(request.displayName(), "Display name cannot be blank");
        String email = normalizeEmail(request.email());
        String avatarUrl = normalizeOptionalText(request.avatarUrl());

        if (displayName == null && email == null && request.avatarUrl() == null) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "No profile fields to update");
        }

        if (email != null && existsByEmailForOtherUser(email, currentUser.id())) {
            throw new AuthException(HttpStatus.CONFLICT, "Email already exists");
        }

        jdbcTemplate.update("""
                        UPDATE users
                        SET display_name = COALESCE(?, display_name),
                            email = COALESCE(?, email),
                            avatar_url = CASE WHEN ? THEN ? ELSE avatar_url END
                        WHERE id = ?
                        """,
                displayName,
                email,
                request.avatarUrl() != null,
                avatarUrl,
                currentUser.id());

        return findProfileById(currentUser.id()).toResponse();
    }

    @Transactional
    public boolean changePassword(String authorization, ChangePasswordRequest request) {
        AuthenticatedSession session = sessionAuthService.requireActiveMemberSession(authorization);
        UserProfile currentUser = findProfileById(session.userId());

        if (currentUser.passwordHash() == null
                || !passwordEncoder.matches(request.currentPassword(), currentUser.passwordHash())) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "Invalid current password");
        }

        jdbcTemplate.update("""
                        UPDATE users
                        SET password_hash = ?
                        WHERE id = ?
                        """,
                passwordEncoder.encode(request.newPassword()),
                currentUser.id());

        jdbcTemplate.update("""
                UPDATE guest_sessions
                SET revoked_at = CURRENT_TIMESTAMP
                WHERE user_id = ?
                  AND session_token_hash <> ?
                  AND revoked_at IS NULL
                """, currentUser.id(), session.tokenHash());

        return true;
    }

    private UserProfile getCurrentUser(String authorization) {
        AuthenticatedSession session = sessionAuthService.requireActiveMemberSession(authorization);
        return findProfileById(session.userId());
    }

    private UserProfile findProfileById(UUID userId) {
        return jdbcTemplate.queryForObject("""
                        SELECT id, username, email, display_name, avatar_url, status,
                               last_seen_at, created_at, updated_at, password_hash
                        FROM users
                        WHERE id = ?
                          AND account_type = 'MEMBER'
                          AND status = 'ACTIVE'
                        """,
                UserProfile.rowMapper(),
                userId);
    }

    private boolean existsByEmailForOtherUser(String email, UUID userId) {
        Boolean exists = jdbcTemplate.queryForObject("""
                        SELECT EXISTS (
                            SELECT 1
                            FROM users
                            WHERE LOWER(email) = LOWER(?)
                              AND id <> ?
                        )
                        """,
                Boolean.class,
                email,
                userId);
        return Boolean.TRUE.equals(exists);
    }

    private String normalizeRequiredText(String value, String blankMessage) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new AuthException(HttpStatus.BAD_REQUEST, blankMessage);
        }
        return normalized;
    }

    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeEmail(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "Email cannot be blank");
        }
        return normalized;
    }
}
