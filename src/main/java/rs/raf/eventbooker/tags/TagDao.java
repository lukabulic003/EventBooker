package rs.raf.eventbooker.tags;

import rs.raf.eventbooker.config.Database;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class TagDao {

    public Optional<Long> findIdByName(Connection c, String name) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT id FROM tags WHERE name = ?")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(rs.getLong(1));
                return Optional.empty();
            }
        }
    }

    public long findOrCreate(Connection c, String name) throws SQLException {

        Optional<Long> found = findIdByName(c, name);
        if (found.isPresent()) return found.get();


        try (PreparedStatement ins = c.prepareStatement(
                "INSERT INTO tags(name) VALUES (?)", Statement.RETURN_GENERATED_KEYS)) {
            ins.setString(1, name);
            ins.executeUpdate();
            try (ResultSet keys = ins.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        } catch (SQLException ex) {

            if ("23000".equals(ex.getSQLState())) {
                return findIdByName(c, name).orElseThrow(() -> ex);
            }
            throw ex;
        }
    }

    public List<Tag> list(int page, int size, String q) throws SQLException {
        List<Tag> list = new ArrayList<>();
        int offset = (Math.max(page, 1) - 1) * Math.max(size, 1);

        String base = "SELECT id, name FROM tags ";
        String where = (q == null || q.isBlank()) ? "" : "WHERE name LIKE ? ";
        String orderLimit = "ORDER BY name ASC LIMIT ? OFFSET ?";

        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(base + where + orderLimit)) {
            int idx = 1;
            if (!where.isEmpty()) {
                String like = "%" + q.trim() + "%";
                ps.setString(idx++, like);
            }
            ps.setInt(idx++, size);
            ps.setInt(idx, offset);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new Tag(rs.getLong("id"), rs.getString("name")));
                }
            }
        }
        return list;
    }

    public int count(String q) throws SQLException {
        String base = "SELECT COUNT(*) AS c FROM tags ";
        String where = (q == null || q.isBlank()) ? "" : "WHERE name LIKE ? ";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(base + where)) {
            if (!where.isEmpty()) {
                String like = "%" + q.trim() + "%";
                ps.setString(1, like);
            }
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt("c");
            }
        }
    }
}
