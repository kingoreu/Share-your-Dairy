package com.share.dairy.service.diary_analysis;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.*;

import java.io.IOException;
import java.sql.*;
import java.time.Instant;
import java.util.Objects;

@Service 
public class DiaryAnalysisService {

    // ====== ENV 전용 유틸 ======
    private static boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }

    private static String sanitize(String s) {
        if (s == null) return null;
        return s.replace("\"","")
                .replace("\u200B","") // zero-width
                .replace("\uFEFF","") // BOM
                .trim();
    }
    private static String env(String key) { return sanitize(System.getenv(key)); }

    /** 여러 키 중 먼저 설정된 ENV 반환 (없으면 null) */
    private static String envFirst(String... keys) {
        for (String k : keys) {
            String v = env(k);
            if (!isBlank(v)) return v;
        }
        return null;
    }

    /** 필수 ENV가 없으면 예외 */
    private static String requireEnv(String... keys) {
        String v = envFirst(keys);
        if (isBlank(v)) {
            throw new IllegalStateException("필수 환경변수 미설정: " + String.join(" 또는 ", keys));
        }
        return v;
    }

    private static String mask(String s) {
        if (s == null || s.length() < 12) return String.valueOf(s);
        return s.substring(0, 8) + "…" + s.substring(s.length() - 4);
    }

    // ====== OpenAI 설정 (ENV 우선, 없으면 스킵) ======
    private static String getApiKey() {
        String v = envFirst("OPENAI_API_KEY");
        if (isBlank(v)) v = System.getProperty("OPENAI_API_KEY");
        return sanitize(v);
    }
    private static final String OPENAI_URL   = Objects.requireNonNullElse(
            env("OPENAI_API_URL"),
            "https://api.openai.com/v1/chat/completions");
    private static final String OPENAI_MODEL = Objects.requireNonNullElse(
            env("OPENAI_API_MODEL"),
            "gpt-3.5-turbo"); // 필요시 gpt-4o-mini 등으로 교체

    // ====== DB 설정 (ENV만 사용, Spring 호환 키도 지원) ======
    private static final String JDBC_URL  = Objects.requireNonNullElse(
            envFirst("JDBC_URL", "SPRING_DATASOURCE_URL"),
            "jdbc:mysql://113.198.238.119:3306/dairy?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Seoul");
    private static final String JDBC_USER = Objects.requireNonNullElse(
            envFirst("JDBC_USER", "SPRING_DATASOURCE_USERNAME"),
            "root");
    private static final String JDBC_PASS = Objects.requireNonNullElse(
            envFirst("JDBC_PASS", "SPRING_DATASOURCE_PASSWORD"),
            "sohyun");

    private static final OkHttpClient HTTP = new OkHttpClient();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ---------- 실행 진입점 (테스트 편의) ----------
    public static void main(String[] args) throws Exception {
        // 디버깅용 마스킹 로그(키 전체는 절대 출력 금지)
        System.out.println("[OpenAI] key=" + mask(getApiKey()));
        System.out.println("[OpenAI] url=" + OPENAI_URL + ", model=" + OPENAI_MODEL);
        System.out.println("[JDBC] url=" + JDBC_URL + ", user=" + JDBC_USER);

        long entryId = (args.length > 0) ? Long.parseLong(args[0]) : 1L;
        new DiaryAnalysisService().process(entryId);
        System.out.println("Analyzed entry_id=" + entryId + " at " + Instant.now());
    }

    /** diary_entries.entry_id를 분석해서 diary_analysis에 upsert */
    public void process(long entryId) throws Exception {
        System.out.println("[DiaryAnalysisService] Using JDBC_URL=" + JDBC_URL + ", USER=" + JDBC_USER);

        String content = getDiaryContent(entryId);
        if (isBlank(content)) {
            throw new IllegalArgumentException("일기 내용이 없습니다: entry_id=" + entryId);
        }

        String apiKey = getApiKey();
        if (isBlank(apiKey)) {
            System.out.println("[DiaryAnalysis] OPENAI_API_KEY 없음 → 분석 스킵 (entry_id=" + entryId + ")");
            return; // 키 없으면 조용히 건너뜀
        }

        AnalysisResult result = callChatGPT(content, apiKey);
        saveAnalysis(entryId, result);
    }

    // ---------- DB ----------
    private String getDiaryContent(long entryId) throws SQLException {
        String sql = "SELECT diary_content FROM diary_entries WHERE entry_id = ?";
        try (java.sql.Connection conn = DriverManager.getConnection(JDBC_URL, JDBC_USER, JDBC_PASS);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, entryId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    private void saveAnalysis(long entryId, AnalysisResult r) throws SQLException {
        // diary_analysis.entry_id UNIQUE 필요
        String sql = """
            INSERT INTO diary_analysis (entry_id, summary, happiness_score, analysis_keywords, analyzed_at)
            VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
            ON DUPLICATE KEY UPDATE
              summary = VALUES(summary),
              happiness_score = VALUES(happiness_score),
              analysis_keywords = VALUES(analysis_keywords),
              analyzed_at = CURRENT_TIMESTAMP
            """;
        try (java.sql.Connection conn = DriverManager.getConnection(JDBC_URL, JDBC_USER, JDBC_PASS);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, entryId);
            ps.setString(2, r.summary);
            ps.setInt(3, r.happinessScore);
            ps.setString(4, r.keyword);
            ps.executeUpdate();
        }
    }

    // ---------- OpenAI ----------
    private AnalysisResult callChatGPT(String diaryContent, String apiKey) throws IOException {
        String systemPrompt =
                "너는 일기 분석기다. 다음 JSON 형식으로만 응답해.\n" +
                "{ \"analysis_keywords\": string, \"happiness_score\": number, \"summary\": string }\n" +
                "analysis_keywords: 일기의 전체 의미를 대표하는 하나의 짧은 문구(1개 문장).\n" +
                "happiness_score: 1~10 정수 (10 행복, 1 우울).\n" +
                "summary: 3~5줄 요약.";

        String userPrompt = "일기 내용:\n" + diaryContent + "\n\nJSON만 반환해.";

        ObjectNode root = MAPPER.createObjectNode();
        root.put("model", OPENAI_MODEL);
        root.set("response_format", MAPPER.createObjectNode().put("type", "json_object"));
        ArrayNode messages = MAPPER.createArrayNode();
        messages.add(MAPPER.createObjectNode().put("role", "system").put("content", systemPrompt));
        messages.add(MAPPER.createObjectNode().put("role", "user").put("content", userPrompt));
        root.set("messages", messages);

        Request request = new Request.Builder()
                .url(OPENAI_URL)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(root.toString(), MediaType.get("application/json")))
                .build();

        try (Response resp = HTTP.newCall(request).execute()) {
            if (!resp.isSuccessful()) {
                String err = (resp.body() != null) ? resp.body().string() : "";
                throw new IOException("OpenAI API 오류: " + resp.code() + " - " + err);
            }
            String body = Objects.requireNonNull(resp.body()).string();
            JsonNode apiRoot = MAPPER.readTree(body);
            String content = apiRoot.path("choices").get(0).path("message").path("content").asText();
            JsonNode data = MAPPER.readTree(content);

            String keyword = data.path("analysis_keywords").asText("");
            int score = data.path("happiness_score").asInt(5);
            String summary = data.path("summary").asText("");

            if (score < 1) score = 1;
            if (score > 10) score = 10;

            return new AnalysisResult(keyword, score, summary);
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
