package com.share.dairy.dto.imageGen;

import com.share.dairy.service.imageGen.ProgressRegistry;

public class ImageJobStatusDto {
    public String status;    // "PENDING|RUNNING|DONE|ERROR"
    public Integer progress; // 0..100 (모를 땐 null 가능)
    public String message;

    public static ImageJobStatusDto of(ProgressRegistry.JobState st) {
        ImageJobStatusDto dto = new ImageJobStatusDto();
        dto.status = st.status.name();
        dto.progress = st.progress;
        dto.message = st.message;
        return dto;
    }
}