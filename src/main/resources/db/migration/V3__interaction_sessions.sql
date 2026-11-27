CREATE TABLE interaction_session (
    id VARCHAR(32) PRIMARY KEY,
    household_id BIGINT NOT NULL REFERENCES household(id),
    telegram_user_id BIGINT,
    chat_id BIGINT NOT NULL,
    kind VARCHAR(24) NOT NULL,
    state VARCHAR(24) NOT NULL,
    payload_json TEXT NOT NULL,
    prompt_message_id INTEGER,
    prompt_expires_at TIMESTAMP,
    menu_message_id INTEGER,
    version INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    closed_at TIMESTAMP
);
CREATE INDEX interaction_session_owner ON interaction_session(household_id, telegram_user_id, kind, closed_at);
CREATE INDEX interaction_session_prompt ON interaction_session(chat_id, telegram_user_id, prompt_message_id);
CREATE INDEX interaction_session_expiry ON interaction_session(expires_at);
