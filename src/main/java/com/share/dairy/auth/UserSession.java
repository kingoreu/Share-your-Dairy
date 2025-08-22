package com.share.dairy.auth;

/** 로그인한 사용자 정보를 보관하는 전역 세션 (JavaFX 클라이언트 전용) */
public final class UserSession {
    private static volatile UserSession current;

    private final long userId;
    private final String loginId;
    private String nickname;
    private String email;
    private String characterType; // 예: RACCOON / DOG / CAT

    public UserSession(long userId, String loginId, String nickname, String email, String characterType) {
        this.userId = userId;
        this.loginId = loginId;
        this.nickname = nickname;
        this.email = email;
        this.characterType = characterType;
    }

    // 현재 로그인한 사용자 ID를 반환 (없으면 null)
    public static Long currentId() { return current != null ? current.userId : null; }
    public static long requireId() {
        if (current == null) throw new IllegalStateException("로그인 세션이 없습니다.");
        return current.userId;
    }

    /** 현재 세션 저장/조회/해제 */
    public static void set(UserSession s) { current = s; }
    public static UserSession get() { return current; }
    public static void clear() { current = null; }

    // getters/setters
    public long getUserId() { return userId; }
    public String getLoginId() { return loginId; }
    public String getNickname() { return nickname; }
    public String getEmail() { return email; }
    public String getCharacterType() { return characterType; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public void setEmail(String email) { this.email = email; }
    public void setCharacterType(String characterType) { this.characterType = characterType; }
}
