ALTER TABLE file_record ADD COLUMN thumbnail_object_key VARCHAR(500) NULL;
ALTER TABLE file_record ADD COLUMN thumbnail_size BIGINT NOT NULL DEFAULT 0;
ALTER TABLE file_record ADD COLUMN image_width INT NULL;
ALTER TABLE file_record ADD COLUMN image_height INT NULL;

CREATE INDEX idx_file_record_owner_created ON file_record(owner_id, created_at);

