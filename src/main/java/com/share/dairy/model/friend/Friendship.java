package com.share.dairy.model.friend;

import com.share.dairy.model.enums.FriendshipStatus;
import java.time.LocalDateTime;

public class Friendship {
    private Long userId;
    private Long friendId;
    private FriendshipStatus friendshipStatus;
    private LocalDateTime requestedAt;
    private LocalDateTime respondedAt; // nullable

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getFriendId() { return friendId; }
    public void setFriendId(Long friendId) { this.friendId = friendId; }

    public FriendshipStatus getFriendshipStatus() { return friendshipStatus; }
    public void setFriendshipStatus(FriendshipStatus friendshipStatus) { this.friendshipStatus = friendshipStatus; }

    public LocalDateTime getRequestedAt() { return requestedAt; }
    public void setRequestedAt(LocalDateTime requestedAt) { this.requestedAt = requestedAt; }

    public LocalDateTime getRespondedAt() { return respondedAt; }
    public void setRespondedAt(LocalDateTime respondedAt) { this.respondedAt = respondedAt; }
}
