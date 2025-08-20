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
    user_email      VARCHAR(255) NOT NULL UNIQUE,
    character_type  VARCHAR(20)  NOT NULL,
    user_created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    user_updated_at DATETIME     NULL,
    KEY idx_users_nickname (nickname)

) ENGINE = InnoDB;

-- !!이메일에 UNIQUE 제약 조건 추가함!!
ALTER TABLE users
ADD CONSTRAINT uq_users_email UNIQUE (user_email);


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

-- !! 일기 내용 테이블에 title 컬럼 추가 !! --
ALTER TABLE diary_entries
    ADD COLUMN title VARCHAR(255) AFTER entry_date;


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

# -- 9) 일기 내용 분석 키워드
# CREATE TABLE keywords
# (
#     keyword_id   BIGINT PRIMARY KEY AUTO_INCREMENT,
#     keyword_name VARCHAR(100) NOT NULL UNIQUE
# ) ENGINE = InnoDB;
#
# -- 10) 키워드-이미지 매핑
# CREATE TABLE keyword_images
# (
#     mapping_id    BIGINT PRIMARY KEY AUTO_INCREMENT,
#     keyword_id    BIGINT       NOT NULL,
#     image_name    VARCHAR(255) NOT NULL,
#     display_order SMALLINT     NULL,
#     created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
#     CONSTRAINT fk_ki_keyword
#         FOREIGN KEY (keyword_id) REFERENCES keywords (keyword_id)
#             ON DELETE CASCADE
#             ON UPDATE CASCADE,
#     KEY idx_ki_keyword (keyword_id, display_order)
# ) ENGINE = InnoDB;

-- 11) 키워드 이미지
-- 일기 관련 이미지. 책장 클릭하면 나오는거
CREATE TABLE keyword_images (
    keyword_image           BIGINT  PRIMARY KEY     AUTO_INCREMENT,
    analysis_id             BIGINT        NOT NULL,
    user_id                 BIGINT      NOT NULL,
    created_at              DATETIME,
    CONSTRAINT fk_keyword_id
        FOREIGN KEY (analysis_id) REFERENCES diary_analysis(analysis_id)
            ON DELETE CASCADE
            ON UPDATE CASCADE,
    CONSTRAINT fk_keyword_user
        FOREIGN KEY (user_id) REFERENCES users(user_id)
            ON DELETE CASCADE
            ON UPDATE CASCADE
) ENGINE = InnoDB;

-- 12) 캐릭터 키워드 이미지
-- 캐릭터 관련 이미지. 메인 화면에서 바뀌는 모습, 캘린더 모습
CREATE TABLE character_keyword_images (
    keyword_image           BIGINT    PRIMARY KEY   AUTO_INCREMENT,
    analysis_id             BIGINT        NOT NULL,
    user_id                 BIGINT      NOT NULL,
    created_at              DATETIME,
    CONSTRAINT fk_character_keyword_id
        FOREIGN KEY (analysis_id) REFERENCES diary_analysis(analysis_id)
            ON DELETE CASCADE
            ON UPDATE CASCADE,
    CONSTRAINT fk_character_keyword_user
        FOREIGN KEY (user_id) REFERENCES users(user_id)
            ON DELETE CASCADE
            ON UPDATE CASCADE
) ENGINE = InnoDB;