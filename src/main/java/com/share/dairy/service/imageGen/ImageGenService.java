package com.share.dairy.service.imageGen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

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
 *  (2) 캐릭터 액션: 기본 캐릭터 PNG를 "마스크 없이" /images/edits (multipart)
 * 결과 PNG 저장 후 URL 리턴.
 *
 * ※ gpt-image-1 일부 배포는 response_format/background 파라미터 미지원 → 모두 제거.
 * ※ 응답은 b64_json 혹은 url 이 섞일 수 있어 둘 다 처리.
 */
@Service
public class ImageGenService {

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

    public Result generateTwoWithBase_NoMask(long entryId, String keyword, Path baseCharPng,
                                             boolean useCache, String sizeSq) {
        if (openAiApiKey == null || openAiApiKey.isBlank()) {
            throw new IllegalStateException("OPENAI API 키가 설정되지 않았습니다. " +
                    "VM옵션 -Dopenai.api-key 또는 환경변수 OPENAI_API_KEY 로 전달하세요.");
        }
        try {
            // 저장 폴더 보장
            Path dir = Path.of(mediaRootDir).toAbsolutePath();
            Files.createDirectories(dir);

            // 파일명/경로
            String kwName = entryId + "_keyword.png";
            String chName = entryId + "_character.png";
            Path kwPath = dir.resolve(kwName);
            Path chPath = dir.resolve(chName);

            // 캐시
            if (useCache && Files.exists(kwPath) && Files.exists(chPath)) {
                return new Result(mediaUrlPrefix + kwName, mediaUrlPrefix + chName);
            }

            String sz = (sizeSq == null || sizeSq.isBlank()) ? "1024" : sizeSq;
            String sizeStr = sz + "x" + sz;

            // (1) 키워드 일러스트
            String promptKeyword = """
                키워드 '%s'를 직관적으로 표현한 미니멀 일러스트.
                앱 UI용으로 단순/선명, 과한 배경 지양.
            """.formatted(keyword);
            byte[] keywordPng = requestImageGenerate(promptKeyword, sizeStr);

            // (2) 캐릭터 액션 (무마스크 편집)
            String actionPrompt = """
                동일한 캐릭터(너구리)의 외형을 유지하면서 '%s'를 하는 장면.
                얼굴 무늬/체형/털 색 유지, 소품/포즈만 추가. 배경은 단순.
            """.formatted(keyword);
            byte[] characterPng = requestImageEdit_NoMask(actionPrompt, baseCharPng, sizeStr);

            // 저장
            try (OutputStream os = Files.newOutputStream(kwPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) { os.write(keywordPng); }
            try (OutputStream os = Files.newOutputStream(chPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) { os.write(characterPng); }

            System.out.println("[ImageGenService] saved → " + kwPath + " / " + chPath);
            return new Result(mediaUrlPrefix + kwName, mediaUrlPrefix + chName);

        } catch (Exception e) {
            throw new RuntimeException("이미지 생성 실패: " + e.getMessage(), e);
        }
    }

    /* ── (A) 텍스트→이미지: response_format/background 빼고, b64_json/url 모두 처리 ── */
    private byte[] requestImageGenerate(String prompt, String size) throws Exception {
        Map<String,Object> body = new LinkedHashMap<>();
        body.put("model", "gpt-image-1");
        body.put("prompt", prompt);
        body.put("size", size);   // 예: "1024x1024"
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
            HttpRequest get = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(60)).build();
            HttpResponse<byte[]> img = http.send(get, HttpResponse.BodyHandlers.ofByteArray());
            if (img.statusCode()/100 != 2)
                throw new IllegalStateException("Fetch image " + img.statusCode());
            return img.body();
        }
        throw new IllegalStateException("Unknown generate response (no b64_json/url)");
    }

    /* ── (B) 편집(Edit, 무마스크): response_format/background 제거 + image[] 필드 사용 ── */
    private byte[] requestImageEdit_NoMask(String prompt, Path imagePng, String size) throws Exception {
        Multipart mp = new Multipart("----JavaBoundary" + UUID.randomUUID());
        mp.addText("model", "gpt-image-1");
        mp.addText("prompt", prompt);
        mp.addText("size", size);
        mp.addText("n", "1");
        // response_format / background 제거
        mp.addFile("image[]", imagePng, "image/png"); // 호환성 높은 필드명

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
        // ① b64_json
        String b64 = (data0 != null && data0.hasNonNull("b64_json")) ? data0.get("b64_json").asText() : null;
        if (b64 != null && !b64.isBlank()) return Base64.getDecoder().decode(b64);
        // ② url fallback
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
