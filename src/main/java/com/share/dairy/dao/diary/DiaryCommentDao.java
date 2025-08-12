package com.share.dairy.dao.diary;

import com.share.dairy.mapper.diary.DiaryCommentMapper;
import com.share.dairy.model.diary.DiaryComment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class DiaryCommentDao {
    private final DataSource dataSource;
    private final DiaryCommentMapper mapper = new DiaryCommentMapper();

    // CREATE
    public int insert(DiaryComment comment) throws SQLException {
        String sql = """
            INSERT INTO diary_comments 
            (entry_id, user_id, parent_id, comment_content, is_deleted, created_at) 
            VALUES (?, ?, ?, ?, ?, NOW())
        """;
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setLong(1, comment.getEntryId());
            ps.setLong(2, comment.getUserId());
            if (comment.getParentId() != null) {
                ps.setLong(3, comment.getParentId());
            } else {
                ps.setNull(3, Types.BIGINT);
            }
            ps.setString(4, comment.getCommentContent());
            ps.setBoolean(5, comment.isDeleted());
            int rows = ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    comment.setCommentId(rs.getLong(1));
                }
            }
            return rows;
        }
    }

    // READ - by ID
    public Optional<DiaryComment> findById(Long commentId) throws SQLException {
        String sql = "SELECT * FROM diary_comments WHERE comment_id = ?";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setLong(1, commentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapper.map(rs));
                }
                return Optional.empty();
            }
        }

    }

    // READ - by EntryId (모든 댓글 조회)
    public List<DiaryComment> findAllByEntryId(Long entryId) throws SQLException {
        String sql = "SELECT * FROM diary_comments WHERE entry_id = ? ORDER BY created_at ASC";
        List<DiaryComment> list = new ArrayList<>();
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setLong(1, entryId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapper.map(rs));
                }
            }
        }
        return list;
    }

    // UPDATE
    public int update(DiaryComment comment) throws SQLException {
        String sql = """
            UPDATE diary_comments 
            SET comment_content = ?, is_deleted = ?, updated_at = NOW() 
            WHERE comment_id = ?
        """;
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, comment.getCommentContent());
            ps.setBoolean(2, comment.isDeleted());
            ps.setLong(3, comment.getCommentId());
            return ps.executeUpdate();
        }
    }

    // DELETE (soft delete)
    public int delete(Long commentId) throws SQLException {
        String sql = "UPDATE diary_comments SET is_deleted = true, updated_at = NOW() WHERE comment_id = ?";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setLong(1, commentId);
            return ps.executeUpdate();
        }
    }
}
