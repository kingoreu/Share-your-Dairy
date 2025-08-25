// src/main/java/com/share/dairy/service/imageGen/ImageGenService.java
package com.share.dairy.service.imageGen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.share.dairy.controller.character.CharacterPaneController;
import com.share.dairy.repo.imageGen.ImageDbRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
// ❌ @Transactional 제거: 외부 API 호출은 트랜잭션 밖에서(Workflow에서 관리)

import java.io.OutputStream;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import javafx.application.Platform;

/**
 * 이미지 2장 생성 서비스
 *  - (1) 키워드 일러스트: /v1/images/generations (JSON)
 *  - (2) 캐릭터 액션(무마스크 편집): /v1/images/edits (multipart/form-data)
 *
 * 생성 후:
 *  - /generated-images/<entry>_keyword.png, <entry>_character.png 저장
 *  - ✅ DB 저장은 하지 않고, 생성된 "공개 URL"만 반환한다.
 *    (실제 DB 저장은 DiaryWorkflowService가 수행)
 *
 * 주의:
 *  - 일부 배포에서 response_format/background 미지원 → 사용 안 함
 *  - 응답은 b64_json 또는 url → 둘 다 처리
 */
@Service
public class ImageGenService {

    /** Workflow로 돌려줄 간단 결과 DTO (공개 URL 두 개) */
    public record Result(String keywordUrl, String characterUrl) {}

    // OpenAI 이미지 엔드포인트
    private static final String GEN_ENDPOINT  = "https://api.openai.com/v1/images/generations";
    private static final String EDIT_ENDPOINT = "https://api.openai.com/v1/images/edits";

    // HTTP 클라이언트 & JSON 매퍼
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20)).build();
    private final ObjectMapper om = new ObjectMapper();

    // ======== 환경/앱 설정 ========

    /** OpenAI 키: 스프링이 못 줄 수도 있으니(예: .env) 아래 getApiKey()로 보강 */
    @Value("${OPENAI_API_KEY:}")
    private String openAiApiKey;

    /** 생성 파일 저장 루트(프로젝트 루트 기준 기본값) */
    @Value("${app.media.root-dir:./generated-images}")
    private String mediaRootDir;

    /** 공개 URL 접두사: /media/ (WebConfig에서 정적 매핑) */
    @Value("${app.media.url-prefix:/media/}")
    private String mediaUrlPrefix;

    // ======== DB Repository ========
    // ⚠️ 여기서는 "컨텍스트 조회(findContext)"에만 사용한다.
    //    (DB 저장은 DiaryWorkflowService가 수행)
    private final ImageDbRepository imageDbRepo;

    public ImageGenService(ImageDbRepository imageDbRepo) {
        this.imageDbRepo = imageDbRepo;
    }

    // ---------- 유틸: 키/문자열 ----------
    private static boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }
    private static String mask(String s) {
        if (s == null || s.length() < 12) return String.valueOf(s);
        return s.substring(0, 8) + "…" + s.substring(s.length() - 4);
    }
    /** @Value → env → -D 프로퍼티 순으로 키 취득 */
    private String getApiKey() {
        if (!isBlank(openAiApiKey)) return openAiApiKey.trim();
        String v = System.getenv("OPENAI_API_KEY");
        if (!isBlank(v)) return v.trim();
        v = System.getProperty("OPENAI_API_KEY");
        if (!isBlank(v)) return v.trim();
        return "";
    }

    /**
     * 캐릭터 "라벨"(예: HAMSTER, RACCOON ...)을 프롬프트에도 반영.
     *
     * ✅ 트랜잭션 없음: 외부 API 호출은 느릴 수 있으므로 서비스 레벨에서 트랜잭션을 열지 않는다.
     * @param useCache true면 파일 2개가 이미 있으면 OpenAI 호출 생략
     */
    public Result generateTwoWithBase_NoMask(long entryId, String keyword,
                                             String characterLabel, Path baseCharPng,
                                             boolean useCache, String sizeSq) {
        // 0) 키 존재 체크(부팅 실패 방지를 위해 여기서 검증)
        String apiKey = getApiKey();
        if (isBlank(apiKey)) {
            throw new IllegalStateException(
                    "OPENAI_API_KEY가 설정되지 않았습니다. " +
                            "Run/Debug 환경변수 또는 -DOPENAI_API_KEY, 혹은 Spring @Value로 주입하세요."
            );
        }
        System.out.println("[ImageGenService] OPENAI key=" + mask(apiKey));

        try {
            // 1) 분석/사용자 컨텍스트 확인(정책: 분석이 먼저여야 함)
            var ctxOpt = imageDbRepo.findContext(entryId);
            if (ctxOpt.isEmpty()) {
                throw new IllegalStateException("diary_analysis가 먼저 생성되어야 합니다. entry_id=" + entryId);
            }
            var ctx = ctxOpt.get();
            System.out.println("[ImageGenService] ctx.analysisId=" + ctx.analysisId()
                    + ", userId=" + ctx.userId()
                    + ", keyword=" + ctx.analysisKeywords()
                    + ", character=" + characterLabel);

            // 2) 저장 위치/URL 계산
            Path dir = Path.of(mediaRootDir).toAbsolutePath();
            Files.createDirectories(dir);

            String kwName = entryId + "_keyword.png";
            String chName = entryId + "_character.png";
            Path kwPath = dir.resolve(kwName);
            Path chPath = dir.resolve(chName);

            String kwUrl = mediaUrlPrefix + kwName; // 공개 URL (정적 리소스 매핑)
            String chUrl = mediaUrlPrefix + chName;

            boolean cacheExists = Files.exists(kwPath) && Files.exists(chPath);
            String sz = (sizeSq == null || sizeSq.isBlank()) ? "1024" : sizeSq;
            String sizeStr = sz + "x" + sz;

            // 3) 캐시 미사용 또는 캐시 없으면 OpenAI 호출
            if (!useCache || !cacheExists) {
                System.out.println("[ImageGenService] generating... size=" + sizeStr + ", cache=" + useCache);

                // (1) 키워드 일러스트
                String promptKeyword = """
                        A minimal, flat icon-style illustration that intuitively represents the keyword “%s”.
                        Simple geometric shapes, crisp clean outlines, 2–3 colors max, no text, no gradients, no shadows.
                        Centered composition with even padding. Transparent background (PNG), no background elements.
                    """.formatted(keyword);
                byte[] keywordPng = requestImageGenerate(apiKey, promptKeyword, sizeStr);

                // (2) 캐릭터 액션
                if (!Files.exists(baseCharPng)) {
                    throw new IllegalStateException("캐릭터 PNG가 존재하지 않습니다: " + baseCharPng);
                }
                String actionPrompt = """
                        Keep the exact same character as “%s”: preserve facial markings, body shape, and fur color.
                        Show the character doing “%s” using only pose and small props; do not alter face patterns or body shape.
                        Same art style and proportions as the original, clean outlines, no text. Transparent background (PNG), no background.   
                    """.formatted(characterLabel, keyword);
                byte[] characterPng = requestImageEdit_NoMask(apiKey, actionPrompt, baseCharPng, sizeStr);

                // 파일 저장(덮어쓰기)
                try (OutputStream os = Files.newOutputStream(kwPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) { os.write(keywordPng); }
                try (OutputStream os = Files.newOutputStream(chPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) { os.write(characterPng); }
            } else {
                System.out.println("[ImageGenService] cache hit → " + kwPath + " , " + chPath);
            }

            // 4) ✅ DB 기록은 여기서 하지 않는다.
            //    (예전 코드)
            //    imageDbRepo.upsertAttachment(entryId, kwUrl, 10);
            //    imageDbRepo.upsertAttachment(entryId, chUrl, 20);
            //
            //    (새 정책)
            //    DiaryWorkflowService가
            //    insertKeywordImageIfAbsent / insertCharacterImageIfAbsent 를 호출해 저장한다.

            System.out.println("[ImageGenService] saved → " + kwPath + " / " + chPath);

            // UI 즉시 반영 (JavaFX Application Thread에서 실행)
            // 제가 추가한 부분이에요!!!!!
            Platform.runLater(() -> {
                CharacterPaneController controller = CharacterPaneController.getInstance();
                if (controller != null) {
                    controller.updateCharacter(chUrl); // 캐릭터 교체
                }
            });

            return new Result(kwUrl, chUrl); // ✅ URL만 반환 → 워크플로우가 DB에 저장

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("이미지 생성 실패: " + e.getMessage(), e);
        }
    }

    /** 하위 호환: 캐릭터 라벨을 파일명에서 추정(hamster.png → "hamster") */
    public Result generateTwoWithBase_NoMask(long entryId, String keyword,
                                             Path baseCharPng, boolean useCache, String sizeSq) {
        String name = baseCharPng.getFileName().toString()
                .replaceFirst("(?i)\\.(png|jpg|jpeg|webp)$", "");
        return generateTwoWithBase_NoMask(entryId, keyword, name, baseCharPng, useCache, sizeSq);
    }

    // ------- OpenAI 호출 유틸: 텍스트→이미지 -------
    private byte[] requestImageGenerate(String apiKey, String prompt, String size) throws Exception {
        Map<String,Object> body = new LinkedHashMap<>();
        body.put("model", "gpt-image-1");
        body.put("prompt", prompt);
        body.put("size", size); // 예: "1024x1024"
        body.put("n", 1);

        String json = new ObjectMapper().writeValueAsString(body);

        HttpRequest req = HttpRequest.newBuilder(URI.create(GEN_ENDPOINT))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        System.out.println("[OpenAI] generate status=" + res.statusCode());
        if (res.statusCode()/100 != 2) {
            if (res.statusCode() == 401 || res.statusCode() == 403) {
                throw new IllegalStateException("OpenAI 인증 실패(키/권한). body=" + res.body());
            }
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
    private byte[] requestImageEdit_NoMask(String apiKey, String prompt, Path imagePng, String size) throws Exception {
        Multipart mp = new Multipart("----JavaBoundary" + UUID.randomUUID());
        mp.addText("model", "gpt-image-1");
        mp.addText("prompt", prompt);
        mp.addText("size", size);
        mp.addText("n", "1");
        mp.addFile("image[]", imagePng, "image/png"); // image[] 필드명 호환성 좋음

        HttpRequest req = HttpRequest.newBuilder(URI.create(EDIT_ENDPOINT))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", mp.contentType())
                .timeout(Duration.ofSeconds(90))
                .POST(mp.publisher())
                .build();

        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        System.out.println("[OpenAI] edit status=" + res.statusCode());
        if (res.statusCode()/100 != 2) {
            if (res.statusCode() == 401 || res.statusCode() == 403) {
                throw new IllegalStateException("OpenAI 인증 실패(키/권한). body=" + res.body());
            }
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