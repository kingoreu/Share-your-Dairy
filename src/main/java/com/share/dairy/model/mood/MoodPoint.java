package com.share.dairy.model.mood;

import java.time.LocalDate;

public record MoodPoint(LocalDate date, int score) {
    public MoodPoint {
        if (score < 1 || score > 10) {
            throw new IllegalArgumentException("score must be 1..10");
        }
    }
}