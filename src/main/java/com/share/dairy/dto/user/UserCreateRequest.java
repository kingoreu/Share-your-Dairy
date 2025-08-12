package com.share.dairy.dto.user;

import lombok.Data;

// 회원가입 시 사용자 추가
@Data
public class UserCreateRequest {
    private String nickname;
    private String loginId;
    private String password;
    private String userEmail;
    private String characterType;
}
