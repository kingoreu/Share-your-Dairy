package com.share.dairy.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public final class DBConnection {

    // ← 현재 잘 되는 값(백업/기본값)
    private static final String DEF_HOST   = "113.198.238.119";
    private static final int    DEF_PORT   = 3306;
    private static final String DEF_SCHEMA = "dairy";
    private static final String DEF_USER   = "dairyuser";
    private static final String DEF_PASS   = "dairypass";

    private static String url;
    private static String user;
    private static String pass;

    static {
        try { Class.forName("com.mysql.cj.jdbc.Driver"); }
        catch (ClassNotFoundException e) { throw new RuntimeException("MySQL 드라이버 없음", e); }

        // 1) properties 시도
        boolean loadedFromProps = false;
        try (var in = DBConnection.class.getResourceAsStream("/application.properties")) {
            if (in != null) {
                Properties p = new Properties();
                p.load(in);
                String host   = p.getProperty("db.host", DEF_HOST);
                int    port   = Integer.parseInt(p.getProperty("db.port", String.valueOf(DEF_PORT)));
                String schema = p.getProperty("db.schema", DEF_SCHEMA);
                user = p.getProperty("db.user", DEF_USER);
                pass = p.getProperty("db.password", DEF_PASS);
                url  = "jdbc:mysql://" + host + ":" + port + "/" + schema
                        + "?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8&serverTimezone=Asia/Seoul";
                loadedFromProps = true;
            }
        } catch (Exception ignore) { /* 프로퍼티 로드 실패 시 하드코딩으로 진행 */ }

        // 2) 실패하면 하드코딩
        if (!loadedFromProps) {
            url  = "jdbc:mysql://" + DEF_HOST + ":" + DEF_PORT + "/" + DEF_SCHEMA
                    + "?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8&serverTimezone=Asia/Seoul";
            user = DEF_USER;
            pass = DEF_PASS;
        }

        System.out.println("[DB] URL=" + url + " USER=" + user + " (props=" + loadedFromProps + ")");
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, user, pass);
    }

    private DBConnection() {}
}
