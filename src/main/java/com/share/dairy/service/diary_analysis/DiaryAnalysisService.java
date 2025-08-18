package com.share.dairy.service.diary_analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.*;

import java.io.IOException;
import java.io.InputStream;
import java.sql.*;
import java.time.Instant;
import java.util.Properties;

public class DiaryAnalysisService {

    // ---------- 설정 로딩 (application.properties -> ENV fallback) ----------
    private static final Properties APP_PROPS = loadProps();
    private static final String OPENAI_API_KEY =
            firstNonBlank(APP_PROPS.getProperty("openai.api.key"), System.getenv("OPENAI_API_KEY"));
    private static final String OPENAI_URL =
            firstNonBlank(APP_PROPS.getProperty("openai.api.url"), "https://api.openai.com/v1/chat/completions");
    private static final String OPENAI_MODEL =
            firstNonBlank(APP_PROPS.getProperty("openai.api.model"), "gpt-4o-mini");

    // DB 설정도 properties에 있으면 우선 사용 (없으면 기존 상수 유지)
    private static final String JDBC_URL =
            firstNonBlank(APP_PROPS.getProperty("spring.datasource.url"),
                    "jdbc:mysql://localhost:3306/dairy?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Seoul");
    private static final String JDBC_USER =
            firstNonBlank(APP_PROPS.getProperty("spring.datasource.username"), "root");
    private static final String JDBC_PASS =
            firstNonBlank(APP_PROPS.getProperty("spring.datasource.password"), "1234");

    private static final OkHttpClient HTTP = new OkHttpClient();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static Properties loadProps() {
        Properties p = new Properties();
        try (InputStream in = DiaryAnalysisService.class.getClassLoader()
                .getResourceAsStream("application.properties")) {
            if (in != null) p.load(in);
        } catch (IOException ignored) {}
        return p;
    }

    private static String firstNonBlank(String a, String b) {
        return (a != null && !a.isBlank()) ? a : (b != null && !b.isBlank() ? b : null);
    }

    // ---------- 실행 진입점 (테스트 편의) ----------
    public static void main(String[] args) throws Exception {
        long entryId = (args.length > 0) ? Long.parseLong(args[0]) : 1L; // mvn exec ... -Dexec.args="3"
        new DiaryAnalysisService().process(entryId);
        System.out.println("Analyzed entry_id=" + entryId + " at " + Instant.now());
    }

    /** diary_entries.entry_id를 분석해서 diary_analysis에 upsert */
    public void process(long entryId) throws Exception {
        if (OPENAI_API_KEY == null || OPENAI_API_KEY.isBlank()) {
            throw new IllegalStateException("OpenAI API 키가 없습니다. application.properties의 openai.api.key 또는 환경변수 OPENAI_API_KEY를 설정하세요.");
        }

        String content = getDiaryContent(entryId);
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("일기 내용이 없습니다: entry_id=" + entryId);
        }

        AnalysisResult result = callChatGPT(content);
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
        // ※ diary_analysis.entry_id 는 UNIQUE 여야 ON DUPLICATE KEY 가 동작합니다.
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
    private AnalysisResult callChatGPT(String diaryContent) throws IOException {
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
                .addHeader("Authorization", "Bearer " + OPENAI_API_KEY)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(root.toString(), MediaType.get("application/json")))
                .build();

        try (Response resp = HTTP.newCall(request).execute()) {
            if (!resp.isSuccessful()) {
                String err = (resp.body() != null) ? resp.body().string() : "";
                throw new IOException("OpenAI API 오류: " + resp.code() + " - " + err);
            }
            String body = resp.body().string();
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
