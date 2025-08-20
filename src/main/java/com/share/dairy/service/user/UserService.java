package com.share.dairy.service.user;

import com.share.dairy.dao.user.UserDao;
import com.share.dairy.dto.user.UserCreateRequest;
import com.share.dairy.dto.user.UserResponse;
import com.share.dairy.dto.user.UserUpdateRequest;
import com.share.dairy.model.user.User;
import com.share.dairy.util.DBConnection;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.util.Optional;

// 회원 정보 수정 후 업데이트 로직 필요
// 컨트롤러에 PutMapping 은 해 둠
@Service
public class UserService {

    private final UserDao userDao;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserDao userDao, PasswordEncoder passwordEncoder) {
        this.userDao = userDao;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional (readOnly = true)
    public Optional<User> findById(long userId) throws SQLException {
        return userDao.findById(userId);
    }

    @Transactional (rollbackFor =  Exception.class)
    public long createUser(UserCreateRequest req) throws SQLException {
        // 이메일 소문자로 통일
        String normalizedEmail = req.getUserEmail() == null ? null
                : req.getUserEmail().trim().toLowerCase();

        // 중복 체크 (유효성)o
        if (existsByLoginId(req.getLoginId())) {
            throw new DuplicateKeyException("이미 사용 중인 아이디입니다.");
        }
        if (normalizedEmail != null && existsByEmail(normalizedEmail)) {
            throw new DuplicateKeyException("이미 가입된 이메일입니다.");
        }

        User u = new User();
        u.setNickname(req.getNickname());
        u.setLoginId(req.getLoginId());
        u.setPassword(passwordEncoder.encode(req.getPassword()));
        u.setUserEmail(normalizedEmail);
        u.setCharacterType(req.getCharacterType());

        return userDao.insert(u);
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateNicknameAndEmail(long userId, String nickname, String email) throws SQLException {
        String normalized = email == null ? null : email.trim().toLowerCase();
        if (userDao.existsOtherByEmail(userId, normalized)) {
            throw new DuplicateKeyException("user_email duplicate");
        }
        int updated = userDao.updateNicknameAndEmail(userId, nickname, normalized);
        if (updated == 0) throw new IllegalArgumentException("존재하지 않는 사용자입니다."); // 전역핸들러 → 400/404 선택
    }


    @Transactional (rollbackFor =  Exception.class)
    public void deleteUser(long userId) throws SQLException {
        userDao.deleteById(userId);
    }

    @Transactional(readOnly = true)
    public User authenticate(String loginId, String rawPassword) throws SQLException {
        var opt = userDao.findByLoginId(loginId);
        if (opt.isEmpty() || !passwordEncoder.matches(rawPassword, opt.get().getPassword())) {
            throw new IllegalArgumentException("아이디 또는 비밀번호가 올바르지 않습니다.");
        }
        return opt.get();
    }

    // dao에 있긴 한데, 레이어 아키텍처 원칙 지켜야해서 한번 더 감쌈
    @Transactional(readOnly = true)
    public boolean existsByLoginId(String loginId) throws SQLException {
        return userDao.existsByLoginId(loginId);
    }

    @Transactional(readOnly = true)
    public boolean existsByEmail(String email) throws SQLException {
        return userDao.existsByEmail(email);
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
