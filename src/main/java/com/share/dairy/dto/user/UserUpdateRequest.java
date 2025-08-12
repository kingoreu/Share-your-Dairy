package com.share.dairy.dto.user;

import lombok.Data;

// 내 정보 보기에서 사용자 정보 수정
// 어디까지 수정되게 할 건지?
@Data
public class UserUpdateRequest {
    private String nickname;
    private String userEmail;
}
