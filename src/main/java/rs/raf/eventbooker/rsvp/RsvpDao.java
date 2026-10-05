package rs.raf.eventbooker.rsvp;

import rs.raf.eventbooker.config.Database;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class RsvpDao {

    public enum CreateResult { CREATED, DUPLICATE, FULL, NO_EVENT }

    public CreateResult create(String email, long eventId) throws SQLException {
        try (Connection c = Database.getConnection()) {

            Integer maxCap = null;
            try (PreparedStatement ps = c.prepareStatement("SELECT max_capacity FROM events WHERE id = ?")) {
                ps.setLong(1, eventId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return CreateResult.NO_EVENT;
                    maxCap = (Integer) rs.getObject(1);
                }
            }
            if (maxCap != null) {
                int count = countForEvent(eventId);
                if (count >= maxCap) return CreateResult.FULL;
            }

            try (PreparedStatement ins = c.prepareStatement(
                    "INSERT INTO rsvps(user_email, event_id) VALUES (?,?)")) {
                ins.setString(1, email);
                ins.setLong(2, eventId);
                ins.executeUpdate();
                return CreateResult.CREATED;
            } catch (SQLException ex) {
                if ("23000".equals(ex.getSQLState())) {
                    return CreateResult.DUPLICATE; // UNIQUE (user_email,event_id)
                }
                throw ex;
            }
        }
    }

    public boolean deleteByEmail(String email, long eventId) throws SQLException {
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM rsvps WHERE user_email = ? AND event_id = ?")) {
            ps.setString(1, email);
            ps.setLong(2, eventId);
            return ps.executeUpdate() > 0;
        }
    }

    public int countForEvent(long eventId) throws SQLException {
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM rsvps WHERE event_id = ?")) {
            ps.setLong(1, eventId);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getInt(1); }
        }
    }

    public List<Rsvp> listForEvent(long eventId, int page, int size) throws SQLException {
        List<Rsvp> out = new ArrayList<>();
        int offset = (Math.max(page, 1) - 1) * Math.max(size, 1);

        String sql = "SELECT id, user_email, event_id, created_at FROM rsvps " +
                "WHERE event_id = ? ORDER BY created_at DESC LIMIT ? OFFSET ?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, eventId);
            ps.setInt(2, size);
            ps.setInt(3, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new Rsvp(
                            rs.getLong("id"),
                            rs.getString("user_email"),
                            rs.getLong("event_id"),
                            rs.getTimestamp("created_at")
                    ));
                }
            }
        }
        return out;
    }
}
