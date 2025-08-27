package com.share.dairy.dto.sharedDiary;

import lombok.Data;

@Data
public class SharedDiaryCreateRequest {
    private String sharedDiaryTitle;
    private Long ownerId;
}

