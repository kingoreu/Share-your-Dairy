// src/main/java/com/share/dairy/util/CharacterAssetResolver.java
package com.share.dairy.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * users.character_type(코드) → base-characters/<asset>.(png|jpg|jpeg|webp) 로 매핑.
 * - 코드: HAMSTER, RACCOON, DOG, CAT, BEAR, DEER, DUCK, RABBIT, WOLF, RICHARD, TAKO, ZZUNI
 * - 에셋 파일명은 소문자 관례로 준비 (예: hamster.png, raccoon.png, zzuni.png)
 */
@Component
public class CharacterAssetResolver {

    @Value("${app.characters.root-dir:./base-characters}")
    private String charactersRootDir;

    // 대문자 코드 → 에셋 파일명(소문자) 매핑
    private static final Map<String,String> CODE_TO_ASSET = Map.ofEntries(
            Map.entry("HAMSTER","hamster"),
            Map.entry("RACCOON","raccoon"),
            Map.entry("DOG","dog"),
            Map.entry("CAT","cat"),
            Map.entry("BEAR","bear"),
            Map.entry("DEER","deer"),
            Map.entry("DUCK","duck"),
            Map.entry("RABBIT","rabbit"),
            Map.entry("WOLF","wolf"),
            Map.entry("RICHARD","richard"),
            Map.entry("TAKO","tako"),
            Map.entry("ZZUNI","zzuni")
    );

    /** DB의 character_type 코드를 받아 에셋 PNG 경로를 찾는다. */
    public Path resolveByCharacterType(String code) {
        if (code == null || code.isBlank())
            throw new IllegalArgumentException("character_type이 비어 있습니다.");
        String asset = CODE_TO_ASSET.get(code.trim().toUpperCase());
        if (asset == null) asset = code.trim().toLowerCase(); // 혹시 모를 예외 입력 방어
        return resolve(asset); // base-characters/<asset>.(png|jpg|jpeg|webp)
    }

    /** 파일명 또는 이름만 줘도 됨(확장자 자동 탐색) */
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
