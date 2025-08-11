package com.share.dairy.controller;

import com.share.dairy.util.DBConnection;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Connection;

@RestController
public class TestController {

    @GetMapping("/db-test")
    public String dbTest() {
        try (Connection conn = DBConnection.getConnection()) {
            if (conn.isValid(2)) {
                return "✅ DB 연결 성공";
            } else {
                return "⚠️ DB 연결 실패";
            }
        } catch (Exception e) {
            return "❌ DB 연결 오류: " + e.getMessage();
        }
    }
}
