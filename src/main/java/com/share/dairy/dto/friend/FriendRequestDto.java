package com.share.dairy.dto.friend;

import lombok.Data;

@Data
public class FriendRequestDto {
    private Long senderId;   // 요청자
    private Long receiverId; // 받는 사람
}
