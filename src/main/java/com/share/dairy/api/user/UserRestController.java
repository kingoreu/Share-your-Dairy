package com.share.dairy.api.user;

import com.share.dairy.dto.user.UserCreateRequest;
import com.share.dairy.dto.user.UserResponse;
import com.share.dairy.dto.user.UserUpdateRequest;
import com.share.dairy.service.user.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.sql.SQLException;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserRestController {

    private final UserService userService;
    public UserRestController(UserService userService) {
        this.userService = userService;
    }

    // 단건 조회
    @GetMapping("/{userId}")
    public ResponseEntity<UserResponse> getUser(@PathVariable long userId) throws SQLException {
        return userService.findById(userId)
                .map(UserService::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // 회원가입
    @PostMapping
    public ResponseEntity<Map<String, Object>> createUser(@RequestBody UserCreateRequest req) throws SQLException {
        long id = userService.createUser(req);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("id", id, "message", "등록 성공"));
    }

    // 정보 수정
    @PutMapping("/{userId}")
    public ResponseEntity<Map<String, Object>> updateUser(
            @PathVariable long userId,
            @RequestBody UserUpdateRequest req) throws SQLException {
        userService.updateNicknameAndEmail(userId, req.getNickname(), req.getUserEmail());
        return ResponseEntity.ok(Map.of("message", "수정 성공"));
    }

    // 회원 탈퇴
    @DeleteMapping("/{userId}")
    public ResponseEntity<Map<String, String>> deleteUser(@PathVariable long userId) throws SQLException {
        userService.deleteUser(userId);
        return ResponseEntity.ok(Map.of("message", "탈퇴 완료"));
    }

}
