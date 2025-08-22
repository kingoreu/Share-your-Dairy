package com.share.dairy.service.diary_analysis;

/*
 * ============================================================
 * DiaryAnalysisService
 * ------------------------------------------------------------
 * 역할
 *  - diary_entries(entry_id)에서 일기 본문을 읽는다.
 *  - OpenAI Chat Completions API에 본문을 보내서
 *    {analysis_keywords, happiness_score, summary} 를 JSON으로 받는다.
 *  - diary_analysis 테이블에 UPSERT 한다.
 *
 * 환경설정(우선순위: OS env > JVM -D 프로퍼티 > .env 파일)
 *  OPENAI_API_KEY   (필수)
 *  OPENAI_API_URL   (선택, 기본: https://api.openai.com/v1/chat/completions)
 *  OPENAI_API_MODEL (선택, 기본: gpt-3.5-turbo)
 *  JDBC_URL         (선택, 기본: jdbc:mysql://localhost:3306/dairy...)
 *  JDBC_USER        (선택, 기본: root)
 *  JDBC_PASS        (선택, 기본: 1234)
 *
 * .env 사용법
 *  - 프로젝트 루트에 .env 또는 .env.local 생성
 *  - 예)
 *      OPENAI_API_KEY=sk-xxxxx
 *      JDBC_URL=jdbc:mysql://127.0.0.1:3306/dairy?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Seoul&characterEncoding=UTF-8
 *      JDBC_USER=root
 *      JDBC_PASS=1234
 *
 *  - pom.xml 의존성 필요:
 *      <dependency>
 *        <groupId>io.github.cdimascio</groupId>
 *        <artifactId>dotenv-java</artifactId>
 *        <version>3.0.0</version>
 *      </dependency>
 *
 * 보안
 *  - .env, .env.* 는 .gitignore에 반드시 제외!
 *  - 키는 부팅 시가 아닌 "API 호출 시점"에만 검사(없으면 그때만 예외)
 *
 * 테스트
 *  - main() 에서 entryId 인자로 실행 가능:
 *      java ... DiaryAnalysisService 1
 * ============================================================
 */

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.share.dairy.util.DBConnection;
import io.github.cdimascio.dotenv.Dotenv;    // ✅ .env 로더
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.sql.Connection;
import java.time.Instant;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class DiaryAnalysisService {

    // ========================= 공통 유틸 =========================

    /** .env 로더: 파일이 없어도 조용히 무시 (.env or .env.local 원하는 이름으로 지정) */
    private static final Dotenv DOTENV = Dotenv.configure()
            .ignoreIfMissing()
            .filename(".env")   // .env 사용 시 ".env" 로 바꿔도 됨
            .load();

    // ====== 간단 로깅 ======
    private static void log(String fmt, Object... args) {
        System.out.println("[DiaryAnalysis] " + String.format(fmt, args));
    }
    private static void err(String fmt, Object... args) {
        System.err.println("[DiaryAnalysis][ERROR] " + String.format(fmt, args));
    }

    // ====== 문자열 유틸 ======
    private static boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }

    private static String sanitize(String s) {
        if (s == null) return null;
        // IDE/OS에 따라 섞일 수 있는 BOM/제로폭 문자 제거
        return s.replace("\"","")
                .replace("\u200B","") // zero-width
                .replace("\uFEFF","") // BOM
                .trim();
    }

    /**
     * 키를 읽는다(우선순위: OS 환경변수 → JVM 시스템 프로퍼티(-DKEY=VAL) → .env 파일)
     * 없으면 null 반환
     */
    private static String env(String key) {
        String v = System.getenv(key);      // 1) OS 환경변수
        if (!isBlank(v)) return sanitize(v);
        v = System.getProperty(key);        // 2) JVM -D 프로퍼티
        if (!isBlank(v)) return sanitize(v);
        v = DOTENV.get(key);                // 3) .env/.env.local
        return sanitize(v);
    }

    /** 여러 키 중 먼저 설정된 값을 반환(없으면 null) */
    private static String envFirst(String... keys) {
        for (String k : keys) {
            String v = env(k);
            if (!isBlank(v)) return v;
        }
        return null;
    }

    /** 필수 값 검사(호출 시점에만 검사해서 부팅 크래시 방지) */
    private static String requireEnvOnCall(String... keys) {
        for (String k : keys) {
            String v = env(k);
            if (!isBlank(v)) return v;
        }
        throw new IllegalStateException("필수 환경변수 미설정: " + String.join(" 또는 ", keys));
    }

    /** 키 마스킹(로그 안전 출력) */
    private static String requireEnv(String... keys) {
        String v = envFirst(keys);
        if (isBlank(v)) throw new IllegalStateException("필수 환경변수 미설정: " + String.join(" 또는 ", keys));
        return v;
    }

    private static String mask(String s) {
        if (s == null || s.length() < 12) return String.valueOf(s);
        return s.substring(0, 8) + "…" + s.substring(s.length() - 4);
    }

    private static String oneLine(String s) {
        if (s == null) return "";
        return s.replace("\r", " ").replace("\n", " ").trim();
    }

    private static String truncate(String s, int n) {
        if (s == null) return "";
        return (s.length() > n) ? s.substring(0, n) + "…" : s;
    }

    /** 모델이 코드블록/문장으로 감싸서 보낼 때, 첫 JSON 객체만 추출 */
    private static String extractJsonObject(String s) {
        if (s == null) return "{}";
        String cleaned = s.replace("```json", "```").replace("```", "").trim();
        int a = cleaned.indexOf('{');
        int b = cleaned.lastIndexOf('}');
        return (a >= 0 && b > a) ? cleaned.substring(a, b + 1) : "{}";
    }

    // ========================= 설정값들 =========================

    /** OpenAI 키는 정적 상수로 고정하지 않고, 매 호출 시점에 확인 */
    private static String requireOpenAiKey() {
        return requireEnvOnCall("OPENAI_API_KEY");
    }

    /** OpenAI API 엔드포인트(기본값 제공 → 부팅 시 안전) */
    private static final String OPENAI_URL = Objects.requireNonNullElse(
            env("OPENAI_API_URL"),
            "https://api.openai.com/v1/chat/completions"
    );

    /** OpenAI 모델(기본값 제공) */
    private static final String OPENAI_MODEL = Objects.requireNonNullElse(
            env("OPENAI_API_MODEL"),
            "gpt-3.5-turbo" // 필요시 gpt-4o-mini 등으로 교체 가능
    );

    // ➕ [추가] 커넥션은 항상 DBConnection을 통해 연다(= application.properties 사용)
    private static Connection openCon() throws SQLException {
        return DBConnection.getConnection();
    }

    // ========================= HTTP/JSON =========================
    // ====== OpenAI 설정 (ENV만 사용) ======
    private static final String OPENAI_API_KEY = requireEnv("OPENAI_API_KEY");
    private static final String OPENAI_URL   = Objects.requireNonNullElse(
            env("OPENAI_API_URL"),
            "https://api.openai.com/v1/chat/completions");
    private static final String OPENAI_MODEL = Objects.requireNonNullElse(
            env("OPENAI_API_MODEL"),
            "gpt-4o-mini");

    // ====== DB 설정 (ENV만 사용, Spring 호환 키도 지원) ======
    private static final String JDBC_URL  = Objects.requireNonNullElse(
            envFirst("JDBC_URL", "SPRING_DATASOURCE_URL"),
            "jdbc:mysql://127.0.0.1:3306/dairy?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Seoul&characterEncoding=UTF-8");
    private static final String JDBC_USER = Objects.requireNonNullElse(
            envFirst("JDBC_USER", "SPRING_DATASOURCE_USERNAME"),
            "root");
    private static final String JDBC_PASS = Objects.requireNonNullElse(
            envFirst("JDBC_PASS", "SPRING_DATASOURCE_PASSWORD"),
            "1234");

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .callTimeout(Duration.ofSeconds(45))
            .connectTimeout(Duration.ofSeconds(15))
            .readTimeout(Duration.ofSeconds(45))
            .writeTimeout(Duration.ofSeconds(45))
            .build();

    static {
        try { Class.forName("com.mysql.cj.jdbc.Driver"); } catch (Throwable ignored) {}
    }

    // ========================= 테스트 진입점 =========================

    public static void main(String[] args) throws Exception {
        // 부팅 시 크래시 방지를 위해 키는 try-catch로 마스킹만 출력
        String maskedKey;
        try {
            maskedKey = mask(requireOpenAiKey());
        } catch (IllegalStateException e) {
            maskedKey = "(미설정)";
        }
        System.out.println("[OpenAI] key=" + maskedKey);
        System.out.println("[OpenAI] url=" + OPENAI_URL + ", model=" + OPENAI_MODEL);

        long entryId = (args.length > 0) ? Long.parseLong(args[0]) : 1L;
        new DiaryAnalysisService().process(entryId);
        log("Analyzed entry_id=%d at %s", entryId, Instant.now());
    }

    // ---------- 퍼블릭 API ----------
    // ========================= 퍼블릭 API =========================

    /**
     * 지정한 entryId의 일기를 읽어 OpenAI로 분석하고,
     * diary_analysis 테이블에 UPSERT 한다.
     */
    public void process(long entryId) throws Exception {
        // 1) 원문 가져오기
        String content = getDiaryContent(entryId);
        if (isBlank(content)) throw new IllegalArgumentException("일기 내용이 없습니다: entry_id=" + entryId);

        // 2) GPT 분석 호출
        AnalysisResult result = callChatGPT(content);
        saveAnalysis(entryId, result);
    }

    // ========================= DB I/O =========================

    /** diary_entries에서 본문을 조회 */
    private String getDiaryContent(long entryId) throws SQLException {
        final String sql = "SELECT diary_content FROM diary_entries WHERE entry_id = ?";
        try (Connection conn = DriverManager.getConnection(JDBC_URL, JDBC_USER, JDBC_PASS);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, entryId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    /** diary_analysis에 UPSERT(analysis_id는 AUTO_INCREMENT, entry_id는 UNIQUE 가정) */
    private void saveAnalysis(long entryId, AnalysisResult r) throws SQLException {
        String sql = """
            INSERT INTO diary_analysis (entry_id, summary, happiness_score, analysis_keywords, analyzed_at)
            VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
            ON DUPLICATE KEY UPDATE
              summary = VALUES(summary),
              happiness_score = VALUES(happiness_score),
              analysis_keywords = VALUES(analysis_keywords),
              analyzed_at = CURRENT_TIMESTAMP
            """;
        try (Connection conn = openCon();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, entryId);
            ps.setString(2, r.summary);
            ps.setInt(3, r.happinessScore);
            ps.setString(4, r.keyword); // 현재 스키마 TEXT 컬럼(문자열) 저장
            ps.executeUpdate();
        }
    }

    // ========================= OpenAI 호출 =========================

    /**
     * Chat Completions 호출
     * - 응답은 {"choices":[{"message":{"content":"{...json...}"}}]} 형태
     * - response_format: json_object 로 요청해서 content 안에 JSON만 오도록 유도
     */
    private AnalysisResult callChatGPT(String diaryContent) throws IOException {
        // 시스템 메시지: 모델에게 출력 포맷을 강하게 지정
        String systemPrompt =
                "너는 일기 분석기다. 다음 JSON 형식으로만 응답해.\n" +
                        "{ \"analysis_keywords\": string, \"happiness_score\": number, \"summary\": string }\n" +
                        "analysis_keywords: 일기의 전체 의미를 대표하는 하나의 짧은 문구(1개 문장).\n" +
                        "happiness_score: 1~10 정수 (10 행복, 1 우울).\n" +
                        "summary: 3~5줄 요약.";

        // 유저 메시지: 실제 본문
        String userPrompt = "일기 내용:\n" + diaryContent + "\n\nJSON만 반환해.";

        // 요청 바디(JSON)
        ObjectNode root = MAPPER.createObjectNode();
        root.put("model", OPENAI_MODEL);
        root.put("temperature", 0.2); // JSON만 받도록

        ArrayNode messages = MAPPER.createArrayNode();
        messages.add(MAPPER.createObjectNode().put("role", "system").put("content", systemPrompt));
        messages.add(MAPPER.createObjectNode().put("role", "user").put("content", userPrompt));
        root.set("messages", messages);

        // HTTP 요청 구성
        Request request = new Request.Builder()
                .url(OPENAI_URL)
                .addHeader("Authorization", "Bearer " + requireOpenAiKey())   // ← 이 시점에만 키 검사
                .addHeader("Content-Type", "application/json; charset=utf-8")
                .post(RequestBody.create(root.toString().getBytes(StandardCharsets.UTF_8), JSON))
                .build();

        // 호출/에러 처리
        try (Response resp = HTTP.newCall(request).execute()) {
            String body = (resp.body() != null) ? resp.body().string() : "";
            if (!resp.isSuccessful()) {
                err("OpenAI HTTP %d body=%s", resp.code(), truncate(body, 800));
                throw new IOException("OpenAI API 오류: " + resp.code());
            }

            log("[OpenAI RAW] %s", truncate(body, 800));


            // 응답 파싱
            JsonNode apiRoot = MAPPER.readTree(body);
            String content = apiRoot.path("choices").path(0).path("message").path("content").asText("");
            if (isBlank(content)) {
                err("OpenAI content 비어있음. raw=%s", truncate(body, 800));
                throw new IOException("OpenAI content empty");
            }

            // JSON만 추출해 파싱
            String json = extractJsonObject(content);

            // 모델이 준 문자열(content) 자체가 JSON이므로 다시 파싱
            JsonNode data = MAPPER.readTree(json);

            String keyword = data.path("analysis_keywords").asText("");
            int score = data.path("happiness_score").asInt(5);
            String summary = data.path("summary").asText("");

            // 방어: 스코어 범위 보정
            if (score < 1) score = 1;
            if (score > 10) score = 10;

            summary = oneLine(summary);
            if (summary.length() > 120) summary = summary.substring(0, 120) + "…";

            return new AnalysisResult(keyword, score, summary);
        } catch (IOException e) {
            err("OpenAI 호출/파싱 중 예외: %s", e.getMessage());
            throw e;
        }
    }

    // ========================= DTO =========================

    /** 분석 결과 보관용 DTO */
    static class AnalysisResult {
        final String keyword;        // 분석 키워드(문장/단어)
        final int    happinessScore; // 1~10
        final String summary;        // 3~5줄 요약

        AnalysisResult(String keyword, int happinessScore, String summary) {
            this.keyword = keyword;
            this.happinessScore = happinessScore;
            this.summary = summary;
        }
    }
}
