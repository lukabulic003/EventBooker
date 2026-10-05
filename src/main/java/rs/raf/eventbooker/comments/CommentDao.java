package rs.raf.eventbooker.comments;

import rs.raf.eventbooker.config.Database;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class CommentDao {

    public List<Comment> listByEvent(long eventId, int page, int size) throws SQLException {
        List<Comment> out = new ArrayList<>();
        int offset = (Math.max(page, 1) - 1) * Math.max(size, 1);

        String sql = "SELECT id, event_id, author_name, body, created_at, likes_count, dislikes_count " +
                "FROM comments WHERE event_id = ? ORDER BY created_at DESC LIMIT ? OFFSET ?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, eventId);
            ps.setInt(2, size);
            ps.setInt(3, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(map(rs));
            }
        }
        return out;
    }

    public int countByEvent(long eventId) throws SQLException {
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) AS c FROM comments WHERE event_id = ?")) {
            ps.setLong(1, eventId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt("c");
            }
        }
    }

    public Comment create(long eventId, String authorName, String body) throws SQLException {
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO comments(event_id, author_name, body) VALUES (?,?,?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, eventId);
            ps.setString(2, authorName);
            ps.setString(3, body);
            ps.executeUpdate();
            try (ResultSet k = ps.getGeneratedKeys()) {
                k.next();
                long id = k.getLong(1);
                return findById(id).orElseThrow();
            }
        }
    }

    public Optional<Comment> findById(long id) throws SQLException {
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, event_id, author_name, body, created_at, likes_count, dislikes_count FROM comments WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
                return Optional.empty();
            }
        }
    }

    public boolean like(long id, boolean positive) throws SQLException {
        String sql = positive
                ? "UPDATE comments SET likes_count = likes_count + 1 WHERE id = ?"
                : "UPDATE comments SET dislikes_count = dislikes_count + 1 WHERE id = ?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean delete(long id) throws SQLException {
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM comments WHERE id = ?")) {
            ps.setLong(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    public Optional<Long> getEventIdForComment(long commentId) throws SQLException {
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT event_id FROM comments WHERE id = ?")) {
            ps.setLong(1, commentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(rs.getLong(1));
                return Optional.empty();
            }
        }
    }

    private Comment map(ResultSet rs) throws SQLException {
        return new Comment(
                rs.getLong("id"),
                rs.getLong("event_id"),
                rs.getString("author_name"),
                rs.getString("body"),
                rs.getTimestamp("created_at"),
                rs.getInt("likes_count"),
                rs.getInt("dislikes_count")
        );
    }
}
