package com.share.dairy.service.imageGen;

import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ProgressRegistry {

    public static class JobState {
        public volatile JobStatus status = JobStatus.PENDING;
        public volatile int progress = 0;       // 0~100
        public volatile String message = "대기중";
        public volatile Instant updatedAt = Instant.now();
    }

    private final Map<Long, JobState> store = new ConcurrentHashMap<>();

    public void start(long entryId, String msg) {
        JobState st = store.computeIfAbsent(entryId, k -> new JobState());
        st.status = JobStatus.RUNNING;
        st.progress = Math.max(0, st.progress);
        st.message = (msg == null || msg.isBlank()) ? "시작" : msg;
        st.updatedAt = Instant.now();
    }

    public void update(long entryId, int progress, String msg) {
        JobState st = store.computeIfAbsent(entryId, k -> new JobState());
        st.status = JobStatus.RUNNING;
        st.progress = Math.max(0, Math.min(100, progress));
        if (msg != null && !msg.isBlank()) st.message = msg;
        st.updatedAt = Instant.now();
    }

    public void done(long entryId, String msg) {
        JobState st = store.computeIfAbsent(entryId, k -> new JobState());
        st.status = JobStatus.DONE;
        st.progress = 100;
        if (msg != null && !msg.isBlank()) st.message = msg;
        st.updatedAt = Instant.now();
    }

    public void error(long entryId, String msg) {
        JobState st = store.computeIfAbsent(entryId, k -> new JobState());
        st.status = JobStatus.ERROR;
        if (msg != null && !msg.isBlank()) st.message = msg;
        st.updatedAt = Instant.now();
    }

    public JobState get(long entryId) {
        return store.get(entryId);
    }
}
