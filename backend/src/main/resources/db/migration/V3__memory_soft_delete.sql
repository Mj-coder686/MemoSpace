ALTER TABLE memory
    ADD COLUMN deleted_at TIMESTAMP NULL;

CREATE INDEX idx_memory_deleted_creator
    ON memory(deleted_at, creator_id, occurred_at);
