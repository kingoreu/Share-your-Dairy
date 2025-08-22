package com.share.dairy.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * "raccoon"(또는 "raccoon.png") → base-characters/raccoon.(png|jpg|jpeg|webp)
 * - 확장자 허용 넓힘
 * - 실제 찾은 경로 로그 출력
 */
@Component
public class CharacterAssetResolver {

    @Value("${app.characters.root-dir:./base-characters}")
    private String charactersRootDir;

    public Path resolve(String name) {
        if (name == null || name.isBlank())
            throw new IllegalArgumentException("character가 비었습니다.");

        Path dir = Path.of(charactersRootDir).toAbsolutePath();
        System.out.println("[CharacterAssetResolver] dir = " + dir);

        String base = name.trim().replaceAll("(?i)\\.(png|jpg|jpeg|webp)$", "");
        String[] exts = {".png", ".jpg", ".jpeg", ".webp"};

        for (String ext : exts) {
            Path p = dir.resolve(base + ext);
            if (Files.exists(p)) {
                System.out.println("[CharacterAssetResolver] matched = " + p);
                return p;
            }
        }
        throw new IllegalArgumentException("캐릭터 파일을 찾을 수 없습니다: " + base + ".(png/jpg/jpeg/webp)");
    }
}
