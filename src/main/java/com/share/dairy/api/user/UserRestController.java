package com.share.dairy.api.user;

import com.share.dairy.dto.user.UserCreateRequest;
import com.share.dairy.dto.user.UserResponse;
import com.share.dairy.dto.user.UserUpdateRequest;
import com.share.dairy.service.user.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.sql.SQLException;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@Validated
public class UserRestController {

    private final UserService userService;
    public UserRestController(UserService userService) {
        this.userService = userService;
    }

    // 단건 조회
    @GetMapping("/{userId}")
    public ResponseEntity<UserResponse> getUser(@PathVariable @Min(1) long userId) throws SQLException {
        return userService.findById(userId)
                .map(UserService::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // 회원가입
    @PostMapping
    public ResponseEntity<Map<String, Object>> createUser(@Valid @RequestBody UserCreateRequest req) throws SQLException {
        long id = userService.createUser(req);
        return ResponseEntity
                .created(URI.create("/api/users/" + id))
                .body(Map.of("userId", id));
    }

    // 정보 수정
    @PutMapping("/{userId}")
    public ResponseEntity<Map<String, Object>> updateUser(
            @PathVariable @Min(1) long userId,
            @Valid @RequestBody UserUpdateRequest req) throws SQLException {
        userService.updateNicknameAndEmail(userId, req.getNickname(), req.getUserEmail());
        return ResponseEntity.noContent().build();  // 204
    }

    // 회원 탈퇴
    @DeleteMapping("/{userId}")
    public ResponseEntity<Map<String, String>> deleteUser(@PathVariable @Min(1) long userId) throws SQLException {
        userService.deleteUser(userId);
        return ResponseEntity.noContent().build();
    }

    // 로그인
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> body) throws SQLException {
        String loginId = body.getOrDefault("loginId", "").trim();
        String password = body.getOrDefault("password", "");
        if (loginId.isEmpty() || password.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "loginId/password 필수"));
        }
        var user = userService.authenticate(loginId, password); // ← 서비스에 아래 메서드 추가(있으면 그대로 사용)
        return ResponseEntity.ok(Map.of(
                "userId", user.getUserId(),
                "nickname", user.getNickname(),
                "userEmail", user.getUserEmail(),
                "characterType", user.getCharacterType()
        ));
    }

}