package com.share.dairy.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

// 예외처리 response 객체 생성
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiErrorResponse {
    private final String error;    // "Invalid Argument", "Conflict", "Database Error" 등
    private final String message;  // 사용자에게 보여줄 메시지
    private final Integer status;  // 400/409/500 등
    private final LocalDateTime timestamp; // 필요 없으면 null 넘길 수도 있음

    public ApiErrorResponse(String error, String message, Integer status) {
        this.error = error;
        this.message = message;
        this.status = status;
        this.timestamp = null; // 안 쓰면 null로: JSON에 안 찍힘
    }

    public String getError() { return error; }
    public String getMessage() { return message; }
    public Integer getStatus() { return status; }
    public LocalDateTime getTimestamp() { return timestamp; }
}
