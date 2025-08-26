package com.share.dairy.api.keyword;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.share.dairy.dto.keyword.keywordImage.ResponseDto;

import java.net.http.*;
import java.net.URI;
import java.util.List;

public class KeywordImageClient {

    private static final String BASE_URL = "http://localhost:8080/api/keyword_images";
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public KeywordImageClient() {
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    // 유저별 이미지 가져오기
    public List<ResponseDto> fetchByUserId(long userId) throws Exception {
        String url = BASE_URL + "?userId=" + userId;

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

        if (res.statusCode() == 200) {
            return objectMapper.readValue(res.body(), new TypeReference<>() {});
        } else {
            throw new RuntimeException("이미지 조회 실패: " + res.statusCode());
        }
    }
}
