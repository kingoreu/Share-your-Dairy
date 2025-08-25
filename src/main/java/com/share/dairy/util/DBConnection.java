package com.share.dairy.util;

import java.sql.*;

public class DBConnection {
    private static final String PROPERTIES_FILE = "/application.properties";

    static {
        try {
            var props = new java.util.Properties();
            try (var input = DBConnection.class.getResourceAsStream(PROPERTIES_FILE)) {
                if (input == null) {
                    throw new RuntimeException("application.properties 파일을 찾을 수 없습니다.");
                }
                props.load(input);
            }

            String driver = props.getProperty("driver");
            Class.forName(driver);

            url = props.getProperty("url");
            user = props.getProperty("user");
            password = props.getProperty("password");

        } catch (Exception e) {
            throw new RuntimeException("DB 설정 로드 실패: " + e.getMessage(), e);
        }
    }

    private static String url;
    private static String user;
    private static String password;

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    public static void close(Connection conn, Statement stmt, ResultSet rs) {
        try { if (rs != null) rs.close(); } catch (Exception ignored) {}
        try { if (stmt != null) stmt.close(); } catch (Exception ignored) {}
        try { if (conn != null) conn.close(); } catch (Exception ignored) {}
    }
}
