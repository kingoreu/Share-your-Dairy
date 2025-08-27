package com.share.dairy.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

// 회원가입 시 사용자 추가
@Data
public class UserCreateRequest {

    @NotBlank
    @Size(min = 2, max = 50)
    private String nickname;

    @NotBlank
    @Size(min = 2, max = 50)
    @Pattern(regexp = "^[a-z0-9_]{4,20}$", message = "loginId는 소문자/숫자/밑줄 4~20자")
    private String loginId;

    @NotBlank
    @Size(min = 8, max = 255, message = "비밀번호는 8자 이상")
    // 필요 시 복잡도: 대소문자/숫자/특수문자 2종 이상
    // @Pattern(regexp="^(?=.*[A-Za-z])(?=.*\\d).{8,}$", message="영문+숫자 조합")
    private String password;

    @NotBlank
    @Email
    @Size(max = 50)
    private String userEmail;

    @NotBlank
    private String characterType;
}
