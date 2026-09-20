ALTER TABLE user_account ADD COLUMN account_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE user_account ADD COLUMN muted_until TIMESTAMP NULL;
ALTER TABLE user_account ADD COLUMN violation_count INT NOT NULL DEFAULT 0;

CREATE TABLE content_report (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    reporter_id BIGINT NOT NULL,
    reported_user_id BIGINT NOT NULL,
    target_type VARCHAR(20) NOT NULL,
    target_id BIGINT NOT NULL,
    reason_category VARCHAR(30) NOT NULL,
    description VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    reviewed_by BIGINT,
    resolution_action VARCHAR(40),
    admin_note VARCHAR(500),
    target_snapshot TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at TIMESTAMP NULL,
    CONSTRAINT fk_report_reporter FOREIGN KEY (reporter_id) REFERENCES user_account(id),
    CONSTRAINT fk_report_reported_user FOREIGN KEY (reported_user_id) REFERENCES user_account(id),
    CONSTRAINT fk_report_reviewer FOREIGN KEY (reviewed_by) REFERENCES user_account(id),
    CONSTRAINT uk_reporter_target UNIQUE (reporter_id, target_type, target_id),
    INDEX idx_report_status_created (status, created_at),
    INDEX idx_report_reported_user (reported_user_id)
);
