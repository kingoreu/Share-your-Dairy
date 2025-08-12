package com.share.dairy;

import com.share.dairy.util.DBConnection;
import java.sql.Connection;

public class TestDB {
    public static void main(String[] args) {
        try (Connection conn = DBConnection.getConnection()) {
            System.out.println("DB 연결 성공. 스키마: " + conn.getCatalog());
        } catch (Exception e) {
            e.printStackTrace();

        }
    }
}
