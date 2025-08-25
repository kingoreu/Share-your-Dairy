package com.share.dairy.service.imageGen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.share.dairy.controller.character.CharacterPaneController;
import com.share.dairy.repo.imageGen.ImageDbRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.OutputStream;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.Base64;
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
 */
@Service
public class ImageGenService {

    /** Workflow로 돌려줄 간단 결과 DTO (공개 URL 두 개) */
    public record Result(String keywordUrl, String characterUrl) {}

    private static final String GEN_ENDPOINT  = "https://api.openai.com/v1/images/generations";
    private static final String EDIT_ENDPOINT = "https://api.openai.com/v1/images/edits";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20)).build();
    private final ObjectMapper om = new ObjectMapper();

    @Value("${openai.api-key:${OPENAI_API_KEY:}}")
    private String openAiApiKey;

    @Value("${app.media.root-dir:./generated-images}")
    private String mediaRootDir;

    @Value("${app.media.url-prefix:/media/}")
    private String mediaUrlPrefix;

    // 컨텍스트 조회용 레포지토리(생성/저장은 워크플로우에서)
    private final ImageDbRepository imageDbRepo;
    public ImageGenService(ImageDbRepository imageDbRepo) {
        this.imageDbRepo = imageDbRepo;
    }

    private static boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }
    private static String mask(String s) {
        if (s == null || s.length() < 12) return String.valueOf(s);
        return s.substring(0, 8) + "…" + s.substring(s.length() - 4);
    }
    private String getApiKey() {
        if (!isBlank(openAiApiKey)) return openAiApiKey.trim();
        String v = System.getenv("OPENAI_API_KEY");
        if (!isBlank(v)) return v.trim();
        v = System.getProperty("OPENAI_API_KEY");
        if (!isBlank(v)) return v.trim();
        return "";
    }

    /**
     * 두 장 생성: 키워드, 캐릭터(무마스크)
     */
    public Result generateTwoWithBase_NoMask(long entryId, String keyword,
                                             String characterLabel, Path baseCharPng,
                                             boolean useCache, String sizeSq) {
        if (isBlank(getApiKey())) {
            throw new IllegalStateException("OPENAI API 키가 설정되지 않았습니다. " +
                    "VM옵션 -Dopenai.api-key 또는 환경변수 OPENAI_API_KEY 로 전달하세요.");
        }
        try {
            Path dir = Path.of(mediaRootDir).toAbsolutePath();
            Files.createDirectories(dir);

            String kwName = entryId + "_keyword.png";
            String chName = entryId + "_character.png";
            Path kwPath = dir.resolve(kwName);
            Path chPath = dir.resolve(chName);

            String kwUrl = mediaUrlPrefix + kwName;
            String chUrl = mediaUrlPrefix + chName;

            String sz = (sizeSq == null || sizeSq.isBlank()) ? "1024" : sizeSq;
            String sizeStr = sz + "x" + sz;

            if (useCache && Files.exists(kwPath) && Files.exists(chPath)) {
                System.out.println("[ImageGenService] cache hit → " + kwPath + " , " + chPath);

                // UI 즉시 반영
                Platform.runLater(() -> {
                    CharacterPaneController controller = CharacterPaneController.getInstance();
                    if (controller != null) controller.updateCharacter(chUrl);
                });

                return new Result(kwUrl, chUrl);
            } else {
                // (1) 키워드 일러스트
                String promptKeyword = """
                    키워드 '%s'를 직관적으로 표현한 미니멀 일러스트.
                    앱 UI용으로 단순/선명, 과한 배경 지양.
                """.formatted(keyword);
                byte[] keywordPng = requestImageGenerate(promptKeyword, sizeStr);

                // (2) 캐릭터 액션 (무마스크 편집)
                String actionPrompt = """
                    동일한 캐릭터(%s)의 외형을 유지하면서 '%s'를 하는 장면.
                    얼굴 무늬/체형/털 색 유지, 소품/포즈만 추가. 배경은 단순.
                """.formatted(characterLabel, keyword);
                byte[] characterPng = requestImageEdit_NoMask(actionPrompt, baseCharPng, sizeStr);

                // 파일 저장(덮어쓰기)
                try (OutputStream os = Files.newOutputStream(kwPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                    os.write(keywordPng);
                }
                try (OutputStream os = Files.newOutputStream(chPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                    os.write(characterPng);
                }

                System.out.println("[ImageGenService] saved → " + kwPath + " / " + chPath);

                // UI 즉시 반영
                Platform.runLater(() -> {
                    CharacterPaneController controller = CharacterPaneController.getInstance();
                    if (controller != null) controller.updateCharacter(chUrl);
                });

                return new Result(kwUrl, chUrl);
            }
        } catch (Exception e) {
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
    // 🔧 FIX: 불필요한 apiKey 파라미터 제거(호출부와 시그니처 일치)
    private byte[] requestImageGenerate(String prompt, String size) throws Exception {
        Map<String,Object> body = new LinkedHashMap<>();
        body.put("model", "gpt-image-1");
        body.put("prompt", prompt);
        body.put("size", size);
        body.put("n", 1);

        String json = new ObjectMapper().writeValueAsString(body);

        HttpRequest req = HttpRequest.newBuilder(URI.create(GEN_ENDPOINT))
                .header("Authorization", "Bearer " + getApiKey())
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode()/100 != 2) {
            throw new IllegalStateException("OpenAI Generate " + res.statusCode() + ": " + res.body());
        }

        JsonNode data0 = om.readTree(res.body()).path("data").get(0);
        String b64 = (data0 != null && data0.hasNonNull("b64_json")) ? data0.get("b64_json").asText() : null;
        if (b64 != null && !b64.isBlank()) return Base64.getDecoder().decode(b64);

        String url = (data0 != null && data0.hasNonNull("url")) ? data0.get("url").asText() : null;
        if (url != null && !url.isBlank()) {
            HttpRequest get = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(60)).build();
            HttpResponse<byte[]> img = http.send(get, HttpResponse.BodyHandlers.ofByteArray());
            if (img.statusCode()/100 != 2)
                throw new IllegalStateException("Fetch image " + img.statusCode());
            return img.body();
        }
        throw new IllegalStateException("Unknown generate response (no b64_json/url)");
    }

    /* ── (B) 편집(Edit, 무마스크) ── */
    private byte[] requestImageEdit_NoMask(String prompt, Path imagePng, String size) throws Exception {
        Multipart mp = new Multipart("----JavaBoundary" + UUID.randomUUID());
        mp.addText("model", "gpt-image-1");
        mp.addText("prompt", prompt);
        mp.addText("size", size);
        mp.addText("n", "1");
        mp.addFile("image[]", imagePng, "image/png");

        HttpRequest req = HttpRequest.newBuilder(URI.create(EDIT_ENDPOINT))
                .header("Authorization", "Bearer " + getApiKey())
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
            HttpRequest get = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(60)).build();
            HttpResponse<byte[]> img = http.send(get, HttpResponse.BodyHandlers.ofByteArray());
            if (img.statusCode()/100 != 2)
                throw new IllegalStateException("Fetch image " + img.statusCode());
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
