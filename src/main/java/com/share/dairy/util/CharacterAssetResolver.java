// src/main/java/com/share/dairy/util/CharacterAssetResolver.java
package com.share.dairy.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.file.*;

/**
 * 캐릭터 PNG를 “file:” 또는 “classpath:”에서 찾아 Path로 돌려준다.
 *
 * application.yml 예:
 * app:
 *   characters:
 *     location: classpath:character   # src/main/resources/character/HAMSTER.png
 *
 * 또는
 * app:
 *   characters:
 *     location: file:./base-characters # 프로젝트 루트 기준 로컬 폴더
 */
@Component
public class CharacterAssetResolver {

    @Value("${app.characters.location:classpath:character}")
    private String location;

    private final ResourceLoader resourceLoader;

    public CharacterAssetResolver(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    /** character_type(HAMSTER 등)로 파일을 찾아 Path 반환 (없으면 IllegalArgumentException) */
    public Path resolve(String name) {
        if (name == null || name.isBlank())
            throw new IllegalArgumentException("character가 비었습니다.");

        String base = name.trim().replaceAll("(?i)\\.(png|jpg|jpeg|webp)$", "");
        String[] bases = { base, base.toLowerCase(), base.toUpperCase() };
        String[] exts  = { ".png", ".jpg", ".jpeg", ".webp" };

        for (String b : bases) {
            for (String ext : exts) {
                String candidate = normalize(location, b + ext); // file:/ or classpath:/
                Resource res = resourceLoader.getResource(candidate);
                try {
                    if (res.exists()) {
                        // 파일시스템이면 바로 Path 사용
                        if (res.isFile()) {
                            Path p = res.getFile().toPath();
                            System.out.println("[CharacterAssetResolver] matched(file) = " + p);
                            return p;
                        }
                        // JAR/classpath이면 임시파일로 복사 후 Path 사용
                        try (InputStream in = res.getInputStream()) {
                            Path tmp = Files.createTempFile("char-", ext);
                            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
                            System.out.println("[CharacterAssetResolver] matched(classpath→tmp) = " + tmp);
                            return tmp;
                        }
                    }
                } catch (Exception ignore) { /* 다음 후보 계속 */ }
            }
        }
        throw new IllegalArgumentException(
                "캐릭터 파일을 찾을 수 없습니다: " + base + ".(png/jpg/jpeg/webp) @ " + location
        );
    }

    /** 과거 호환용 래퍼 (이름만 다르고 같은 동작) */
    public Path resolveByCharacterType(String characterType) {
        return resolve(characterType);
    }

    private static String normalize(String baseLocation, String filename) {
        String sep = baseLocation.endsWith("/") ? "" : "/";
        return baseLocation + sep + filename;
    }
}
