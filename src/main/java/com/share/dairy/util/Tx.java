
package com.share.dairy.util;

import java.sql.Connection;
import java.sql.SQLException;

public class Tx {
    @FunctionalInterface
    public interface SQLFunction<A, R> { R apply(A a) throws Exception; }

    public static <T> T inTx(SQLFunction<Connection, T> work) throws SQLException {
        try (var con = DBConnection.getConnection()) {
            try {
                con.setAutoCommit(false);
                T result = work.apply(con);
                con.commit();
                return result;
            } catch (Exception e) {
                con.rollback();
                throw e instanceof SQLException ? (SQLException) e : new SQLException(e);
            } finally {
                try { con.setAutoCommit(true); } catch (Exception ignored) {}
            }
        }
    }
}
