package com.share.dairy.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.List;

public enum CharacterType {
    ZZUNI("/character/zzuni.png"),
    CAT("/character/cat.png"),
    HAMSTER("/character/hamster.png"),
    RACCOON("/character/raccoon.png"),
    BEAR("/character/bear.png"),
    DEER("/character/deer.png"),
    DOG("/character/dog.png"),
    DUCK("/character/duck.png"),
    RABBIT("/character/rabbit.png"),
    RICHARD("/character/richard.png"),
    TAKO("/character/tako.png"),
    WOLF("/character/wolf.png");

    private final String imagePath;

    CharacterType(String imagePath) {
        this.imagePath = imagePath;
    }

    @JsonValue
    public String toValue() {
        return this.name().toLowerCase();
    }

    @JsonCreator
    public static CharacterType fromValue(String value) {
        try {
            return CharacterType.valueOf(value.toUpperCase());
        } catch (Exception e) {
            return ZZUNI;
        }
    }

    public String getImagePath() {
        return imagePath;
    }

    public static CharacterType fromPath(String path) {
        if (path == null) return ZZUNI;
        String file = path.substring(path.lastIndexOf('/') + 1, path.lastIndexOf('.'));
        try {
            return CharacterType.valueOf(file.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ZZUNI; // 기본값
        }
    }

    public static List<String> getAllImagePaths() {
        return Arrays.stream(values())
                .map(CharacterType::getImagePath)
                .toList();
    }

    public static CharacterType fromString(String name) {
        try {
            return CharacterType.valueOf(name.toUpperCase());
        } catch (Exception e) {
            return ZZUNI; // 기본값
        }
    }
}