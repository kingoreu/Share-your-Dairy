USE dairy;

-- =========================================
-- 1) 필수 트리거
--      주로 데이터 정합성에 어긋날 경우를 고려하여 작성.
-- =========================================

-- 1-1. 자기 자신을 친구로 추가 금지
DROP TRIGGER IF EXISTS trg_friendship_no_self;
DELIMITER //
CREATE TRIGGER trg_set_updated_at_on_update
	BEFORE UPDATE ON diary_entries
    FOR EACH ROW
BEGIN
    SET NEW.diary_updated_at = NOW();
END;
//
DELIMITER ;

-- 1-2. 다이어리 수정 시 diary_updated_at 자동 변경
DROP TRIGGER IF EXISTS trg_set_updated_at_on_update;
DELIMITER //
CREATE TRIGGER trg_set_updated_at_on_update
    BEFORE UPDATE ON diary_entries
    FOR EACH ROW
BEGIN
    SET NEW.diary_updated_at = NOW();
END;

DELIMITER ;
