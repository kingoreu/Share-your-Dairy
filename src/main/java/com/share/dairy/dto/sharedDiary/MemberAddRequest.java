package com.share.dairy.dto.sharedDiary;

import lombok.Data;

@Data
public class MemberAddRequest {
    private Long userId;
    private Long ownerId;
}
