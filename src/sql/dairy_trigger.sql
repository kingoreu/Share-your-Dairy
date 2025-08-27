USE dairy;

-- 1) 자기 자신을 친구로 추가 금지
DROP TRIGGER IF EXISTS dairy.trg_friendship_no_self;
DELIMITER //
CREATE TRIGGER dairy.trg_friendship_no_self
BEFORE INSERT ON `friendship`
FOR EACH ROW
BEGIN
    IF NEW.user_id = NEW.friend_id THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '자기 자신을 친구로 추가할 수 없습니다.';
    END IF;
END//
DELIMITER ;

-- 2) 다이어리 수정 시 diary_updated_at 자동 갱신
DROP TRIGGER IF EXISTS dairy.trg_set_updated_at_on_update;
DELIMITER //
CREATE TRIGGER dairy.trg_set_updated_at_on_update
BEFORE UPDATE ON diary_entries
FOR EACH ROW
BEGIN
    SET NEW.diary_updated_at = NOW();
END//
DELIMITER ;
