package com.share.dairy.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

// 내 정보 보기에서 사용자 정보 수정
// 어디까지 수정되게 할 건지?
@Data
public class UserUpdateRequest {
    @NotBlank
    @Size(min = 2, max = 50)
    private String nickname;

    @NotBlank
    @Email
    @Size(min = 2, max = 50)
    private String userEmail;
}
