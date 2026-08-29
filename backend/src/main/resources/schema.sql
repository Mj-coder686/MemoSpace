CREATE TABLE IF NOT EXISTS user_account (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    public_id CHAR(12) UNIQUE,
    username VARCHAR(40) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    nickname VARCHAR(60) NOT NULL,
    avatar VARCHAR(500),
    bio VARCHAR(300),
    gender VARCHAR(20),
    birthday DATE,
    location VARCHAR(120),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS user_follow (
    follower_id BIGINT NOT NULL,
    following_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (follower_id, following_id),
    CONSTRAINT fk_follow_follower FOREIGN KEY (follower_id) REFERENCES user_account(id),
    CONSTRAINT fk_follow_following FOREIGN KEY (following_id) REFERENCES user_account(id)
);

CREATE TABLE IF NOT EXISTS friend_request (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    sender_id BIGINT NOT NULL,
    receiver_id BIGINT NOT NULL,
    message VARCHAR(200),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    responded_at TIMESTAMP,
    CONSTRAINT fk_friend_request_sender FOREIGN KEY (sender_id) REFERENCES user_account(id),
    CONSTRAINT fk_friend_request_receiver FOREIGN KEY (receiver_id) REFERENCES user_account(id),
    INDEX idx_friend_request_receiver_status (receiver_id, status),
    INDEX idx_friend_request_sender_status (sender_id, status)
);

CREATE TABLE IF NOT EXISTS friendship (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_low_id BIGINT NOT NULL,
    user_high_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ended_at TIMESTAMP,
    CONSTRAINT uk_friendship_pair UNIQUE (user_low_id, user_high_id),
    CONSTRAINT fk_friendship_low FOREIGN KEY (user_low_id) REFERENCES user_account(id),
    CONSTRAINT fk_friendship_high FOREIGN KEY (user_high_id) REFERENCES user_account(id),
    INDEX idx_friendship_low_status (user_low_id, status),
    INDEX idx_friendship_high_status (user_high_id, status)
);

CREATE TABLE IF NOT EXISTS friend_setting (
    owner_id BIGINT NOT NULL,
    friend_id BIGINT NOT NULL,
    remark_name VARCHAR(60),
    allow_direct_reminders BOOLEAN NOT NULL DEFAULT TRUE,
    mute_chat BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (owner_id, friend_id),
    CONSTRAINT fk_friend_setting_owner FOREIGN KEY (owner_id) REFERENCES user_account(id),
    CONSTRAINT fk_friend_setting_friend FOREIGN KEY (friend_id) REFERENCES user_account(id)
);

CREATE TABLE IF NOT EXISTS direct_message (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    friendship_id BIGINT NOT NULL,
    sender_id BIGINT NOT NULL,
    receiver_id BIGINT NOT NULL,
    client_message_id VARCHAR(80) NOT NULL,
    content VARCHAR(1000) NOT NULL,
    sent_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    delivered_at TIMESTAMP,
    read_at TIMESTAMP,
    CONSTRAINT uk_direct_message_client UNIQUE (sender_id, client_message_id),
    CONSTRAINT fk_direct_message_friendship FOREIGN KEY (friendship_id) REFERENCES friendship(id),
    CONSTRAINT fk_direct_message_sender FOREIGN KEY (sender_id) REFERENCES user_account(id),
    CONSTRAINT fk_direct_message_receiver FOREIGN KEY (receiver_id) REFERENCES user_account(id),
    INDEX idx_direct_message_conversation (friendship_id, id),
    INDEX idx_direct_message_receiver_read (receiver_id, read_at, id)
);

CREATE TABLE IF NOT EXISTS relationship_invitation (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    sender_id BIGINT NOT NULL,
    receiver_id BIGINT NOT NULL,
    relationship_type VARCHAR(20) NOT NULL,
    message VARCHAR(200),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    responded_at TIMESTAMP,
    CONSTRAINT fk_invitation_sender FOREIGN KEY (sender_id) REFERENCES user_account(id),
    CONSTRAINT fk_invitation_receiver FOREIGN KEY (receiver_id) REFERENCES user_account(id),
    INDEX idx_invitation_receiver_status (receiver_id, status)
);

CREATE TABLE IF NOT EXISTS relationships (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    relationship_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    established_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    archived_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS relationship_member (
    relationship_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    PRIMARY KEY (relationship_id, user_id),
    CONSTRAINT fk_relation_member_relation FOREIGN KEY (relationship_id) REFERENCES relationships(id),
    CONSTRAINT fk_relation_member_user FOREIGN KEY (user_id) REFERENCES user_account(id)
);

CREATE TABLE IF NOT EXISTS relationship_category (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    owner_id BIGINT NOT NULL,
    category_key VARCHAR(80) NOT NULL,
    name VARCHAR(40) NOT NULL,
    icon VARCHAR(32) NOT NULL DEFAULT 'users',
    category_type VARCHAR(20) NOT NULL DEFAULT 'SYSTEM',
    theme_id BIGINT,
    is_visible BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_relationship_category_owner_key UNIQUE (owner_id, category_key),
    CONSTRAINT fk_relationship_category_owner FOREIGN KEY (owner_id) REFERENCES user_account(id),
    INDEX idx_relationship_category_owner_order (owner_id, sort_order)
);

CREATE TABLE IF NOT EXISTS relationship_category_link (
    category_id BIGINT NOT NULL,
    relationship_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (category_id, relationship_id),
    CONSTRAINT fk_category_link_category FOREIGN KEY (category_id) REFERENCES relationship_category(id) ON DELETE CASCADE,
    CONSTRAINT fk_category_link_relationship FOREIGN KEY (relationship_id) REFERENCES relationships(id),
    INDEX idx_category_link_relationship (relationship_id)
);

CREATE TABLE IF NOT EXISTS relationship_invitation_category (
    invitation_id BIGINT PRIMARY KEY,
    sender_category_id BIGINT NOT NULL,
    category_key VARCHAR(80) NOT NULL,
    category_name VARCHAR(40) NOT NULL,
    category_icon VARCHAR(32) NOT NULL,
    theme_id BIGINT,
    CONSTRAINT fk_invitation_category_invitation FOREIGN KEY (invitation_id) REFERENCES relationship_invitation(id) ON DELETE CASCADE,
    CONSTRAINT fk_invitation_category_sender FOREIGN KEY (sender_category_id) REFERENCES relationship_category(id)
);

CREATE TABLE IF NOT EXISTS space_theme (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    preset_name VARCHAR(50) NOT NULL,
    primary_color VARCHAR(20) NOT NULL,
    secondary_color VARCHAR(20) NOT NULL,
    background_color VARCHAR(20) NOT NULL,
    surface_color VARCHAR(20) NOT NULL,
    text_color VARCHAR(20) NOT NULL,
    muted_color VARCHAR(20) NOT NULL,
    radius INT NOT NULL DEFAULT 24,
    card_opacity DECIMAL(3,2) NOT NULL DEFAULT 0.92
);

CREATE TABLE IF NOT EXISTS space (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    space_type VARCHAR(20) NOT NULL,
    name VARCHAR(80) NOT NULL,
    owner_id BIGINT,
    relationship_id BIGINT,
    visibility VARCHAR(20) NOT NULL DEFAULT 'PRIVATE',
    cover_url VARCHAR(500),
    theme_id BIGINT,
    custom_primary_color VARCHAR(20),
    custom_background_color VARCHAR(20),
    custom_text_color VARCHAR(20),
    background_file_id BIGINT,
    background_brightness INT NOT NULL DEFAULT 100,
    background_overlay INT NOT NULL DEFAULT 18,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    archived_at TIMESTAMP,
    CONSTRAINT fk_space_owner FOREIGN KEY (owner_id) REFERENCES user_account(id),
    CONSTRAINT fk_space_relationship FOREIGN KEY (relationship_id) REFERENCES relationships(id),
    CONSTRAINT fk_space_theme FOREIGN KEY (theme_id) REFERENCES space_theme(id)
);

CREATE TABLE IF NOT EXISTS space_member (
    space_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    member_role VARCHAR(20) NOT NULL DEFAULT 'MEMBER',
    joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (space_id, user_id),
    CONSTRAINT fk_space_member_space FOREIGN KEY (space_id) REFERENCES space(id),
    CONSTRAINT fk_space_member_user FOREIGN KEY (user_id) REFERENCES user_account(id)
);

CREATE TABLE IF NOT EXISTS memory (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    creator_id BIGINT NOT NULL,
    title VARCHAR(160) NOT NULL,
    content TEXT,
    memory_type VARCHAR(20) NOT NULL,
    occurred_at TIMESTAMP NOT NULL,
    location VARCHAR(160),
    latitude DECIMAL(10,7),
    longitude DECIMAL(10,7),
    visibility VARCHAR(20) NOT NULL DEFAULT 'PRIVATE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_memory_creator FOREIGN KEY (creator_id) REFERENCES user_account(id),
    INDEX idx_memory_creator_occurred (creator_id, occurred_at),
    INDEX idx_memory_visibility_created (visibility, created_at)
);

CREATE TABLE IF NOT EXISTS memory_space (
    memory_id BIGINT NOT NULL,
    space_id BIGINT NOT NULL,
    added_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (memory_id, space_id),
    CONSTRAINT fk_memory_space_memory FOREIGN KEY (memory_id) REFERENCES memory(id) ON DELETE CASCADE,
    CONSTRAINT fk_memory_space_space FOREIGN KEY (space_id) REFERENCES space(id),
    CONSTRAINT fk_memory_space_adder FOREIGN KEY (added_by) REFERENCES user_account(id),
    INDEX idx_memory_space_space (space_id, memory_id)
);

CREATE TABLE IF NOT EXISTS memory_custom_viewer (
    memory_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    PRIMARY KEY (memory_id, user_id),
    CONSTRAINT fk_custom_view_memory FOREIGN KEY (memory_id) REFERENCES memory(id) ON DELETE CASCADE,
    CONSTRAINT fk_custom_view_user FOREIGN KEY (user_id) REFERENCES user_account(id)
);

CREATE TABLE IF NOT EXISTS memory_media (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    memory_id BIGINT NOT NULL,
    media_type VARCHAR(20) NOT NULL,
    object_key VARCHAR(500) NOT NULL,
    thumbnail_key VARCHAR(500),
    width INT,
    height INT,
    duration INT,
    file_size BIGINT NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_media_memory FOREIGN KEY (memory_id) REFERENCES memory(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS `event` (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    creator_id BIGINT NOT NULL,
    space_id BIGINT NOT NULL,
    name VARCHAR(160) NOT NULL,
    description TEXT,
    start_at TIMESTAMP NOT NULL,
    end_at TIMESTAMP,
    location VARCHAR(160),
    cover_url VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_event_creator FOREIGN KEY (creator_id) REFERENCES user_account(id),
    CONSTRAINT fk_event_space FOREIGN KEY (space_id) REFERENCES space(id)
);

CREATE TABLE IF NOT EXISTS event_memory (
    event_id BIGINT NOT NULL,
    memory_id BIGINT NOT NULL,
    PRIMARY KEY (event_id, memory_id),
    CONSTRAINT fk_event_memory_event FOREIGN KEY (event_id) REFERENCES `event`(id) ON DELETE CASCADE,
    CONSTRAINT fk_event_memory_memory FOREIGN KEY (memory_id) REFERENCES memory(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS post (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    memory_id BIGINT NOT NULL UNIQUE,
    creator_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED',
    published_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_post_memory FOREIGN KEY (memory_id) REFERENCES memory(id) ON DELETE CASCADE,
    CONSTRAINT fk_post_creator FOREIGN KEY (creator_id) REFERENCES user_account(id)
);

CREATE TABLE IF NOT EXISTS comments (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    memory_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    content VARCHAR(600) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_comment_memory FOREIGN KEY (memory_id) REFERENCES memory(id) ON DELETE CASCADE,
    CONSTRAINT fk_comment_user FOREIGN KEY (user_id) REFERENCES user_account(id)
);

CREATE TABLE IF NOT EXISTS reaction (
    memory_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    reaction_type VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (memory_id, user_id),
    CONSTRAINT fk_reaction_memory FOREIGN KEY (memory_id) REFERENCES memory(id) ON DELETE CASCADE,
    CONSTRAINT fk_reaction_user FOREIGN KEY (user_id) REFERENCES user_account(id)
);

CREATE TABLE IF NOT EXISTS favorite (
    memory_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (memory_id, user_id),
    CONSTRAINT fk_favorite_memory FOREIGN KEY (memory_id) REFERENCES memory(id) ON DELETE CASCADE,
    CONSTRAINT fk_favorite_user FOREIGN KEY (user_id) REFERENCES user_account(id)
);

CREATE TABLE IF NOT EXISTS notification (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    actor_id BIGINT,
    notification_type VARCHAR(30) NOT NULL,
    title VARCHAR(120) NOT NULL,
    content VARCHAR(300),
    reference_id BIGINT,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_notification_user FOREIGN KEY (user_id) REFERENCES user_account(id),
    CONSTRAINT fk_notification_actor FOREIGN KEY (actor_id) REFERENCES user_account(id),
    INDEX idx_notification_user_read (user_id, is_read, created_at)
);

CREATE TABLE IF NOT EXISTS anniversary (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    space_id BIGINT NOT NULL,
    creator_id BIGINT NOT NULL,
    title VARCHAR(100) NOT NULL,
    anniversary_date DATE NOT NULL,
    repeat_yearly BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_anniversary_space FOREIGN KEY (space_id) REFERENCES space(id),
    CONSTRAINT fk_anniversary_creator FOREIGN KEY (creator_id) REFERENCES user_account(id)
);

CREATE TABLE IF NOT EXISTS file_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    owner_id BIGINT NOT NULL,
    object_key VARCHAR(500) NOT NULL UNIQUE,
    original_name VARCHAR(255) NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    is_private BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_file_owner FOREIGN KEY (owner_id) REFERENCES user_account(id)
);

CREATE TABLE IF NOT EXISTS user_appearance (
    user_id BIGINT PRIMARY KEY,
    background_color VARCHAR(20) NOT NULL DEFAULT '#f5f2ec',
    background_file_id BIGINT,
    background_brightness INT NOT NULL DEFAULT 100,
    background_overlay INT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_appearance_user FOREIGN KEY (user_id) REFERENCES user_account(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_appearance_file FOREIGN KEY (background_file_id) REFERENCES file_record(id)
);

CREATE TABLE IF NOT EXISTS reminder (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    creator_id BIGINT NOT NULL,
    related_user_id BIGINT,
    relationship_id BIGINT,
    image_file_id BIGINT,
    title VARCHAR(160) NOT NULL,
    note VARCHAR(1000),
    reminder_kind VARCHAR(30) NOT NULL DEFAULT 'CUSTOM',
    schedule_type VARCHAR(20) NOT NULL DEFAULT 'ONCE',
    remind_at TIMESTAMP NOT NULL,
    next_trigger_at TIMESTAMP,
    timezone VARCHAR(60) NOT NULL DEFAULT 'Asia/Shanghai',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_reminder_creator FOREIGN KEY (creator_id) REFERENCES user_account(id),
    CONSTRAINT fk_reminder_related_user FOREIGN KEY (related_user_id) REFERENCES user_account(id),
    CONSTRAINT fk_reminder_relationship FOREIGN KEY (relationship_id) REFERENCES relationships(id),
    CONSTRAINT fk_reminder_image FOREIGN KEY (image_file_id) REFERENCES file_record(id),
    INDEX idx_reminder_due (status, next_trigger_at)
);

CREATE TABLE IF NOT EXISTS reminder_participant (
    reminder_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    participant_role VARCHAR(20) NOT NULL DEFAULT 'RECIPIENT',
    acceptance_status VARCHAR(20) NOT NULL DEFAULT 'ACCEPTED',
    notifications_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    last_notified_at TIMESTAMP,
    completed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (reminder_id, user_id),
    CONSTRAINT fk_reminder_participant_reminder FOREIGN KEY (reminder_id) REFERENCES reminder(id) ON DELETE CASCADE,
    CONSTRAINT fk_reminder_participant_user FOREIGN KEY (user_id) REFERENCES user_account(id),
    INDEX idx_reminder_participant_user_status (user_id, acceptance_status)
);

CREATE TABLE IF NOT EXISTS reminder_delivery (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    reminder_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    scheduled_for TIMESTAMP NOT NULL,
    delivered_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    channel VARCHAR(20) NOT NULL DEFAULT 'IN_APP',
    CONSTRAINT uk_reminder_delivery UNIQUE (reminder_id, user_id, scheduled_for),
    CONSTRAINT fk_reminder_delivery_reminder FOREIGN KEY (reminder_id) REFERENCES reminder(id) ON DELETE CASCADE,
    CONSTRAINT fk_reminder_delivery_user FOREIGN KEY (user_id) REFERENCES user_account(id)
);

CREATE TABLE IF NOT EXISTS location_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    memory_id BIGINT NOT NULL,
    name VARCHAR(160),
    latitude DECIMAL(10,7) NOT NULL,
    longitude DECIMAL(10,7) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_location_memory FOREIGN KEY (memory_id) REFERENCES memory(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS user_block (
    blocker_id BIGINT NOT NULL,
    blocked_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (blocker_id, blocked_id),
    CONSTRAINT fk_blocker FOREIGN KEY (blocker_id) REFERENCES user_account(id),
    CONSTRAINT fk_blocked FOREIGN KEY (blocked_id) REFERENCES user_account(id)
);

CREATE TABLE IF NOT EXISTS space_message (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    space_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    content VARCHAR(500) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_space_message_space FOREIGN KEY (space_id) REFERENCES space(id),
    CONSTRAINT fk_space_message_user FOREIGN KEY (user_id) REFERENCES user_account(id)
);
