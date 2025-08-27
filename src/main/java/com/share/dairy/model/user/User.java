package com.share.dairy.model.user;

import java.time.LocalDateTime;

public class User {
    private Long userId;
    private String nickname;
    private String loginId;
    private String password;
    private String userEmail;
    private String characterType;
    private LocalDateTime userCreatedAt;
    private LocalDateTime userUpdatedAt;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }

    public String getLoginId() { return loginId; }
    public void setLoginId(String loginId) { this.loginId = loginId; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getUserEmail() { return userEmail; }
    public void setUserEmail(String userEmail) { this.userEmail = userEmail; }

    public String getCharacterType() { return characterType; }
    public void setCharacterType(String characterType) { this.characterType = characterType; }

    public LocalDateTime getUserCreatedAt() { return userCreatedAt; }
    public void setUserCreatedAt(LocalDateTime userCreatedAt) { this.userCreatedAt = userCreatedAt; }

    public LocalDateTime getUserUpdatedAt() { return userUpdatedAt; }
    public void setUserUpdatedAt(LocalDateTime userUpdatedAt) { this.userUpdatedAt = userUpdatedAt; }
}
