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
 * 이미지 2장 생성 서비스
 *  - (1) 키워드 일러스트: /v1/images/generations (JSON)
 *  - (2) 캐릭터 액션(무마스크 편집): /v1/images/edits (multipart/form-data)
 *
 * 파일 저장 후:
 *  - diary_attachments 에 URL 2개를 기록(키워드/캐릭터)
 *  - keyword_images / character_keyword_images 는 분석기준(analysis_id)으로 "1회 생성됨"을 기록
 *
 * ※ 주의
 *  - 일부 배포에서 response_format/background 파라미터 미지원 → 사용 안 함
 *  - 응답은 b64_json 또는 url 형태 모두 올 수 있어 둘 다 처리
 */
@Service
public class ImageGenService {

    /** 컨트롤러에서 돌려줄 간단 결과 DTO(레코드) */
    public record Result(String keywordUrl, String characterUrl) {}

    // OpenAI 이미지 엔드포인트
    private static final String GEN_ENDPOINT  = "https://api.openai.com/v1/images/generations";
    private static final String EDIT_ENDPOINT = "https://api.openai.com/v1/images/edits";

    // HTTP 클라이언트 & JSON 매퍼
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();
    private final ObjectMapper om = new ObjectMapper();

    // ======== 환경/앱 설정 ========

    /** OpenAI 키: 환경변수만 사용. 없으면 빈 문자열("") → 아래에서 수동 체크 */
    @Value("${OPENAI_API_KEY:}")
    private String openAiApiKey;

    /** 생성 파일 저장 루트(프로젝트 루트 기준 기본값) */
    @Value("${app.media.root-dir:./generated-images}")
    private String mediaRootDir;

    /** 공개 URL 접두사: /media/ */
    @Value("${app.media.url-prefix:/media/}")
    private String mediaUrlPrefix;

    // ======== DB Repository ========

    private final ImageDbRepository imageDbRepo;

    public ImageGenService(ImageDbRepository imageDbRepo) {
        this.imageDbRepo = imageDbRepo;
    }

    /**
     * 이미지 두 장을 생성(또는 캐시 사용)하고, 파일 저장 + DB 기록까지 한 번에 처리.
     *
     * @param entryId     diary_entries.entry_id
     * @param keyword     키워드(예: "야구")
     * @param baseCharPng 캐릭터 기본 PNG 경로(예: ./base-characters/raccoon.png)
     * @param useCache    true면 캐시(두 파일 존재) 시 OpenAI 호출 스킵
     * @param sizeSq      정사각 해상도 문자열("512"|"1024"|"1536") → "1024x1024"로 변환
     * @return 키워드/캐릭터 이미지의 공개 URL
     *
     * ❗ 트랜잭션:
     *   - DB 입력(첨부/기록)을 하나로 묶기 위해 @Transactional 사용
     *   - 파일 IO는 트랜잭션 대상이 아니므로, 실패 시 예외를 던져 호출 측에서 재시도/정리
     */
    @Transactional
    public Result generateTwoWithBase_NoMask(long entryId, String keyword, Path baseCharPng,
                                             boolean useCache, String sizeSq) {

        // 0) 키 존재 체크(부팅 실패를 막기 위해 기본값 ""로 받고, 여기서 수동 검증)
        if (openAiApiKey == null || openAiApiKey.isBlank()) {
            throw new IllegalStateException(
                    "OPENAI_API_KEY가 설정되지 않았습니다. " +
                            "Run/Debug 환경변수 또는 OS 환경변수로 OPENAI_API_KEY를 설정하세요."
            );
        }

        try {
            // 1) 분석/사용자 컨텍스트 조회 (분석이 먼저 완료되어 있어야 이미지 기록 가능)
            var ctxOpt = imageDbRepo.findContext(entryId);
            if (ctxOpt.isEmpty()) {
                // 필요 시 여기서 DiaryAnalysisService.process(entryId) 호출을 붙여도 되지만,
                // 지금은 정책상 "분석 먼저"를 강제한다.
                throw new IllegalStateException(
                        "해당 일기(entry_id=" + entryId + ")의 diary_analysis가 존재하지 않습니다. " +
                                "이미지 생성 전에 일기 분석을 먼저 실행하세요."
                );
            }
            var ctx = ctxOpt.get();

            // 2) 저장 폴더/파일명/URL 계산
            Path dir = Path.of(mediaRootDir).toAbsolutePath();
            Files.createDirectories(dir); // 폴더 없으면 생성

            // 파일명은 {entryId}_keyword.png / {entryId}_character.png 로 고정
            String kwName = entryId + "_keyword.png";
            String chName = entryId + "_character.png";

            Path kwPath = dir.resolve(kwName);
            Path chPath = dir.resolve(chName);

            // 공개 URL은 /media/** 로 매핑(WebConfig)
            String kwUrl = mediaUrlPrefix + kwName;
            String chUrl = mediaUrlPrefix + chName;

            // 3) 캐시 여부 판단(두 파일이 모두 존재해야 캐시 인정)
            boolean cacheExists = Files.exists(kwPath) && Files.exists(chPath);

            // 해상도 "1024" → "1024x1024"
            String sz = (sizeSq == null || sizeSq.isBlank()) ? "1024" : sizeSq;
            String sizeStr = sz + "x" + sz;

            // 4) 캐시 미사용 또는 캐시 미존재 → OpenAI 호출
            if (!useCache || !cacheExists) {
                // (1) 키워드 일러스트 생성
                String promptKeyword = """
                    키워드 '%s'를 직관적으로 표현한 미니멀 일러스트.
                    앱 UI용으로 단순/선명하고, 과한 배경은 지양한다.
                    """.formatted(keyword);
                byte[] keywordPng = requestImageGenerate(promptKeyword, sizeStr);

                // (2) 캐릭터 액션(무마스크) 생성
                String actionPrompt = """
                    동일한 캐릭터(너구리)의 외형을 유지하면서 '%s'를 하는 장면.
                    얼굴 무늬/체형/털 색은 유지하고, 소품/포즈만 추가한다. 배경은 단순하게.
                    """.formatted(keyword);
                byte[] characterPng = requestImageEdit_NoMask(actionPrompt, baseCharPng, sizeStr);

                // (3) 파일 저장(덮어씀)
                try (OutputStream os = Files.newOutputStream(
                        kwPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                    os.write(keywordPng);
                }
                try (OutputStream os = Files.newOutputStream(
                        chPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                    os.write(characterPng);
                }
            }

            // 5) DB 기록
            // 5-1) diary_attachments에 두 URL을 upsert(표시 순서: 키워드 10, 캐릭터 20)
            imageDbRepo.upsertAttachment(entryId, kwUrl, 10);
            imageDbRepo.upsertAttachment(entryId, chUrl, 20);

            // 5-2) 분석 기준 기록(중복 방지 삽입)
            imageDbRepo.insertKeywordImageIfAbsent(ctx.analysisId(), ctx.userId());
            imageDbRepo.insertCharacterImageIfAbsent(ctx.analysisId(), ctx.userId());

            // 6) 로그 + 결과 반환
            System.out.println("[ImageGenService] saved → " + kwPath + " / " + chPath);
            return new Result(kwUrl, chUrl);

        } catch (Exception e) {
            // 파일과 DB의 완벽한 원자성은 보장할 수 없으므로,
            // 필요시 여기에서 일부 보상 로직(부분 파일 삭제 등)을 추가할 수 있다.
            throw new RuntimeException("이미지 생성 실패: " + e.getMessage(), e);
        }
    }

    // ======== OpenAI 호출 유틸(텍스트→이미지) ========

    /**
     * /v1/images/generations 호출
     *
     * @param prompt  프롬프트
     * @param size    "1024x1024" 형태
     * @return PNG 바이트
     * @throws Exception 오류 시 예외
     */
    private byte[] requestImageGenerate(String prompt, String size) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", "gpt-image-1");
        body.put("prompt", prompt);
        body.put("size", size); // 예: "1024x1024"
        body.put("n", 1);

        String json = om.writeValueAsString(body);

        HttpRequest req = HttpRequest.newBuilder(URI.create(GEN_ENDPOINT))
                .header("Authorization", "Bearer " + openAiApiKey)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() / 100 != 2) {
            // OpenAI 응답은 body에 상세 메시지를 담는다.
            throw new IllegalStateException("OpenAI Generate " + res.statusCode() + ": " + res.body());
        }

        // data[0].b64_json 또는 data[0].url 둘 중 하나가 내려올 수 있다.
        JsonNode data0 = om.readTree(res.body()).path("data").get(0);

        // ① b64_json 우선
        String b64 = (data0 != null && data0.hasNonNull("b64_json")) ? data0.get("b64_json").asText() : null;
        if (b64 != null && !b64.isBlank()) return Base64.getDecoder().decode(b64);

        // ② url fallback
        String url = (data0 != null && data0.hasNonNull("url")) ? data0.get("url").asText() : null;
        if (url != null && !url.isBlank()) {
            HttpRequest get = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(60))
                    .build();
            HttpResponse<byte[]> img = http.send(get, HttpResponse.BodyHandlers.ofByteArray());
            if (img.statusCode() / 100 != 2) {
                throw new IllegalStateException("Fetch image " + img.statusCode());
            }
            return img.body();
        }

        throw new IllegalStateException("Unknown generate response (no b64_json/url)");
    }

    // ======== OpenAI 호출 유틸(편집: 무마스크) ========

    /**
     * /v1/images/edits 호출 (마스크 없이, 기본 캐릭터 PNG를 image[]로 업로드)
     *
     * @param prompt   프롬프트(캐릭터 동작/소품 설명)
     * @param imagePng 기본 캐릭터 PNG 경로
     * @param size     "1024x1024" 형태
     * @return PNG 바이트
     * @throws Exception 오류 시 예외
     */
    private byte[] requestImageEdit_NoMask(String prompt, Path imagePng, String size) throws Exception {
        Multipart mp = new Multipart("----JavaBoundary" + UUID.randomUUID());
        mp.addText("model", "gpt-image-1");
        mp.addText("prompt", prompt);
        mp.addText("size", size);
        mp.addText("n", "1");
        // response_format / background 는 일부 배포 미지원 → 사용 안 함
        // 업로드 필드명은 image[] (호환성 좋음)
        mp.addFile("image[]", imagePng, "image/png");

        HttpRequest req = HttpRequest.newBuilder(URI.create(EDIT_ENDPOINT))
                .header("Authorization", "Bearer " + openAiApiKey)
                .header("Content-Type", mp.contentType())
                .timeout(Duration.ofSeconds(90))
                .POST(mp.publisher())
                .build();

        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() / 100 != 2) {
            throw new IllegalStateException("OpenAI Edit " + res.statusCode() + ": " + res.body());
        }

        JsonNode data0 = new ObjectMapper().readTree(res.body()).path("data").get(0);

        // ① b64_json 우선
        String b64 = (data0 != null && data0.hasNonNull("b64_json")) ? data0.get("b64_json").asText() : null;
        if (b64 != null && !b64.isBlank()) return Base64.getDecoder().decode(b64);

        // ② url fallback
        String url = (data0 != null && data0.hasNonNull("url")) ? data0.get("url").asText() : null;
        if (url != null && !url.isBlank()) {
            HttpRequest get = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(60))
                    .build();
            HttpResponse<byte[]> img = http.send(get, HttpResponse.BodyHandlers.ofByteArray());
            if (img.statusCode() / 100 != 2) {
                throw new IllegalStateException("Fetch image " + img.statusCode());
            }
            return img.body();
        }

        throw new IllegalStateException("Unknown edit response (no b64_json/url)");
    }

    // ======== 경량 multipart 빌더 ========

    /**
     * 간단한 multipart/form-data 빌더.
     * HttpRequest.BodyPublisher.ofByteArrays(...)를 이용해 직접 본문 생성.
     */
    private static class Multipart {
        private final String boundary;
        private final List<byte[]> parts = new ArrayList<>();

        Multipart(String boundary) { this.boundary = boundary; }

        void addText(String name, String value) {
            String head = "--" + boundary + "\r\n" +
                    "Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n";
            parts.add(head.getBytes(StandardCharsets.UTF_8));
            parts.add(value.getBytes(StandardCharsets.UTF_8));
            parts.add("\r\n".getBytes(StandardCharsets.UTF_8));
        }

        void addFile(String name, Path file, String mime) throws Exception {
            String head = "--" + boundary + "\r\n" +
                    "Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + file.getFileName() + "\"\r\n" +
                    "Content-Type: " + mime + "\r\n\r\n";
            parts.add(head.getBytes(StandardCharsets.UTF_8));
            parts.add(Files.readAllBytes(file));
            parts.add("\r\n".getBytes(StandardCharsets.UTF_8));
        }

        HttpRequest.BodyPublisher publisher() {
            byte[] end = ("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
            var list = new ArrayList<byte[]>(parts);
            list.add(end);
            return HttpRequest.BodyPublishers.ofByteArrays(list);
        }

        String contentType() { return "multipart/form-data; boundary=" + boundary; }
    }
}
