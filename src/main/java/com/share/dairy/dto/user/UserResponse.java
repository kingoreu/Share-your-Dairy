package com.share.dairy.dto.user;

import lombok.Data;

import java.time.LocalDateTime;

// 사용자 응답 관련(request 와 분리)
@Data
public class UserResponse {
    private Long userId;
    private String nickname;
    private String userEmail;
    private String characterType;
    private LocalDateTime userCreatedAt;
    private LocalDateTime userUpdatedAt;
}
