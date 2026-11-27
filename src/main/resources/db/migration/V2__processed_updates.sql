CREATE TABLE processed_update (
    bot_identity VARCHAR(64) NOT NULL,
    update_id INTEGER NOT NULL,
    callback_id VARCHAR(128),
    created_at TIMESTAMP NOT NULL,
    PRIMARY KEY (bot_identity, update_id),
    UNIQUE (bot_identity, callback_id)
);
CREATE INDEX processed_update_created ON processed_update(created_at);
