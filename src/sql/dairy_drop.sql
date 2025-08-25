USE dairy;

-- 트리거 삭제
DROP TRIGGER IF EXISTS trg_friendship_no_self;
DROP TRIGGER IF EXISTS trg_set_updated_at_on_update;

-- 테이블 삭제 (FK 의존성 때문에 역순으로)
DROP TABLE IF EXISTS keyword_images;
DROP TABLE IF EXISTS character_keyword_images;
DROP TABLE IF EXISTS diary_comments;
DROP TABLE IF EXISTS diary_attachments;
DROP TABLE IF EXISTS diary_analysis;
DROP TABLE IF EXISTS diary_entries;
DROP TABLE IF EXISTS friendship;
DROP TABLE IF EXISTS shared_diary_members;
DROP TABLE IF EXISTS shared_diaries;
DROP TABLE IF EXISTS users;
