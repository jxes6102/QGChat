-- QGChat PostgreSQL schema
-- Supports guest/member accounts, friend relationships, groups, and chat messages.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- 開發階段重建資料表用：如果表存在就刪除
DROP TABLE IF EXISTS message_reads CASCADE;
DROP TABLE IF EXISTS email_delivery_logs CASCADE;
DROP TABLE IF EXISTS password_reset_tokens CASCADE;
DROP TABLE IF EXISTS messages CASCADE;
DROP TABLE IF EXISTS direct_conversations CASCADE;
DROP TABLE IF EXISTS conversation_participants CASCADE;
DROP TABLE IF EXISTS conversations CASCADE;
DROP TABLE IF EXISTS group_members CASCADE;
DROP TABLE IF EXISTS chat_groups CASCADE;
DROP TABLE IF EXISTS friendships CASCADE;
DROP TABLE IF EXISTS guest_sessions CASCADE;
DROP TABLE IF EXISTS users CASCADE;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'user_account_type') THEN
        CREATE TYPE user_account_type AS ENUM ('GUEST', 'MEMBER');
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'user_status') THEN
        CREATE TYPE user_status AS ENUM ('ACTIVE', 'DISABLED', 'DELETED');
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'friendship_status') THEN
        CREATE TYPE friendship_status AS ENUM ('PENDING', 'ACCEPTED', 'BLOCKED', 'DECLINED');
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'group_role') THEN
        CREATE TYPE group_role AS ENUM ('OWNER', 'ADMIN', 'MEMBER');
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'group_member_status') THEN
        CREATE TYPE group_member_status AS ENUM ('ACTIVE', 'INVITED', 'LEFT', 'REMOVED');
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'conversation_type') THEN
        CREATE TYPE conversation_type AS ENUM ('DIRECT', 'GROUP');
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'message_type') THEN
        CREATE TYPE message_type AS ENUM ('TEXT', 'IMAGE', 'FILE', 'SYSTEM');
    END IF;
END $$;

-- users: 使用者主檔，存放訪客與會員的基本資料。
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_type user_account_type NOT NULL DEFAULT 'GUEST',
    username VARCHAR(50),
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255),
    display_name VARCHAR(80) NOT NULL,
    avatar_url TEXT,
    status user_status NOT NULL DEFAULT 'ACTIVE',
    last_seen_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT users_member_username_required
        CHECK (account_type = 'GUEST' OR username IS NOT NULL),
    CONSTRAINT users_member_password_required
        CHECK (account_type = 'GUEST' OR password_hash IS NOT NULL),
    CONSTRAINT users_email_format
        CHECK (email IS NULL OR email ~* '^[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}$'),
    CONSTRAINT users_member_email_required
        CHECK (account_type = 'GUEST' OR email IS NOT NULL)
);

COMMENT ON TABLE users IS '使用者主檔，存放訪客與會員的基本資料、登入身分、狀態與最後上線時間。';

CREATE UNIQUE INDEX IF NOT EXISTS ux_users_username
    ON users (LOWER(username))
    WHERE username IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_users_email
    ON users (LOWER(email))
    WHERE email IS NOT NULL;

CREATE INDEX IF NOT EXISTS ix_users_account_type ON users (account_type);
CREATE INDEX IF NOT EXISTS ix_users_status ON users (status);

-- guest_sessions: 訪客 Session 紀錄，管理訪客臨時登入狀態。
CREATE TABLE IF NOT EXISTS guest_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    session_token_hash VARCHAR(255) NOT NULL UNIQUE,
    ip_address INET,
    user_agent TEXT,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at TIMESTAMPTZ,
    CONSTRAINT guest_sessions_valid_expiry
        CHECK (expires_at > created_at)
);

COMMENT ON TABLE guest_sessions IS '訪客 Session 紀錄，存放訪客臨時登入憑證、來源資訊、到期時間與撤銷時間。';

CREATE INDEX IF NOT EXISTS ix_guest_sessions_user_id ON guest_sessions (user_id);
CREATE INDEX IF NOT EXISTS ix_guest_sessions_expires_at ON guest_sessions (expires_at);

-- friendships: 好友關係表，記錄好友邀請與好友狀態。
CREATE TABLE IF NOT EXISTS friendships (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    requester_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    addressee_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    status friendship_status NOT NULL DEFAULT 'PENDING',
    requested_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    responded_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT friendships_no_self_request
        CHECK (requester_id <> addressee_id)
);

COMMENT ON TABLE friendships IS '好友關係表，記錄好友邀請、接受、封鎖或拒絕狀態，避免兩位使用者重複建立好友關係。';

CREATE UNIQUE INDEX IF NOT EXISTS ux_friendships_pair
    ON friendships (LEAST(requester_id, addressee_id), GREATEST(requester_id, addressee_id));

CREATE INDEX IF NOT EXISTS ix_friendships_requester_id ON friendships (requester_id);
CREATE INDEX IF NOT EXISTS ix_friendships_addressee_id ON friendships (addressee_id);
CREATE INDEX IF NOT EXISTS ix_friendships_status ON friendships (status);

-- chat_groups: 群組主檔，存放群組基本資料與群主資訊。
CREATE TABLE IF NOT EXISTS chat_groups (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(120) NOT NULL,
    description TEXT,
    avatar_url TEXT,
    owner_id UUID NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    is_private BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE chat_groups IS '群組主檔，存放群組名稱、描述、頭像、群主與是否為私人群組等資料。';

CREATE INDEX IF NOT EXISTS ix_chat_groups_owner_id ON chat_groups (owner_id);
CREATE INDEX IF NOT EXISTS ix_chat_groups_name ON chat_groups (LOWER(name));

-- group_members: 群組成員表，記錄使用者在群組中的身分與狀態。
CREATE TABLE IF NOT EXISTS group_members (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id UUID NOT NULL REFERENCES chat_groups (id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role group_role NOT NULL DEFAULT 'MEMBER',
    status group_member_status NOT NULL DEFAULT 'ACTIVE',
    invited_by UUID REFERENCES users (id) ON DELETE SET NULL,
    joined_at TIMESTAMPTZ,
    left_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (group_id, user_id)
);

COMMENT ON TABLE group_members IS '群組成員表，記錄使用者在群組中的角色、邀請來源、加入狀態與離開時間。';

CREATE INDEX IF NOT EXISTS ix_group_members_group_id ON group_members (group_id);
CREATE INDEX IF NOT EXISTS ix_group_members_user_id ON group_members (user_id);
CREATE INDEX IF NOT EXISTS ix_group_members_status ON group_members (status);

-- conversations: 聊天室主檔，私聊與群組聊天共用的聊天容器。
CREATE TABLE IF NOT EXISTS conversations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type conversation_type NOT NULL,
    group_id UUID UNIQUE REFERENCES chat_groups (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT conversations_group_required
        CHECK ((type = 'GROUP' AND group_id IS NOT NULL) OR (type = 'DIRECT' AND group_id IS NULL))
);

COMMENT ON TABLE conversations IS '聊天室主檔，作為私聊與群組聊天共用的聊天容器，訊息會掛在此表底下。';

CREATE INDEX IF NOT EXISTS ix_conversations_type ON conversations (type);

-- conversation_participants: 聊天室參與者表，記錄聊天室成員與個人閱讀設定。
CREATE TABLE IF NOT EXISTS conversation_participants (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    joined_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_read_message_id UUID,
    muted_until TIMESTAMPTZ,
    UNIQUE (conversation_id, user_id)
);

COMMENT ON TABLE conversation_participants IS '聊天室參與者表，記錄每個聊天室有哪些使用者，以及各自的已讀進度與靜音設定。';

CREATE INDEX IF NOT EXISTS ix_conversation_participants_user_id
    ON conversation_participants (user_id);

-- direct_conversations: 一對一私聊對照表，記錄私聊聊天室中的兩位使用者。
CREATE TABLE IF NOT EXISTS direct_conversations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL UNIQUE REFERENCES conversations (id) ON DELETE CASCADE,
    user_one_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    user_two_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT direct_conversations_two_users
        CHECK (user_one_id <> user_two_id)
);

COMMENT ON TABLE direct_conversations IS '一對一私聊對照表，記錄私聊聊天室對應的兩位使用者，避免同一組使用者重複開房。';

CREATE UNIQUE INDEX IF NOT EXISTS ux_direct_conversations_pair
    ON direct_conversations (LEAST(user_one_id, user_two_id), GREATEST(user_one_id, user_two_id));

-- messages: 訊息主檔，存放私聊與群組聊天的實際訊息。
CREATE TABLE IF NOT EXISTS messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    sender_id UUID REFERENCES users (id) ON DELETE SET NULL,
    type message_type NOT NULL DEFAULT 'TEXT',
    content TEXT NOT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    reply_to_message_id UUID REFERENCES messages (id) ON DELETE SET NULL,
    sent_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    edited_at TIMESTAMPTZ,
    deleted_at TIMESTAMPTZ
);

COMMENT ON TABLE messages IS '訊息主檔，存放私聊與群組聊天的訊息內容、類型、附件 metadata、回覆關係與編輯/刪除時間。';

CREATE INDEX IF NOT EXISTS ix_messages_conversation_sent
    ON messages (conversation_id, sent_at DESC);

CREATE INDEX IF NOT EXISTS ix_messages_sender_id ON messages (sender_id);
CREATE INDEX IF NOT EXISTS ix_messages_metadata ON messages USING GIN (metadata);


-- password_reset_tokens: 忘記密碼用的一次性 token
CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    requested_ip INET,
    user_agent TEXT,
    CONSTRAINT password_reset_tokens_valid_expiry
        CHECK (expires_at > created_at)
);

CREATE INDEX IF NOT EXISTS ix_password_reset_tokens_user_id
    ON password_reset_tokens (user_id);

CREATE INDEX IF NOT EXISTS ix_password_reset_tokens_expires_at
    ON password_reset_tokens (expires_at);

CREATE INDEX IF NOT EXISTS ix_password_reset_tokens_active
    ON password_reset_tokens (user_id, expires_at)
    WHERE used_at IS NULL;

-- email_delivery_logs: 寄信紀錄，例如忘記密碼、驗證信、通知信
CREATE TABLE IF NOT EXISTS email_delivery_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users (id) ON DELETE SET NULL,
    email VARCHAR(255) NOT NULL,
    template_key VARCHAR(80) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    provider_message_id VARCHAR(255),
    error_message TEXT,
    sent_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT email_delivery_logs_status_check
        CHECK (status IN ('PENDING', 'SENT', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS ix_email_delivery_logs_user_id
    ON email_delivery_logs (user_id);

CREATE INDEX IF NOT EXISTS ix_email_delivery_logs_email
    ON email_delivery_logs (LOWER(email));

CREATE INDEX IF NOT EXISTS ix_email_delivery_logs_status
    ON email_delivery_logs (status);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_conversation_participants_last_read_message'
    ) THEN
        ALTER TABLE conversation_participants
            ADD CONSTRAINT fk_conversation_participants_last_read_message
            FOREIGN KEY (last_read_message_id) REFERENCES messages (id) ON DELETE SET NULL;
    END IF;
END $$;

-- message_reads: 訊息已讀紀錄，記錄每位使用者讀過哪些訊息。
CREATE TABLE IF NOT EXISTS message_reads (
    message_id UUID NOT NULL REFERENCES messages (id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    read_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (message_id, user_id)
);

COMMENT ON TABLE message_reads IS '訊息已讀紀錄，記錄每位使用者讀過哪些訊息，可用於私聊已讀與群組已讀名單。';

CREATE INDEX IF NOT EXISTS ix_message_reads_user_id ON message_reads (user_id);
