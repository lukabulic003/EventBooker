package rs.raf.eventbooker.auth;

import rs.raf.eventbooker.config.Database;
import rs.raf.eventbooker.util.PasswordUtil;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class UserDao {

    private User map(ResultSet rs) throws SQLException {
        return new User(
                rs.getLong("id"),
                rs.getString("email"),
                rs.getString("first_name"),
                rs.getString("last_name"),
                rs.getString("user_type"),
                rs.getString("status"),
                rs.getString("password_hash")
        );
    }

    public boolean existsByEmail(String email) throws SQLException {
        String sql = "SELECT 1 FROM users WHERE email = ? LIMIT 1";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public Optional<User> findActiveByEmail(String email) throws SQLException {
        String sql = "SELECT * FROM users WHERE email = ? AND status = 'ACTIVE' LIMIT 1";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    public Optional<User> findById(long id) throws SQLException {
        String sql = "SELECT * FROM users WHERE id = ?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }


    public List<User> list(int page, int size, String q) throws SQLException {
        int offset = (Math.max(page,1)-1) * Math.max(size,1);
        String base = "SELECT * FROM users ";
        String where = (q == null || q.isBlank()) ? "" :
                "WHERE email LIKE ? OR first_name LIKE ? OR last_name LIKE ? ";
        String order = "ORDER BY id DESC LIMIT ? OFFSET ?";

        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(base + where + order)) {
            int i = 1;
            if (!where.isEmpty()) {
                String like = "%" + q.trim() + "%";
                ps.setString(i++, like);
                ps.setString(i++, like);
                ps.setString(i++, like);
            }
            ps.setInt(i++, size);
            ps.setInt(i, offset);

            List<User> list = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            }
            return list;
        }
    }

    public int count(String q) throws SQLException {
        String base = "SELECT COUNT(*) AS c FROM users ";
        String where = (q == null || q.isBlank()) ? "" :
                "WHERE email LIKE ? OR first_name LIKE ? OR last_name LIKE ? ";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(base + where)) {
            if (!where.isEmpty()) {
                String like = "%" + q.trim() + "%";
                ps.setString(1, like);
                ps.setString(2, like);
                ps.setString(3, like);
            }
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getInt("c"); }
        }
    }

    // Kreiranje korisnika
    public User create(String email, String first, String last,
                       String userType, String rawPassword, boolean active) throws SQLException {
        String sql = "INSERT INTO users(email, first_name, last_name, user_type, status, password_hash) " +
                "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, email);
            ps.setString(2, first);
            ps.setString(3, last);
            ps.setString(4, userType);
            ps.setString(5, active ? "ACTIVE" : "INACTIVE");
            ps.setString(6, PasswordUtil.hash(rawPassword));
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                long id = keys.getLong(1);
                return findById(id).orElseThrow();
            }
        }
    }

    // Admin: izmena imena/prezimena/emaila/tipa
    public boolean updateBasic(long id, String email, String first, String last, String userType) throws SQLException {
        String sql = "UPDATE users SET email=?, first_name=?, last_name=?, user_type=? WHERE id=?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, email);
            ps.setString(2, first);
            ps.setString(3, last);
            ps.setString(4, userType);
            ps.setLong(5, id);
            return ps.executeUpdate() > 0;
        }
    }

    // Admin: aktiviraj/deaktiviraj
    public boolean setActive(long id, boolean active) throws SQLException {
        String sql = "UPDATE users SET status=? WHERE id=?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, active ? "ACTIVE" : "INACTIVE");
            ps.setLong(2, id);
            return ps.executeUpdate() > 0;
        }
    }
}
