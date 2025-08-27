package com.share.dairy.dto.user;

import lombok.Data;

@Data
public class PendingSignUp {
    public final String nickname;
    public final String loginId;
    public final String password;
    public final String userEmail;
    public PendingSignUp(String nickname, String loginId, String password, String userEmail) {
        this.nickname = nickname;
        this.loginId = loginId;
        this.password = password;
        this.userEmail = userEmail;
    }
}
