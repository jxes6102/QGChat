package training.QGChat.chat.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import training.QGChat.auth.exception.AuthException;
import training.QGChat.chat.dto.ChatMessageResponse;
import training.QGChat.chat.dto.ConversationResponse;
import training.QGChat.chat.dto.CreateDirectConversationRequest;
import training.QGChat.chat.dto.CreateGroupConversationRequest;
import training.QGChat.chat.dto.MarkReadRequest;
import training.QGChat.chat.dto.SendMessageRequest;
import training.QGChat.chat.model.ChatMessage;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class ChatService {
    private static final String BEARER_PREFIX = "Bearer ";
    // 對應 schema 裡的 message_type enum。
    private static final Set<String> MESSAGE_TYPES = Set.of("TEXT", "IMAGE", "FILE", "SYSTEM");

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public ChatService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<ConversationResponse> listConversations(String authorization) {
        UUID userId = requireUserId(authorization);
        // 一次查出聊天室基本資料、直聊對象、最後一則訊息與未讀數，避免前端逐間聊天室補查。
        return jdbcTemplate.query("""
                        SELECT c.id,
                               c.type::text AS type,
                               c.group_id,
                               g.name AS group_name,
                               g.avatar_url AS group_avatar_url,
                               du.id AS direct_user_id,
                               du.display_name AS direct_display_name,
                               du.avatar_url AS direct_avatar_url,
                               lm.id AS last_message_id,
                               lm.sender_id AS last_sender_id,
                               lms.display_name AS last_sender_display_name,
                               lm.type::text AS last_type,
                               lm.content AS last_content,
                               lm.metadata::text AS last_metadata,
                               lm.reply_to_message_id AS last_reply_to_message_id,
                               lm.sent_at AS last_sent_at,
                               lm.edited_at AS last_edited_at,
                               lm.deleted_at AS last_deleted_at,
                               COUNT(um.id) AS unread_count,
                               c.updated_at
                        FROM conversation_participants cp
                        JOIN conversations c ON c.id = cp.conversation_id
                        LEFT JOIN chat_groups g ON g.id = c.group_id
                        LEFT JOIN direct_conversations dc ON dc.conversation_id = c.id
                        LEFT JOIN users du ON du.id = CASE
                            -- 直聊表固定存兩個人，這裡取出「不是目前使用者」的另一方。
                            WHEN dc.user_one_id = ? THEN dc.user_two_id
                            ELSE dc.user_one_id
                        END
                        LEFT JOIN LATERAL (
                            -- 每個聊天室只取最新一則未刪除訊息，供列表預覽使用。
                            SELECT m.*
                            FROM messages m
                            WHERE m.conversation_id = c.id
                              AND m.deleted_at IS NULL
                            ORDER BY m.sent_at DESC
                            LIMIT 1
                        ) lm ON TRUE
                        LEFT JOIN users lms ON lms.id = lm.sender_id
                        LEFT JOIN messages um ON um.conversation_id = c.id
                            -- 未讀數只計算別人發出的、且晚於 last_read_message_id 的訊息。
                            AND um.sender_id IS DISTINCT FROM ?
                            AND um.deleted_at IS NULL
                            AND (cp.last_read_message_id IS NULL OR um.sent_at > (
                                SELECT sent_at FROM messages WHERE id = cp.last_read_message_id
                            ))
                        WHERE cp.user_id = ?
                        GROUP BY c.id, c.type, c.group_id, g.name, g.avatar_url,
                                 du.id, du.display_name, du.avatar_url,
                                 lm.id, lm.sender_id, lms.display_name, lm.type, lm.content, lm.metadata,
                                 lm.reply_to_message_id, lm.sent_at, lm.edited_at, lm.deleted_at, c.updated_at
                        ORDER BY COALESCE(lm.sent_at, c.updated_at) DESC
                        """,
                (rs, rowNum) -> new ConversationResponse(
                        rs.getObject("id", UUID.class),
                        rs.getString("type"),
                        rs.getObject("group_id", UUID.class),
                        rs.getString("group_name"),
                        rs.getString("group_avatar_url"),
                        rs.getObject("direct_user_id", UUID.class),
                        rs.getString("direct_display_name"),
                        rs.getString("direct_avatar_url"),
                        rs.getObject("last_message_id", UUID.class) == null ? null : new ChatMessageResponse(
                                rs.getObject("last_message_id", UUID.class),
                                rs.getObject("id", UUID.class),
                                rs.getObject("last_sender_id", UUID.class),
                                rs.getString("last_sender_display_name"),
                                rs.getString("last_type"),
                                rs.getString("last_content"),
                                rs.getString("last_metadata"),
                                rs.getObject("last_reply_to_message_id", UUID.class),
                                rs.getObject("last_sent_at", OffsetDateTime.class),
                                rs.getObject("last_edited_at", OffsetDateTime.class),
                                rs.getObject("last_deleted_at", OffsetDateTime.class)
                        ),
                        rs.getLong("unread_count"),
                        rs.getObject("updated_at", OffsetDateTime.class)
                ),
                userId,
                userId,
                userId);
    }

    @Transactional
    public ConversationResponse createDirectConversation(String authorization, CreateDirectConversationRequest request) {
        UUID userId = requireUserId(authorization);
        // 由 username 查出對方 user id，API 使用上比 UUID 更直覺。
        UUID targetUserId = findActiveUserIdByUsername(request.targetUsername())
                .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "Target user not found"));
        // 一對一聊天室不允許自己和自己建立。
        if (userId.equals(targetUserId)) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "Cannot create a direct conversation with yourself");
        }

        // 若兩人已經有直聊，就回傳既有 conversation，避免重複建立。
        Optional<UUID> existing = findDirectConversation(userId, targetUserId);
        UUID conversationId = existing.orElseGet(() -> insertDirectConversation(userId, targetUserId));
        return getConversation(authorization, conversationId);
    }

    @Transactional
    public ConversationResponse createGroupConversation(String authorization, CreateGroupConversationRequest request) {
        UUID ownerId = requireUserId(authorization);
        // 先建立群組主檔，再建立對應的 GROUP conversation。
        UUID groupId = jdbcTemplate.queryForObject("""
                        INSERT INTO chat_groups (name, description, avatar_url, owner_id, is_private)
                        VALUES (?, ?, ?, ?, ?)
                        RETURNING id
                        """,
                UUID.class,
                request.name().trim(),
                request.description(),
                request.avatarUrl(),
                ownerId,
                request.isPrivate() == null || request.isPrivate());

        // conversations.group_id 對 chat_groups.id 是一對一，schema 用 UNIQUE 保護。
        UUID conversationId = jdbcTemplate.queryForObject("""
                        INSERT INTO conversations (type, group_id)
                        VALUES ('GROUP', ?)
                        RETURNING id
                        """,
                UUID.class,
                groupId);

        // 建立者同時是 group owner 與 conversation participant。
        jdbcTemplate.update("""
                        INSERT INTO group_members (group_id, user_id, role, status, joined_at)
                        VALUES (?, ?, 'OWNER', 'ACTIVE', CURRENT_TIMESTAMP)
                        """,
                groupId,
                ownerId);
        jdbcTemplate.update("""
                        INSERT INTO conversation_participants (conversation_id, user_id)
                        VALUES (?, ?)
                        """,
                conversationId,
                ownerId);

        // 其他成員也要同時寫入 group_members 與 conversation_participants。
        for (UUID memberId : normalizedMemberUsernames(request.memberUsernames(), ownerId)) {
            jdbcTemplate.update("""
                            INSERT INTO group_members (group_id, user_id, role, status, invited_by, joined_at)
                            VALUES (?, ?, 'MEMBER', 'ACTIVE', ?, CURRENT_TIMESTAMP)
                            ON CONFLICT (group_id, user_id) DO NOTHING
                            """,
                    groupId,
                    memberId,
                    ownerId);
            jdbcTemplate.update("""
                            INSERT INTO conversation_participants (conversation_id, user_id)
                            VALUES (?, ?)
                            ON CONFLICT (conversation_id, user_id) DO NOTHING
                            """,
                    conversationId,
                    memberId);
        }

        return getConversation(authorization, conversationId);
    }

    @Transactional(readOnly = true)
    public ConversationResponse getConversation(String authorization, UUID conversationId) {
        UUID userId = requireUserId(authorization);
        // 只有聊天室參與者可以查看聊天室資訊。
        requireParticipant(conversationId, userId);
        return listConversations(authorization).stream()
                .filter(conversation -> conversation.id().equals(conversationId))
                .findFirst()
                .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "Conversation not found"));
    }

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> listMessages(String authorization, UUID conversationId, int limit, OffsetDateTime before) {
        UUID userId = requireUserId(authorization);
        requireParticipant(conversationId, userId);
        // 限制單次查詢筆數，避免前端傳過大 limit 造成資料庫壓力。
        int pageSize = Math.min(Math.max(limit, 1), 100);

        List<Object> params = new ArrayList<>();
        params.add(conversationId);
        String beforeFilter = "";
        if (before != null) {
            // before 作為時間游標，載入更舊訊息。
            beforeFilter = " AND m.sent_at < ? ";
            params.add(before);
        }
        params.add(pageSize);

        return jdbcTemplate.query("""
                        SELECT m.id, m.conversation_id, m.sender_id, u.display_name AS sender_display_name,
                               m.type::text AS type, m.content, m.metadata::text AS metadata,
                               m.reply_to_message_id, m.sent_at, m.edited_at, m.deleted_at
                        FROM messages m
                        LEFT JOIN users u ON u.id = m.sender_id
                        WHERE m.conversation_id = ?
                          AND m.deleted_at IS NULL
                        """ + beforeFilter + """
                        ORDER BY m.sent_at DESC
                        LIMIT ?
                        """,
                ChatMessage.rowMapper(),
                params.toArray()).stream().map(ChatMessage::toResponse).toList();
    }

    @Transactional
    public ChatMessageResponse sendMessage(String authorization, UUID conversationId, SendMessageRequest request) {
        UUID senderId = requireUserId(authorization);
        // 送訊息前先確認使用者確實在聊天室內。
        requireParticipant(conversationId, senderId);
        String messageType = normalizeMessageType(request.type());
        String metadata = normalizeMetadata(request.metadata());

        // 回覆訊息必須屬於同一個 conversation，避免跨聊天室引用。
        if (request.replyToMessageId() != null && !messageExistsInConversation(conversationId, request.replyToMessageId())) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "Reply message does not belong to this conversation");
        }

        ChatMessage message = jdbcTemplate.queryForObject("""
                        INSERT INTO messages (conversation_id, sender_id, type, content, metadata, reply_to_message_id)
                        VALUES (?, ?, ?::message_type, ?, CAST(? AS jsonb), ?)
                        RETURNING id, conversation_id, sender_id,
                                  (SELECT display_name FROM users WHERE id = sender_id) AS sender_display_name,
                                  type::text AS type, content, metadata::text AS metadata,
                                  reply_to_message_id, sent_at, edited_at, deleted_at
                        """,
                ChatMessage.rowMapper(),
                conversationId,
                senderId,
                messageType,
                request.content().trim(),
                metadata,
                request.replyToMessageId());

        // 更新 conversation.updated_at，讓聊天室列表可以依最後活動時間排序。
        jdbcTemplate.update("UPDATE conversations SET updated_at = CURRENT_TIMESTAMP WHERE id = ?", conversationId);
        // 自己送出的訊息視為已讀，並同步更新 last_read_message_id。
        jdbcTemplate.update("""
                        INSERT INTO message_reads (message_id, user_id)
                        VALUES (?, ?)
                        ON CONFLICT (message_id, user_id) DO NOTHING
                        """,
                message.id(),
                senderId);
        jdbcTemplate.update("""
                        UPDATE conversation_participants
                        SET last_read_message_id = ?
                        WHERE conversation_id = ?
                          AND user_id = ?
                        """,
                message.id(),
                conversationId,
                senderId);

        return message.toResponse();
    }

    @Transactional
    public ChatMessageResponse markRead(String authorization, UUID conversationId, MarkReadRequest request) {
        UUID userId = requireUserId(authorization);
        requireParticipant(conversationId, userId);
        // 先確認 message 存在且屬於此聊天室。
        ChatMessage message = findMessage(conversationId, request.messageId())
                .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "Message not found"));

        // message_reads 保留每則訊息的已讀紀錄，conversation_participants 則記錄最新讀到哪一則。
        jdbcTemplate.update("""
                        INSERT INTO message_reads (message_id, user_id)
                        VALUES (?, ?)
                        ON CONFLICT (message_id, user_id) DO UPDATE SET read_at = CURRENT_TIMESTAMP
                        """,
                request.messageId(),
                userId);
        jdbcTemplate.update("""
                        UPDATE conversation_participants
                        SET last_read_message_id = ?
                        WHERE conversation_id = ?
                          AND user_id = ?
                        """,
                request.messageId(),
                conversationId,
                userId);

        return message.toResponse();
    }

    private UUID requireUserId(String authorization) {
        String token = parseBearerToken(authorization)
                .orElseThrow(() -> new AuthException(HttpStatus.UNAUTHORIZED, "Missing bearer token"));
        try {
            // 系統目前用 guest_sessions 保存登入 token，資料庫只存 SHA-256 hash。
            return jdbcTemplate.queryForObject("""
                            SELECT u.id
                            FROM guest_sessions gs
                            JOIN users u ON u.id = gs.user_id
                            WHERE gs.session_token_hash = ?
                              AND gs.revoked_at IS NULL
                              AND gs.expires_at > CURRENT_TIMESTAMP
                              AND u.status = 'ACTIVE'
                            """,
                    UUID.class,
                    hash(token));
        } catch (EmptyResultDataAccessException exception) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "Invalid or expired bearer token");
        }
    }

    private Optional<String> parseBearerToken(String authorization) {
        // 只接受標準 Authorization: Bearer <token> 格式。
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return Optional.empty();
        }
        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? Optional.empty() : Optional.of(token);
    }

    private String hash(String value) {
        try {
            // 與 AuthService 使用相同 hash 方式，才能查回 guest_sessions.session_token_hash。
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private void requireActiveUser(UUID userId) {
        // 建立聊天室或邀請成員前，先確認目標使用者存在且啟用。
        Boolean exists = jdbcTemplate.queryForObject("""
                        SELECT EXISTS (
                            SELECT 1 FROM users WHERE id = ? AND status = 'ACTIVE'
                        )
                        """,
                Boolean.class,
                userId);
        if (!Boolean.TRUE.equals(exists)) {
            throw new AuthException(HttpStatus.NOT_FOUND, "User not found");
        }
    }

    private Optional<UUID> findActiveUserIdByUsername(String username) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject("""
                            SELECT id
                            FROM users
                            WHERE LOWER(username) = LOWER(?)
                              AND status = 'ACTIVE'
                            """,
                    UUID.class,
                    username.trim()));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    private void requireParticipant(UUID conversationId, UUID userId) {
        // 防止非聊天室成員讀取訊息、送訊息或標記已讀。
        Boolean exists = jdbcTemplate.queryForObject("""
                        SELECT EXISTS (
                            SELECT 1
                            FROM conversation_participants
                            WHERE conversation_id = ? AND user_id = ?
                        )
                        """,
                Boolean.class,
                conversationId,
                userId);
        if (!Boolean.TRUE.equals(exists)) {
            throw new AuthException(HttpStatus.FORBIDDEN, "You are not a participant in this conversation");
        }
    }

    private Optional<UUID> findDirectConversation(UUID userId, UUID targetUserId) {
        try {
            // 使用 LEAST/GREATEST 讓 A-B 與 B-A 視為同一組直聊。
            return Optional.ofNullable(jdbcTemplate.queryForObject("""
                            SELECT conversation_id
                            FROM direct_conversations
                            WHERE LEAST(user_one_id, user_two_id) = LEAST(?::uuid, ?::uuid)
                              AND GREATEST(user_one_id, user_two_id) = GREATEST(?::uuid, ?::uuid)
                            """,
                    UUID.class,
                    userId,
                    targetUserId,
                    userId,
                    targetUserId));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    private UUID insertDirectConversation(UUID userId, UUID targetUserId) {
        // DIRECT conversation 不綁 group_id。
        UUID conversationId = jdbcTemplate.queryForObject("""
                        INSERT INTO conversations (type)
                        VALUES ('DIRECT')
                        RETURNING id
                        """,
                UUID.class);

        // user_one_id/user_two_id 以固定排序保存，搭配 unique index 避免重複直聊。
        jdbcTemplate.update("""
                        INSERT INTO direct_conversations (conversation_id, user_one_id, user_two_id)
                        VALUES (?, LEAST(?::uuid, ?::uuid), GREATEST(?::uuid, ?::uuid))
                        """,
                conversationId,
                userId,
                targetUserId,
                userId,
                targetUserId);
        jdbcTemplate.update("""
                        INSERT INTO conversation_participants (conversation_id, user_id)
                        VALUES (?, ?), (?, ?)
                        """,
                conversationId,
                userId,
                conversationId,
                targetUserId);

        return conversationId;
    }

    private Set<UUID> normalizedMemberUsernames(Set<String> memberUsernames, UUID ownerId) {
        // 建立者已在前面加入，這裡排除 owner 避免重複寫入。
        if (memberUsernames == null || memberUsernames.isEmpty()) {
            return Set.of();
        }
        return memberUsernames.stream()
                .map(username -> findActiveUserIdByUsername(username)
                        .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "Group member not found: " + username)))
                .filter(memberId -> !ownerId.equals(memberId))
                .collect(java.util.stream.Collectors.toSet());
    }

    private String normalizeMessageType(String type) {
        // 未指定 type 時預設為文字訊息。
        String messageType = type == null || type.isBlank() ? "TEXT" : type.trim().toUpperCase(Locale.ROOT);
        if (!MESSAGE_TYPES.contains(messageType)) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "Unsupported message type");
        }
        return messageType;
    }

    private String normalizeMetadata(String metadata) {
        // metadata 存 JSONB；空值統一存成空物件。
        if (metadata == null || metadata.isBlank()) {
            return "{}";
        }
        try {
            // 先 parse 再序列化，確保寫入資料庫的一定是合法 JSON。
            Object parsed = objectMapper.readValue(metadata, Object.class);
            return objectMapper.writeValueAsString(parsed);
        } catch (JsonProcessingException exception) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "Metadata must be valid JSON");
        }
    }

    private boolean messageExistsInConversation(UUID conversationId, UUID messageId) {
        // 檢查回覆對象是否仍存在且未被刪除。
        Boolean exists = jdbcTemplate.queryForObject("""
                        SELECT EXISTS (
                            SELECT 1 FROM messages
                            WHERE id = ?
                              AND conversation_id = ?
                              AND deleted_at IS NULL
                        )
                        """,
                Boolean.class,
                messageId,
                conversationId);
        return Boolean.TRUE.equals(exists);
    }

    private Optional<ChatMessage> findMessage(UUID conversationId, UUID messageId) {
        try {
            // markRead 需要回傳訊息本體，所以這裡查完整訊息欄位。
            return Optional.ofNullable(jdbcTemplate.queryForObject("""
                            SELECT m.id, m.conversation_id, m.sender_id, u.display_name AS sender_display_name,
                                   m.type::text AS type, m.content, m.metadata::text AS metadata,
                                   m.reply_to_message_id, m.sent_at, m.edited_at, m.deleted_at
                            FROM messages m
                            LEFT JOIN users u ON u.id = m.sender_id
                            WHERE m.id = ?
                              AND m.conversation_id = ?
                              AND m.deleted_at IS NULL
                            """,
                    ChatMessage.rowMapper(),
                    messageId,
                    conversationId));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }
}
