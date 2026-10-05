package rs.raf.eventbooker.analytics;

import rs.raf.eventbooker.config.Database;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class AnalyticsDao {

    public enum Reaction { LIKE, DISLIKE }
    public enum ReactResult { CREATED, UNCHANGED, SWITCHED, NOT_FOUND }

    /** Zabeleži pregled ako nije već viđen u ovoj sesiji; po uspehu inkrementuje events.views_count. */
    public boolean registerEventView(long eventId, String sessionId) throws SQLException {
        try (Connection c = Database.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO event_views(event_id, session_id) VALUES(?, ?)")) {
                ps.setLong(1, eventId);
                ps.setString(2, sessionId);
                int ins = ps.executeUpdate();
                if (ins > 0) {
                    try (PreparedStatement up = c.prepareStatement(
                            "UPDATE events SET views_count = views_count + 1 WHERE id = ?")) {
                        up.setLong(1, eventId);
                        up.executeUpdate();
                    }
                    return true;
                }
                return false;
            } catch (SQLException ex) {
                if ("23000".equals(ex.getSQLState())) return false;
                throw ex;
            }
        }
    }

    /** Reakcija na EVENT, po sesiji. Ako postoji suprotna reakcija – prebacuje i koriguje agregate. */
    public ReactResult reactEvent(long eventId, String sessionId, Reaction reaction) throws SQLException {
        try (Connection c = Database.getConnection()) {
            try (PreparedStatement ins = c.prepareStatement(
                    "INSERT INTO event_reactions(event_id, session_id, reaction) VALUES (?,?,?)")) {
                ins.setLong(1, eventId);
                ins.setString(2, sessionId);
                ins.setString(3, reaction.name());
                int i = ins.executeUpdate();
                if (i > 0) {
                    if (reaction == Reaction.LIKE) incEvent(c, eventId, "likes_count");
                    else incEvent(c, eventId, "dislikes_count");
                    return ReactResult.CREATED;
                }
            } catch (SQLException ex) {
                if (!"23000".equals(ex.getSQLState())) throw ex;
                String current = null;
                try (PreparedStatement sel = c.prepareStatement(
                        "SELECT reaction FROM event_reactions WHERE event_id=? AND session_id=?")) {
                    sel.setLong(1, eventId);
                    sel.setString(2, sessionId);
                    try (ResultSet rs = sel.executeQuery()) {
                        if (rs.next()) current = rs.getString(1);
                    }
                }
                if (current == null) return ReactResult.NOT_FOUND;

                if (current.equals(reaction.name())) {
                    return ReactResult.UNCHANGED;
                } else {
                    if (reaction == Reaction.LIKE) {
                        incEvent(c, eventId, "likes_count");
                        decEvent(c, eventId, "dislikes_count");
                    } else {
                        incEvent(c, eventId, "dislikes_count");
                        decEvent(c, eventId, "likes_count");
                    }
                    try (PreparedStatement up = c.prepareStatement(
                            "UPDATE event_reactions SET reaction=? WHERE event_id=? AND session_id=?")) {
                        up.setString(1, reaction.name());
                        up.setLong(2, eventId);
                        up.setString(3, sessionId);
                        up.executeUpdate();
                    }
                    return ReactResult.SWITCHED;
                }
            }
            return ReactResult.NOT_FOUND;
        }
    }

    /** Reakcija na COMMENT, po sesiji – isto ponašanje kao za event. */
    public ReactResult reactComment(long commentId, String sessionId, Reaction reaction) throws SQLException {
        try (Connection c = Database.getConnection()) {
            try (PreparedStatement ins = c.prepareStatement(
                    "INSERT INTO comment_reactions(comment_id, session_id, reaction) VALUES (?,?,?)")) {
                ins.setLong(1, commentId);
                ins.setString(2, sessionId);
                ins.setString(3, reaction.name());
                int i = ins.executeUpdate();
                if (i > 0) {
                    if (reaction == Reaction.LIKE) incComment(c, commentId, "likes_count");
                    else incComment(c, commentId, "dislikes_count");
                    return ReactResult.CREATED;
                }
            } catch (SQLException ex) {
                if (!"23000".equals(ex.getSQLState())) throw ex;

                String current = null;
                try (PreparedStatement sel = c.prepareStatement(
                        "SELECT reaction FROM comment_reactions WHERE comment_id=? AND session_id=?")) {
                    sel.setLong(1, commentId);
                    sel.setString(2, sessionId);
                    try (ResultSet rs = sel.executeQuery()) {
                        if (rs.next()) current = rs.getString(1);
                    }
                }
                if (current == null) return ReactResult.NOT_FOUND;

                if (current.equals(reaction.name())) {
                    return ReactResult.UNCHANGED;
                } else {
                    if (reaction == Reaction.LIKE) {
                        incComment(c, commentId, "likes_count");
                        decComment(c, commentId, "dislikes_count");
                    } else {
                        incComment(c, commentId, "dislikes_count");
                        decComment(c, commentId, "likes_count");
                    }
                    try (PreparedStatement up = c.prepareStatement(
                            "UPDATE comment_reactions SET reaction=? WHERE comment_id=? AND session_id=?")) {
                        up.setString(1, reaction.name());
                        up.setLong(2, commentId);
                        up.setString(3, sessionId);
                        up.executeUpdate();
                    }
                    return ReactResult.SWITCHED;
                }
            }
            return ReactResult.NOT_FOUND;
        }
    }

    /** Top N najposećenijih u poslednjih X dana (vrati samo event_id redom) */
    public List<Long> topVisitedEventIds(int days, int limit) throws SQLException {
        String sql = "SELECT event_id, COUNT(*) c " +
                "FROM event_views " +
                "WHERE viewed_at >= (NOW() - INTERVAL ? DAY) " +
                "GROUP BY event_id ORDER BY c DESC, event_id ASC LIMIT ?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, Math.max(days, 1));
            ps.setInt(2, Math.max(limit, 1));
            List<Long> ids = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) ids.add(rs.getLong(1));
            }
            return ids;
        }
    }

    // pomocne klase
    private void incEvent(Connection c, long id, String col) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("UPDATE events SET "+col+"="+col+"+1 WHERE id=?")) {
            ps.setLong(1, id); ps.executeUpdate();
        }
    }
    private void decEvent(Connection c, long id, String col) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("UPDATE events SET "+col+"=GREATEST("+col+"-1,0) WHERE id=?")) {
            ps.setLong(1, id); ps.executeUpdate();
        }
    }
    private void incComment(Connection c, long id, String col) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("UPDATE comments SET "+col+"="+col+"+1 WHERE id=?")) {
            ps.setLong(1, id); ps.executeUpdate();
        }
    }
    private void decComment(Connection c, long id, String col) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("UPDATE comments SET "+col+"=GREATEST("+col+"-1,0) WHERE id=?")) {
            ps.setLong(1, id); ps.executeUpdate();
        }
    }
}
