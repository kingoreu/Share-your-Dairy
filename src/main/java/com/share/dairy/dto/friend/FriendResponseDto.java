package com.share.dairy.dto.friend;

import com.share.dairy.model.enums.FriendshipStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FriendResponseDto {
    private Long userId;
    private Long friendId;
    private FriendshipStatus friendshipStatus;
    private LocalDateTime requestedAt;
    private LocalDateTime respondedAt;
}
