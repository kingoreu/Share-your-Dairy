// src/main/java/com/share/dairy/service/imageGen/ImageGenService.java
package com.share.dairy.service.imageGen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.share.dairy.repo.imageGen.ImageDbRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.OutputStream;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;

/**
 * 이미지 2장 생성:
 *  (1) 키워드 일러스트: /images/generations (JSON)
 *  (2) 캐릭터 액션(무마스크 편집): /images/edits (multipart)
 * - 파일 저장 후 diary_attachments/keyword_images/character_keyword_images 기록
 * - 캐시(useCache=true)면 디스크에 두 파일이 있으면 OpenAI 호출 스킵
 */
@Service
public class ImageGenService {

    /** 컨트롤러/워크플로우로 돌려줄 간단 결과 DTO */
    public record Result(String keywordUrl, String characterUrl) {}

    private static final String GEN_ENDPOINT  = "https://api.openai.com/v1/images/generations";
    private static final String EDIT_ENDPOINT = "https://api.openai.com/v1/images/edits";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20)).build();
    private final ObjectMapper om = new ObjectMapper();

    @Value("${OPENAI_API_KEY:}")           // 환경변수만 사용(없으면 빈 문자열)
    private String openAiApiKey;

    @Value("${app.media.root-dir:./generated-images}")
    private String mediaRootDir;

    @Value("${app.media.url-prefix:/media/}")
    private String mediaUrlPrefix;

    private final ImageDbRepository imageDbRepo;

    public ImageGenService(ImageDbRepository imageDbRepo) {
        this.imageDbRepo = imageDbRepo;
    }

    /**
     * 캐릭터 "라벨"(예: HAMSTER)을 프롬프트에도 반영하는 오버로드.
     * @param useCache true면 파일 2개가 이미 있으면 OpenAI 호출 생략
     */
    @Transactional
    public Result generateTwoWithBase_NoMask(long entryId, String keyword,
                                             String characterLabel, Path baseCharPng,
                                             boolean useCache, String sizeSq) {
        if (openAiApiKey == null || openAiApiKey.isBlank()) {
            throw new IllegalStateException("OPENAI_API_KEY가 설정되지 않았습니다.");
        }
        try {
            // 1) 분석/사용자 존재 체크(분석 먼저 정책)
            var ctxOpt = imageDbRepo.findContext(entryId);
            if (ctxOpt.isEmpty()) {
                throw new IllegalStateException("diary_analysis가 먼저 생성되어야 합니다. entry_id=" + entryId);
            }
            var ctx = ctxOpt.get();

            // 2) 저장 위치/URL
            Path dir = Path.of(mediaRootDir).toAbsolutePath();
            Files.createDirectories(dir);

            String kwName = entryId + "_keyword.png";
            String chName = entryId + "_character.png";
            Path kwPath = dir.resolve(kwName);
            Path chPath = dir.resolve(chName);

            String kwUrl  = mediaUrlPrefix + kwName;
            String chUrl  = mediaUrlPrefix + chName;

            boolean cacheExists = Files.exists(kwPath) && Files.exists(chPath);
            String sz = (sizeSq == null || sizeSq.isBlank()) ? "1024" : sizeSq;
            String sizeStr = sz + "x" + sz;

            // 3) 필요 시 OpenAI 호출
            if (!useCache || !cacheExists) {
                // (1) 키워드 일러스트
                String promptKeyword = """
                    키워드 '%s'를 직관적으로 표현한 미니멀 일러스트.
                    앱 UI용으로 단순/선명하고 과한 배경은 지양한다.
                    """.formatted(keyword);
                byte[] keywordPng = requestImageGenerate(promptKeyword, sizeStr);

                // (2) 캐릭터 액션(캐릭터 종 라벨 반영)
                String actionPrompt = """
                    동일한 캐릭터(%s)의 외형을 유지하면서 '%s'를 하는 장면.
                    얼굴 무늬/체형/털 색은 유지하고, 소품/포즈만으로 표현하라. 배경은 단순하게.
                    """.formatted(characterLabel, keyword);
                byte[] characterPng = requestImageEdit_NoMask(actionPrompt, baseCharPng, sizeStr);

                // 파일 저장(덮어쓰기)
                try (OutputStream os = Files.newOutputStream(kwPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) { os.write(keywordPng); }
                try (OutputStream os = Files.newOutputStream(chPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) { os.write(characterPng); }
            }

            // 4) DB 기록 (첨부 upsert + 생성기록 1회)
            imageDbRepo.upsertAttachment(entryId, kwUrl, 10); // 키워드 일러스트 우선순위 10
            imageDbRepo.upsertAttachment(entryId, chUrl, 20); // 캐릭터 액션 우선순위 20
            imageDbRepo.insertKeywordImageIfAbsent(ctx.analysisId(), ctx.userId());
            imageDbRepo.insertCharacterImageIfAbsent(ctx.analysisId(), ctx.userId());

            System.out.println("[ImageGenService] saved → " + kwPath + " / " + chPath);
            return new Result(kwUrl, chUrl);

        } catch (Exception e) {
            throw new RuntimeException("이미지 생성 실패: " + e.getMessage(), e);
        }
    }

    /** 하위 호환: 캐릭터 라벨을 파일명에서 추정(hamster.png → "hamster") */
    @Transactional
    public Result generateTwoWithBase_NoMask(long entryId, String keyword,
                                             Path baseCharPng, boolean useCache, String sizeSq) {
        String name = baseCharPng.getFileName().toString().replaceFirst("(?i)\\.(png|jpg|jpeg|webp)$", "");
        return generateTwoWithBase_NoMask(entryId, keyword, name, baseCharPng, useCache, sizeSq);
    }

    // ------- OpenAI 호출 유틸: 텍스트→이미지 -------
    private byte[] requestImageGenerate(String prompt, String size) throws Exception {
        Map<String,Object> body = new LinkedHashMap<>();
        body.put("model", "gpt-image-1");
        body.put("prompt", prompt);
        body.put("size", size);
        body.put("n", 1);

        String json = om.writeValueAsString(body);

        HttpRequest req = HttpRequest.newBuilder(URI.create(GEN_ENDPOINT))
                .header("Authorization", "Bearer " + openAiApiKey)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode()/100 != 2) {
            throw new IllegalStateException("OpenAI Generate " + res.statusCode() + ": " + res.body());
        }

        JsonNode data0 = om.readTree(res.body()).path("data").get(0);

        // ① b64_json
        String b64 = (data0 != null && data0.hasNonNull("b64_json")) ? data0.get("b64_json").asText() : null;
        if (b64 != null && !b64.isBlank()) return Base64.getDecoder().decode(b64);

        // ② url fallback
        String url = (data0 != null && data0.hasNonNull("url")) ? data0.get("url").asText() : null;
        if (url != null && !url.isBlank()) {
            HttpResponse<byte[]> img = http.send(
                    HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(60)).build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            if (img.statusCode()/100 != 2) throw new IllegalStateException("Fetch image " + img.statusCode());
            return img.body();
        }
        throw new IllegalStateException("Unknown generate response (no b64_json/url)");
    }

    // ------- OpenAI 호출 유틸: 편집(무마스크) -------
    private byte[] requestImageEdit_NoMask(String prompt, Path imagePng, String size) throws Exception {
        Multipart mp = new Multipart("----JavaBoundary" + UUID.randomUUID());
        mp.addText("model", "gpt-image-1");
        mp.addText("prompt", prompt);
        mp.addText("size", size);
        mp.addText("n", "1");
        mp.addFile("image[]", imagePng, "image/png"); // image[] 필드명 호환성 좋음

        HttpRequest req = HttpRequest.newBuilder(URI.create(EDIT_ENDPOINT))
                .header("Authorization", "Bearer " + openAiApiKey)
                .header("Content-Type", mp.contentType())
                .timeout(Duration.ofSeconds(90))
                .POST(mp.publisher())
                .build();

        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode()/100 != 2) {
            throw new IllegalStateException("OpenAI Edit " + res.statusCode() + ": " + res.body());
        }

        JsonNode data0 = new ObjectMapper().readTree(res.body()).path("data").get(0);
        String b64 = (data0 != null && data0.hasNonNull("b64_json")) ? data0.get("b64_json").asText() : null;
        if (b64 != null && !b64.isBlank()) return Base64.getDecoder().decode(b64);

        String url = (data0 != null && data0.hasNonNull("url")) ? data0.get("url").asText() : null;
        if (url != null && !url.isBlank()) {
            HttpResponse<byte[]> img = http.send(
                    HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(60)).build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            if (img.statusCode()/100 != 2) throw new IllegalStateException("Fetch image " + img.statusCode());
            return img.body();
        }
        throw new IllegalStateException("Unknown edit response (no b64_json/url)");
    }

    /** 경량 multipart/form-data 빌더 */
    private static class Multipart {
        private final String boundary;
        private final List<byte[]> parts = new ArrayList<>();
        Multipart(String boundary) { this.boundary = boundary; }
        void addText(String name, String value) {
            String head = "--"+boundary+"\r\n" +
                    "Content-Disposition: form-data; name=\""+name+"\"\r\n\r\n";
            parts.add(head.getBytes(StandardCharsets.UTF_8));
            parts.add(value.getBytes(StandardCharsets.UTF_8));
            parts.add("\r\n".getBytes(StandardCharsets.UTF_8));
        }
        void addFile(String name, Path file, String mime) throws Exception {
            String head = "--"+boundary+"\r\n" +
                    "Content-Disposition: form-data; name=\""+name+"\"; filename=\""+file.getFileName()+"\"\r\n" +
                    "Content-Type: "+mime+"\r\n\r\n";
            parts.add(head.getBytes(StandardCharsets.UTF_8));
            parts.add(Files.readAllBytes(file));
            parts.add("\r\n".getBytes(StandardCharsets.UTF_8));
        }
        HttpRequest.BodyPublisher publisher() {
            byte[] end = ("--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8);
            var list = new ArrayList<byte[]>(parts); list.add(end);
            return HttpRequest.BodyPublishers.ofByteArrays(list);
        }
        String contentType() { return "multipart/form-data; boundary=" + boundary; }
    }
}
