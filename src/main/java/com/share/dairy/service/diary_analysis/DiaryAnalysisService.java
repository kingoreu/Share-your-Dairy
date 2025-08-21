package com.share.dairy.service.diary_analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import java.time.Instant;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class DiaryAnalysisService {

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
        return s.replace("\"","")
                .replace("\u200B","") // zero-width
                .replace("\uFEFF","") // BOM
                .trim();
    }
    private static String env(String key) { return sanitize(System.getenv(key)); }

    private static String envFirst(String... keys) {
        for (String k : keys) {
            String v = env(k);
            if (!isBlank(v)) return v;
        }
        return null;
    }

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

    // ---------- 실행 진입점 (CLI 테스트용) ----------
    public static void main(String[] args) throws Exception {
        log("[OpenAI] key=%s, url=%s, model=%s", mask(OPENAI_API_KEY), OPENAI_URL, OPENAI_MODEL);
        log("[JDBC] url=%s, user=%s", JDBC_URL, JDBC_USER);

        long entryId = (args.length > 0) ? Long.parseLong(args[0]) : 1L;
        new DiaryAnalysisService().process(entryId);
        log("Analyzed entry_id=%d at %s", entryId, Instant.now());
    }

    // ---------- 퍼블릭 API ----------
    /** diary_entries.entry_id를 분석해서 diary_analysis에 upsert */
    public void process(long entryId) throws Exception {
        String content = getDiaryContent(entryId);
        if (isBlank(content)) throw new IllegalArgumentException("일기 내용이 없습니다: entry_id=" + entryId);

        AnalysisResult result = callChatGPT(content);
        saveAnalysis(entryId, result);
    }

    /** (옵션) 이미 가지고 있는 본문으로 바로 분석 */
    public void process(long entryId, String content) throws Exception {
        String text = isBlank(content) ? getDiaryContent(entryId) : content;
        if (isBlank(text)) throw new IllegalArgumentException("일기 내용이 없습니다: entry_id=" + entryId);

        AnalysisResult result = callChatGPT(text);
        saveAnalysis(entryId, result);
    }

    // ---------- DB ----------
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

    private void saveAnalysis(long entryId, AnalysisResult r) throws SQLException {
        final String sql = """
            INSERT INTO diary_analysis (entry_id, summary, happiness_score, analysis_keywords, analyzed_at)
            VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
            ON DUPLICATE KEY UPDATE
              summary = VALUES(summary),
              happiness_score = VALUES(happiness_score),
              analysis_keywords = VALUES(analysis_keywords),
              analyzed_at = CURRENT_TIMESTAMP
            """;
        try (Connection conn = DriverManager.getConnection(JDBC_URL, JDBC_USER, JDBC_PASS);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, entryId);
            ps.setString(2, r.summary);
            ps.setInt(3, r.happinessScore);
            ps.setString(4, r.keyword); // 현재 스키마 TEXT 컬럼(문자열) 저장
            ps.executeUpdate();
        }
    }

    // ---------- OpenAI ----------
    private AnalysisResult callChatGPT(String diaryContent) throws IOException {
        // 프롬프트: JSON만 반환
        String systemPrompt = String.join("\n",
                "너는 일기 분석기다. 'JSON 객체'만 반환해. 다른 글자는 절대 포함하지 마.",
                "{ \"analysis_keywords\": string, \"happiness_score\": number, \"summary\": string }",
                "- analysis_keywords: 일기의 전체 의미를 대표하는 짧은 문구(하나의 문장).",
                "- happiness_score: 1~10 정수 (10=매우 행복, 1=매우 우울).",
                "- summary: 3~5줄 요약 (가능하면 120자 이내)."
        );
        String userPrompt = "일기 내용:\n" + diaryContent + "\nJSON만 반환해.";

        ObjectNode root = MAPPER.createObjectNode();
        root.put("model", OPENAI_MODEL);
        root.put("temperature", 0.2);

        ArrayNode messages = MAPPER.createArrayNode();
        messages.add(MAPPER.createObjectNode().put("role", "system").put("content", systemPrompt));
        messages.add(MAPPER.createObjectNode().put("role", "user").put("content", userPrompt));
        root.set("messages", messages);

        Request request = new Request.Builder()
                .url(OPENAI_URL)
                .addHeader("Authorization", "Bearer " + OPENAI_API_KEY)
                .addHeader("Content-Type", "application/json; charset=utf-8")
                .post(RequestBody.create(root.toString().getBytes(StandardCharsets.UTF_8), JSON))
                .build();

        try (Response resp = HTTP.newCall(request).execute()) {
            String body = (resp.body() != null) ? resp.body().string() : "";
            if (!resp.isSuccessful()) {
                err("OpenAI HTTP %d body=%s", resp.code(), truncate(body, 800));
                throw new IOException("OpenAI API 오류: " + resp.code());
            }

            log("[OpenAI RAW] %s", truncate(body, 800));

            JsonNode apiRoot = MAPPER.readTree(body);
            String content = apiRoot.path("choices").path(0).path("message").path("content").asText("");
            if (isBlank(content)) {
                err("OpenAI content 비어있음. raw=%s", truncate(body, 800));
                throw new IOException("OpenAI content empty");
            }

            // JSON만 추출해 파싱
            String json = extractJsonObject(content);
            JsonNode data = MAPPER.readTree(json);

            String keyword = data.path("analysis_keywords").asText("");
            int score = data.path("happiness_score").asInt(5);
            String summary = data.path("summary").asText("");

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

    // ---------- DTO ----------
    static class AnalysisResult {
        final String keyword;
        final int happinessScore;
        final String summary;
        AnalysisResult(String keyword, int happinessScore, String summary) {
            this.keyword = keyword;
            this.happinessScore = happinessScore;
            this.summary = summary;
        }
    }
}
