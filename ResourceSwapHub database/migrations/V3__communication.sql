-- V3: direct conversations and persistent messages.
-- The participant join table and conversation type keep the model ready for groups.

CREATE TABLE conversations (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type            VARCHAR(20) NOT NULL DEFAULT 'DIRECT',
    listing_id      UUID REFERENCES listings (id) ON DELETE SET NULL,
    direct_key      VARCHAR(120),
    last_message_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_conversations_type CHECK (type IN ('DIRECT')),
    CONSTRAINT ck_conversations_direct_listing CHECK (listing_id IS NOT NULL),
    CONSTRAINT ck_conversations_direct_key CHECK (direct_key IS NOT NULL)
);

CREATE UNIQUE INDEX uq_conversations_direct_key ON conversations (direct_key);
CREATE INDEX idx_conversations_last_message ON conversations (last_message_at DESC);

CREATE TABLE messages (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    conversation_id UUID NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    sender_id       UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    body            VARCHAR(4000) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_messages_body CHECK (btrim(body) <> '' AND char_length(body) <= 4000)
);

CREATE INDEX idx_messages_conversation_id_desc ON messages (conversation_id, id DESC);

CREATE TABLE conversation_participants (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id      UUID NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    user_id              UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role                 VARCHAR(20) NOT NULL,
    joined_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_read_message_id BIGINT,
    CONSTRAINT uq_conversation_participants UNIQUE (conversation_id, user_id),
    CONSTRAINT ck_conversation_participants_role CHECK (role IN ('OWNER', 'INTERESTED'))
);

CREATE INDEX idx_conversation_participants_user
    ON conversation_participants (user_id, conversation_id);

ALTER TABLE conversation_participants
    ADD CONSTRAINT fk_participants_last_read
    FOREIGN KEY (last_read_message_id) REFERENCES messages (id) ON DELETE SET NULL;

CREATE OR REPLACE FUNCTION touch_conversation_last_message()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    UPDATE conversations
       SET last_message_at = NEW.created_at
     WHERE id = NEW.conversation_id;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_messages_touch_conversation
    AFTER INSERT ON messages
    FOR EACH ROW EXECUTE FUNCTION touch_conversation_last_message();
