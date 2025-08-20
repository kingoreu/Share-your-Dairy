CREATE DATABASE IF NOT EXISTS dairy
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_general_ci;

USE dairy;

-- 1) 사용자
CREATE TABLE users
(
    user_id         BIGINT PRIMARY KEY AUTO_INCREMENT,
    nickname        VARCHAR(50)  NOT NULL,
    login_id        VARCHAR(50)  NOT NULL UNIQUE,
    password        VARCHAR(255) NOT NULL,
    user_email      VARCHAR(255) NOT NULL,
    character_type  VARCHAR(20)  NOT NULL,
    user_created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    user_updated_at DATETIME     NULL,
    KEY idx_users_nickname (nickname)

) ENGINE = InnoDB;

-- 2) 공유 일기장
CREATE TABLE shared_diaries
(
    shared_diary_id    BIGINT PRIMARY KEY AUTO_INCREMENT,
    shared_diary_title VARCHAR(200) NOT NULL,
    owner_id           BIGINT       NOT NULL,
    created_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME     NULL,
    CONSTRAINT fk_shared_diaries_owner
        FOREIGN KEY (owner_id) REFERENCES users (user_id)
            ON DELETE CASCADE
            ON UPDATE CASCADE
) ENGINE = InnoDB;

-- 3) 공유 일기 멤버
CREATE TABLE shared_diary_members
(
    shared_diary_id BIGINT   NOT NULL,
    user_id         BIGINT   NOT NULL,
    owner_id        BIGINT   NOT NULL,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (shared_diary_id, user_id),
    CONSTRAINT fk_sdm_diary
        FOREIGN KEY (shared_diary_id) REFERENCES shared_diaries (shared_diary_id)
            ON DELETE CASCADE
            ON UPDATE CASCADE,
    CONSTRAINT fk_sdm_user
        FOREIGN KEY (user_id) REFERENCES users (user_id)
            ON DELETE CASCADE
            ON UPDATE CASCADE,
    CONSTRAINT fk_sdm_owner
        FOREIGN KEY (owner_id) REFERENCES users (user_id)
            ON DELETE CASCADE
            ON UPDATE CASCADE
) ENGINE = InnoDB;

-- 4) 친구 관계
CREATE TABLE friendship
(
    user_id           BIGINT                                 NOT NULL,
    friend_id         BIGINT                                 NOT NULL,
    friendship_status ENUM ('PENDING','ACCEPTED','REJECTED') NOT NULL,
    requested_at      DATETIME                               NOT NULL DEFAULT CURRENT_TIMESTAMP,
    responded_at      DATETIME                               NULL,
    PRIMARY KEY (user_id, friend_id),
    CONSTRAINT fk_friend_user
        FOREIGN KEY (user_id) REFERENCES users (user_id)
            ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_friend_friend
        FOREIGN KEY (friend_id) REFERENCES users (user_id)
            ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE = InnoDB;

-- 5) 일기 본문
CREATE TABLE diary_entries
(
    entry_id         BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id          BIGINT                              NOT NULL,
    shared_diary_id  BIGINT                              NULL,
    entry_date       DATE                                NOT NULL,
    diary_content    TEXT                                NOT NULL,
    visibility       ENUM ('PRIVATE','FRIENDS','PUBLIC') NOT NULL,
    diary_created_at DATETIME                            NOT NULL DEFAULT CURRENT_TIMESTAMP,
    diary_updated_at DATETIME                            NULL,
    CONSTRAINT fk_diary_user
        FOREIGN KEY (user_id) REFERENCES users (user_id)
            ON DELETE CASCADE
            ON UPDATE CASCADE,
    CONSTRAINT fk_diary_shared
        FOREIGN KEY (shared_diary_id) REFERENCES shared_diaries (shared_diary_id)
            ON DELETE SET NULL
            ON UPDATE CASCADE,
    KEY idx_diary_user_date (user_id, entry_date),
    KEY idx_diary_visibility (visibility)
) ENGINE = InnoDB;

-- 6) 일기 분석
CREATE TABLE diary_analysis
(
    analysis_id       BIGINT PRIMARY KEY AUTO_INCREMENT,
    entry_id          BIGINT   NOT NULL UNIQUE,
    summary           TEXT     NULL,
    happiness_score   TINYINT  NULL,
    analysis_keywords TEXT     NULL,
    analyzed_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_analysis_entry
        FOREIGN KEY (entry_id) REFERENCES diary_entries (entry_id)
            ON DELETE CASCADE
            ON UPDATE CASCADE
) ENGINE = InnoDB;

-- 7) 첨부파일
CREATE TABLE diary_attachments
(
    attachment_id         BIGINT PRIMARY KEY AUTO_INCREMENT,
    entry_id              BIGINT                                NOT NULL,
    attachment_type       ENUM ('IMAGE','VIDEO','AUDIO','FILE') NULL,
    path_or_url           VARCHAR(255)                          NULL,
    display_order         SMALLINT                              NULL,
    attachment_created_at DATETIME                              NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_attach_entry
        FOREIGN KEY (entry_id) REFERENCES diary_entries (entry_id)
            ON DELETE CASCADE
            ON UPDATE CASCADE,
    KEY idx_attach_entry (entry_id, display_order)
) ENGINE = InnoDB;

-- 8) 공유일기 다이어리 댓글
CREATE TABLE diary_comments
(
    comment_id      BIGINT PRIMARY KEY AUTO_INCREMENT,
    entry_id        BIGINT     NOT NULL,
    user_id         BIGINT     NOT NULL,
    parent_id       BIGINT     NULL,
    comment_content TEXT       NOT NULL,
    is_deleted      TINYINT(1) NOT NULL DEFAULT 0,
    created_at      DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME   NULL,
    CONSTRAINT fk_comment_entry
        FOREIGN KEY (entry_id) REFERENCES diary_entries (entry_id)
            ON DELETE CASCADE
            ON UPDATE CASCADE,
    CONSTRAINT fk_comment_user
        FOREIGN KEY (user_id) REFERENCES users (user_id)
            ON DELETE CASCADE
            ON UPDATE CASCADE,
    CONSTRAINT fk_comment_parent
        FOREIGN KEY (parent_id) REFERENCES diary_comments (comment_id)
            ON DELETE SET NULL
            ON UPDATE CASCADE,
    KEY idx_comment_entry (entry_id, created_at)
) ENGINE = InnoDB;

-- 2.1.6 키워드 이미지 (keyword_images)
CREATE TABLE keyword_images (
                                keyword_image BIGINT PRIMARY KEY AUTO_INCREMENT, -- PK
                                keywords      TEXT        NOT NULL,              -- 키워드 원문
                                analyzed_id   BIGINT      NOT NULL,              -- 분석행 ID(FK 권장)
                                user_id       BIGINT      NOT NULL,              -- 사용자 ID(FK)
                                created_at    DATETIME    NULL DEFAULT CURRENT_TIMESTAMP,

                                CONSTRAINT fk_kimg_analysis
                                    FOREIGN KEY (analyzed_id) REFERENCES diary_analysis (analysis_id)
                                        ON DELETE CASCADE ON UPDATE CASCADE,
                                CONSTRAINT fk_kimg_user
                                    FOREIGN KEY (user_id)       REFERENCES users (user_id)
                                        ON DELETE CASCADE ON UPDATE CASCADE,

                                KEY idx_kimg_user_created (user_id, created_at),
                                KEY idx_kimg_analysis      (analyzed_id)
) ENGINE=InnoDB;

-- 2.1.7 캐릭터 키워드 이미지 (character_keyword_images)
CREATE TABLE character_keyword_images (
                                          keyword_image BIGINT PRIMARY KEY AUTO_INCREMENT, -- PK
                                          keywords      TEXT        NOT NULL,              -- 키워드 원문
                                          analyzed_id   BIGINT      NOT NULL,              -- 분석행 ID(FK 권장)
                                          user_id       BIGINT      NOT NULL,              -- 사용자 ID(FK)
                                          created_at    DATETIME    NULL DEFAULT CURRENT_TIMESTAMP,

                                          CONSTRAINT fk_ckimg_analysis
                                              FOREIGN KEY (analyzed_id) REFERENCES diary_analysis (analysis_id)
                                                  ON DELETE CASCADE ON UPDATE CASCADE,
                                          CONSTRAINT fk_ckimg_user
                                              FOREIGN KEY (user_id)       REFERENCES users (user_id)
                                                  ON DELETE CASCADE ON UPDATE CASCADE,

                                          KEY idx_ckimg_user_created (user_id, created_at),
                                          KEY idx_ckimg_analysis      (analyzed_id)
) ENGINE=InnoDB;
