package com.share.dairy.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
// 상단 import 정리 (중복 제거/추가)
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.bind.MissingServletRequestParameterException;

import jakarta.validation.ConstraintViolationException;
import org.springframework.validation.BindException;

import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.Map;


// 전역 예외 처리기 코드. 따라서 해당 코드를 사용하므로
// 컨트롤러에서 예외 처리 안해도 됨.
@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalExceptionHandler {

    /* ----------------------------
     * 400: 유효성/바인딩/파싱 에러
     * ---------------------------- */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("IllegalArgument: {}", ex.getMessage());
        return badRequest("Invalid Argument", ex.getMessage());
    }

    // DTO @Valid 실패
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        var first = ex.getBindingResult().getFieldErrors().stream().findFirst();
        String msg = first.map(e -> e.getField() + ": " + e.getDefaultMessage())
                .orElse("요청 값이 올바르지 않습니다.");
        log.warn("Validation failed: {}", msg);
        return badRequest("ValidationError", msg);
    }

    // 쿼리/경로 파라미터 바인딩 실패
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiErrorResponse> handleBind(BindException ex) {
        var first = ex.getBindingResult().getFieldErrors().stream().findFirst();
        String msg = first.map(e -> e.getField() + ": " + e.getDefaultMessage())
                .orElse("요청 파라미터가 올바르지 않습니다.");
        log.warn("Bind failed: {}", msg);
        return badRequest("BindError", msg);
    }

@ExceptionHandler(MissingServletRequestParameterException.class)
public ResponseEntity<ApiErrorResponse> handleMissingParam(MissingServletRequestParameterException ex,
                                                           HttpServletRequest req) {
    String uri = req.getRequestURI();
    String qs  = req.getQueryString();
    if (qs != null && !qs.isBlank()) uri += "?" + qs;

    log.warn("Missing request parameter: {} (required type {}), uri={}",
            ex.getParameterName(), ex.getParameterType(), uri);

    String msg = ex.getParameterName() + " 파라미터가 필요합니다.";
    return badRequest("MissingParameter", msg);
}

    // 타입 불일치(예: id=abc 를 long에 바인딩)
    
@ExceptionHandler(MethodArgumentTypeMismatchException.class)
public ResponseEntity<ApiErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                           HttpServletRequest req) {
    // 파라미터 이름/값/기대 타입
    String paramName = ex.getName();
    Object rawValue  = ex.getValue();
    String expected  = (ex.getRequiredType() != null) ? ex.getRequiredType().getSimpleName() : "unknown";

    // 전체 요청 URI(+쿼리)
    String uri = req.getRequestURI();
    String qs  = req.getQueryString();
    if (qs != null && !qs.isBlank()) uri += "?" + qs;

    // 로그에 '정확히 무엇이 문제였는지'를 남김
    log.warn("Type mismatch: {}={} (expected {}), uri={}", paramName, rawValue, expected, uri);

    // 응답 메시지도 조금 더 친절하게
    String msg = paramName + "의 타입이 올바르지 않습니다. (값=" + rawValue + ", 기대타입=" + expected + ")";
    return badRequest("TypeMismatch", msg);
}

    // JSON 파싱 실패
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleNotReadable(HttpMessageNotReadableException ex) {
        log.warn("JSON parse error: {}", ex.getMessage());
        return badRequest("JsonParseError", "요청 본문을 해석할 수 없습니다.");
    }

    // javax/jakarta Validator (@Validated on params)
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        String msg = ex.getConstraintViolations().stream()
                .findFirst().map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .orElse("요청 값이 제약조건을 위반했습니다.");
        log.warn("Constraint violation: {}", msg);
        return badRequest("ConstraintViolation", msg);
    }

    /* --------------------------------
     * 409: 중복키/무결성 충돌 (정교화)
     * -------------------------------- */
    // 스프링 추상화: DuplicateKey (보통 UNIQUE 충돌)
    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ApiErrorResponse> handleDuplicateKey(DuplicateKeyException ex) {
        log.info("DuplicateKey: {}", ex.getMessage());
        String field = extractDuplicateField(ex.getMessage());
        String msg = field != null ? field + " 값이 이미 사용 중입니다." : "중복 데이터입니다.";
        return conflict("Conflict", msg);
    }

    // JDBC 드라이버 직통 무결성 충돌 (예: MySQL 1062, 1452 등)
    @ExceptionHandler(SQLIntegrityConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleSqlIntegrity(SQLIntegrityConstraintViolationException ex) {
        log.info("SQLIntegrityConstraintViolation (state={}, code={}): {}",
                ex.getSQLState(), ex.getErrorCode(), ex.getMessage());

        // MySQL 대표 에러코드 분기
        switch (ex.getErrorCode()) {
            case 1062: { // Duplicate entry
                String field = extractDuplicateField(ex.getMessage());
                String msg = field != null ? field + " 값이 이미 사용 중입니다." : "중복 데이터입니다.";
                return conflict("Conflict", msg);
            }
            case 1452: // Cannot add or update a child row: a foreign key constraint fails
                return conflict("ForeignKeyViolation", "연관 데이터가 존재하지 않습니다.");
            case 1048: // Column 'X' cannot be null
                return badRequest("NotNullViolation", "필수 값이 누락되었습니다.");
            default:
                return conflict("IntegrityViolation", "데이터 제약 조건을 위반했습니다.");
        }
    }

    // 스프링의 광의 무결성 위반 (FK/NOT NULL/UNIQUE 모두 포함 가능)
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.info("DataIntegrityViolation: {}", ex.getMessage());
        // 루트가 SQLException이면 코드로 세분화 시도
        var root = getSqlCause(ex);
        if (root != null) {
            return handleSqlIntegrity(root);
        }
        return conflict("Conflict", "데이터 제약 조건을 위반했습니다.");
    }

    /* ----------------------------
     * 그 외 SQL → 서버 에러로 묶기
     * ---------------------------- */
    @ExceptionHandler(SQLException.class)
    public ResponseEntity<ApiErrorResponse> handleSql(SQLException ex) {
        log.error("SQL error (state={}, code={}): {}", ex.getSQLState(), ex.getErrorCode(), ex.getMessage(), ex);
        return serverError("Database Error", "데이터베이스 오류가 발생했습니다.");
    }

    /* ----------------------------
     * 500: 마지막 안전망
     * ---------------------------- */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGeneric(Exception ex) {
        log.error("Unhandled error", ex);
        return serverError("Internal Server Error", "서버 오류가 발생했습니다.");
    }

    /* ====================
     * 유틸 메서드
     * ==================== */
    private ResponseEntity<ApiErrorResponse> badRequest(String code, String msg) {
        var body = new ApiErrorResponse(code, msg, HttpStatus.BAD_REQUEST.value());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    private ResponseEntity<ApiErrorResponse> conflict(String code, String msg) {
        var body = new ApiErrorResponse(code, msg, HttpStatus.CONFLICT.value());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    private ResponseEntity<ApiErrorResponse> serverError(String code, String msg) {
        var body = new ApiErrorResponse(code, msg, HttpStatus.INTERNAL_SERVER_ERROR.value());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    // "Duplicate entry 'xxx' for key 'users.email_unique'" 에서 키/필드 추출
    private String extractDuplicateField(String message) {
        if (message == null) return null;
        // MySQL 드라이버/스프링 포맷 모두 대략 커버
        // ... for key 'schema.table.key' 또는 'table.key' 또는 'key'
        int idx = message.indexOf("for key");
        if (idx < 0) return null;
        int q1 = message.indexOf('\'', idx);
        int q2 = message.indexOf('\'', q1 + 1);
        if (q1 < 0 || q2 < 0) return null;
        String key = message.substring(q1 + 1, q2); // e.g. users.email_unique
        String last = key.contains(".") ? key.substring(key.lastIndexOf('.') + 1) : key;
        // 관례적으로 *_unique, uk_*, uq_* 제거
        return last.replaceAll("(?i)_?unique$", "")
                .replaceFirst("(?i)^uk_\\w+_", "")
                .replaceFirst("(?i)^uq_\\w+_", "");
    }

    private SQLIntegrityConstraintViolationException getSqlCause(Throwable ex) {
        while (ex != null) {
            if (ex instanceof SQLIntegrityConstraintViolationException sqlEx) {
                return sqlEx;
            }
            ex = ex.getCause();
        }
        return null;
    }
}
