package com.share.dairy.service.user;

import com.share.dairy.dao.user.UserDao;
import com.share.dairy.dto.user.UserCreateRequest;
import com.share.dairy.dto.user.UserResponse;
import com.share.dairy.dto.user.UserUpdateRequest;
import com.share.dairy.model.user.User;
import com.share.dairy.util.DBConnection;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.util.Optional;

// 회원 정보 수정 후 업데이트 로직 필요
// 컨트롤러에 PutMapping 은 해 둠
@Service
public class UserService {

    private final UserDao userDao;

    public UserService(UserDao userDao) {
        this.userDao = userDao;
    }

    public Optional<User> findById(long userId) throws SQLException {
        return userDao.findById(userId);
    }

    public long createUser(UserCreateRequest req) throws SQLException {
        User u = new User();
        u.setNickname(req.getNickname());
        u.setLoginId(req.getLoginId());
        u.setPassword(req.getPassword()); // 실제 서비스라면 해싱 필요
        u.setUserEmail(req.getUserEmail());
        u.setCharacterType(req.getCharacterType());
        return userDao.insert(u);
    }

    public void updateNicknameAndEmail(long userId, String nickname, String email) throws SQLException {
        try (var con = com.share.dairy.util.DBConnection.getConnection()) {
            userDao.updateNicknameAndEmail(con, userId, nickname, email);
        }
    }

    public void deleteUser(long userId) throws SQLException {
        try (var con = DBConnection.getConnection()) {
            // userDao.delete(userId) 같은 메서드 호출
            // 직접 추가해야 함
        }
    }

    // 모델 → 응답 DTO 변환
    public static UserResponse toResponse(User u) {
        UserResponse res = new UserResponse();
        res.setUserId(u.getUserId());
        res.setNickname(u.getNickname());
        res.setUserEmail(u.getUserEmail());
        res.setCharacterType(u.getCharacterType());
        res.setUserCreatedAt(u.getUserCreatedAt());
        res.setUserUpdatedAt(u.getUserUpdatedAt());
        return res;
    }
}
