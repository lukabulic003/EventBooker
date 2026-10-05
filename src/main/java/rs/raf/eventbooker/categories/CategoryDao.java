package rs.raf.eventbooker.categories;

import rs.raf.eventbooker.config.Database;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class CategoryDao {

    private Category mapRow(ResultSet rs) throws SQLException {
        Object ownerObj = null;
        try { ownerObj = rs.getObject("created_by"); } catch (SQLException ignored) {}
        Long createdBy = ownerObj == null ? null : ((Number) ownerObj).longValue();

        Integer cnt = null;
        try {
            int v = rs.getInt("events_count");
            cnt = rs.wasNull() ? null : v;
        } catch (SQLException ignored) {}

        return new Category(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("description"),
                createdBy,
                cnt
        );
    }

    public List<Category> list(int page, int size, String q) throws SQLException {
        List<Category> list = new ArrayList<>();
        int offset = (Math.max(page, 1) - 1) * Math.max(size, 1);

        String base =
                "SELECT c.id, c.name, c.description, c.created_by, " +
                        "       COALESCE(ec.cnt, 0) AS events_count " +
                        "FROM categories c " +
                        "LEFT JOIN (SELECT category_id, COUNT(*) AS cnt FROM events GROUP BY category_id) ec " +
                        "       ON ec.category_id = c.id ";
        String where = (q == null || q.isBlank()) ? "" : "WHERE c.name LIKE ? OR c.description LIKE ? ";
        String orderLimit = "ORDER BY c.id DESC LIMIT ? OFFSET ?";

        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(base + where + orderLimit)) {

            int idx = 1;
            if (!where.isEmpty()) {
                String like = "%" + q.trim() + "%";
                ps.setString(idx++, like);
                ps.setString(idx++, like);
            }
            ps.setInt(idx++, size);
            ps.setInt(idx, offset);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        }
        return list;
    }

    public int count(String q) throws SQLException {
        String base = "SELECT COUNT(*) AS c FROM categories ";
        String where = (q == null || q.isBlank()) ? "" : "WHERE name LIKE ? OR description LIKE ? ";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(base + where)) {
            if (!where.isEmpty()) {
                String like = "%" + q.trim() + "%";
                ps.setString(1, like);
                ps.setString(2, like);
            }
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt("c");
            }
        }
    }

    public Optional<Category> findById(long id) throws SQLException {
        String sql =
                "SELECT c.id, c.name, c.description, c.created_by, " +
                        "       (SELECT COUNT(*) FROM events e WHERE e.category_id = c.id) AS events_count " +
                        "FROM categories c WHERE c.id = ?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
                return Optional.empty();
            }
        }
    }

    public Category create(String name, String description, long createdBy) throws SQLException {
        String sql = "INSERT INTO categories(name, description, created_by) VALUES (?, ?, ?)";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, description);
            ps.setLong(3, createdBy);
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                long id = keys.getLong(1);
                return new Category(id, name, description, createdBy, 0);
            }
        }
    }

    public boolean update(long id, String name, String description) throws SQLException {
        String sql = "UPDATE categories SET name = ?, description = ? WHERE id = ?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, description);
            ps.setLong(3, id);
            return ps.executeUpdate() > 0;
        }
    }


    public int delete(long id) throws SQLException {
        // 1) Ima li događaja u ovoj kategoriji?
        String checkSql = "SELECT COUNT(*) AS c FROM events WHERE category_id = ?";
        try (Connection c = Database.getConnection();
             PreparedStatement chk = c.prepareStatement(checkSql)) {
            chk.setLong(1, id);
            try (ResultSet rs = chk.executeQuery()) {
                rs.next();
                if (rs.getInt("c") > 0) return -1; // zabranjeno brisanje – postoje događaji
            }
        }
        // 2) Nema događaja → briši
        String delSql = "DELETE FROM categories WHERE id = ?";
        try (Connection c = Database.getConnection();
             PreparedStatement del = c.prepareStatement(delSql)) {
            del.setLong(1, id);
            int rows = del.executeUpdate();
            return rows > 0 ? 1 : 0;
        }
    }
}
