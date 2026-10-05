package rs.raf.eventbooker.events;

import rs.raf.eventbooker.config.Database;
import rs.raf.eventbooker.tags.TagDao;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

public class EventDao {
    private final TagDao tagDao = new TagDao();


    public List<Event> list(int page, int size, String q, Long categoryId, String tag,
                            LocalDateTime from, LocalDateTime to, String sort, String order)
            throws SQLException {

        List<Event> out = new ArrayList<>();
        int offset = (Math.max(page, 1) - 1) * Math.max(size, 1);

        StringBuilder sb = new StringBuilder();
        sb.append("SELECT DISTINCT e.id, e.title, e.description, e.created_at, e.scheduled_at, ")
                .append("e.location, e.views_count, e.likes_count, e.dislikes_count, e.max_capacity, ")
                .append("e.author_id, e.category_id ")
                .append("FROM events e ");

        if (tag != null && !tag.isBlank()) {
            sb.append("JOIN event_tags et ON et.event_id = e.id ")
                    .append("JOIN tags t ON t.id = et.tag_id ");
        }

        List<Object> params = new ArrayList<>();
        List<String> cond = new ArrayList<>();

        if (q != null && !q.isBlank()) {
            cond.add("(e.title LIKE ? OR e.description LIKE ?)");
            String like = "%" + q.trim() + "%";
            params.add(like); params.add(like);
        }
        if (categoryId != null && categoryId > 0) {
            cond.add("e.category_id = ?");
            params.add(categoryId);
        }
        if (tag != null && !tag.isBlank()) {
            cond.add("t.name = ?");
            params.add(tag.trim());
        }
        if (from != null) { cond.add("e.scheduled_at >= ?"); params.add(Timestamp.valueOf(from)); }
        if (to   != null) { cond.add("e.scheduled_at <= ?"); params.add(Timestamp.valueOf(to)); }

        if (!cond.isEmpty()) sb.append("WHERE ").append(String.join(" AND ", cond)).append(" ");

        String sortCol = switch (sort == null ? "" : sort) {
            case "created_at"   -> "e.created_at";
            case "views"        -> "e.views_count";
            case "likes"        -> "e.likes_count";
            case "scheduled_at" -> "e.scheduled_at";
            default             -> "e.scheduled_at";
        };
        String ord = ("asc".equalsIgnoreCase(order)) ? "ASC" : "DESC";

        sb.append("ORDER BY ").append(sortCol).append(" ").append(ord).append(" ");
        sb.append("LIMIT ? OFFSET ?");

        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sb.toString())) {

            int idx = 1;
            for (Object p : params) {
                if (p instanceof String s) ps.setString(idx++, s);
                else if (p instanceof Long l) ps.setLong(idx++, l);
                else if (p instanceof Timestamp tss) ps.setTimestamp(idx++, tss);
            }
            ps.setInt(idx++, size);
            ps.setInt(idx, offset);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(mapEvent(rs));
            }
        }

        attachTags(out);
        return out;
    }

    public int count(String q, Long categoryId, String tag, LocalDateTime from, LocalDateTime to)
            throws SQLException {
        StringBuilder sb = new StringBuilder();
        sb.append("SELECT COUNT(DISTINCT e.id) AS c FROM events e ");
        if (tag != null && !tag.isBlank()) {
            sb.append("JOIN event_tags et ON et.event_id = e.id ")
                    .append("JOIN tags t ON t.id = et.tag_id ");
        }

        List<Object> params = new ArrayList<>();
        List<String> cond = new ArrayList<>();

        if (q != null && !q.isBlank()) {
            cond.add("(e.title LIKE ? OR e.description LIKE ?)");
            String like = "%" + q.trim() + "%";
            params.add(like); params.add(like);
        }
        if (categoryId != null && categoryId > 0) { cond.add("e.category_id = ?"); params.add(categoryId); }
        if (tag != null && !tag.isBlank())        { cond.add("t.name = ?");       params.add(tag.trim()); }
        if (from != null) { cond.add("e.scheduled_at >= ?"); params.add(Timestamp.valueOf(from)); }
        if (to   != null) { cond.add("e.scheduled_at <= ?"); params.add(Timestamp.valueOf(to)); }

        if (!cond.isEmpty()) sb.append("WHERE ").append(String.join(" AND ", cond)).append(" ");

        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sb.toString())) {
            int idx = 1;
            for (Object p : params) {
                if (p instanceof String s) ps.setString(idx++, s);
                else if (p instanceof Long l) ps.setLong(idx++, l);
                else if (p instanceof Timestamp tss) ps.setTimestamp(idx++, tss);
            }
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getInt("c"); }
        }
    }

    public Optional<Event> findById(long id) throws SQLException {
        String sql = "SELECT e.id, e.title, e.description, e.created_at, e.scheduled_at, e.location, " +
                "e.views_count, e.likes_count, e.dislikes_count, e.max_capacity, e.author_id, e.category_id " +
                "FROM events e WHERE e.id = ?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                Event e = mapEvent(rs);
                e.setTags(fetchTagsForEvent(c, id));
                return Optional.of(e);
            }
        }
    }

    /** Controller traži findDetails – ovde samo delegiramo na findById. */
    public Optional<Event> findDetails(long id) throws SQLException {
        return findById(id);
    }

    /** Vraća "srodne" događaje: prvo po zajedničkim tagovima, pa dopuna istom kategorijom. */
    public List<Event> related(long id, int limit) throws SQLException {
        if (limit <= 0) limit = 5;

        List<Event> out = new ArrayList<>();
        LinkedHashSet<Long> seen = new LinkedHashSet<>();

        try (Connection c = Database.getConnection()) {

            Long catId = null;
            try (PreparedStatement ps = c.prepareStatement("SELECT category_id FROM events WHERE id = ?")) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return out;
                    catId = rs.getLong(1);
                }
            }


            String sql1 =
                    "SELECT DISTINCT e.id, e.title, e.description, e.created_at, e.scheduled_at, e.location, " +
                            "       e.views_count, e.likes_count, e.dislikes_count, e.max_capacity, e.author_id, e.category_id " +
                            "FROM events e " +
                            "JOIN event_tags et ON et.event_id = e.id " +
                            "WHERE et.tag_id IN (SELECT tag_id FROM event_tags WHERE event_id = ?) " +
                            "  AND e.id <> ? " +
                            "ORDER BY e.scheduled_at ASC " +
                            "LIMIT ?";
            try (PreparedStatement ps1 = c.prepareStatement(sql1)) {
                ps1.setLong(1, id);
                ps1.setLong(2, id);
                ps1.setInt(3, limit);
                try (ResultSet rs = ps1.executeQuery()) {
                    while (rs.next()) {
                        Event e = mapEvent(rs);
                        out.add(e);
                        seen.add(e.getId());
                        if (out.size() >= limit) break;
                    }
                }
            }


            if (out.size() < limit) {
                String notIn = seen.isEmpty() ? "" :
                        " AND e.id NOT IN (" + String.join(",", Collections.nCopies(seen.size(), "?")) + ")";
                String sql2 =
                        "SELECT e.id, e.title, e.description, e.created_at, e.scheduled_at, e.location, " +
                                "       e.views_count, e.likes_count, e.dislikes_count, e.max_capacity, e.author_id, e.category_id " +
                                "FROM events e " +
                                "WHERE e.category_id = ? AND e.id <> ? " + notIn +
                                " ORDER BY e.scheduled_at ASC " +
                                "LIMIT ?";
                try (PreparedStatement ps2 = c.prepareStatement(sql2)) {
                    int i = 1;
                    ps2.setLong(i++, catId);
                    ps2.setLong(i++, id);
                    for (Long sid : seen) ps2.setLong(i++, sid);
                    ps2.setInt(i, Math.max(0, limit - out.size()));
                    try (ResultSet rs = ps2.executeQuery()) {
                        while (rs.next()) {
                            Event e = mapEvent(rs);
                            out.add(e);
                            seen.add(e.getId());
                            if (out.size() >= limit) break;
                        }
                    }
                }
            }
        }


        attachTags(out);
        return out;
    }

    public Optional<Long> getAuthorId(long eventId) throws SQLException {
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT author_id FROM events WHERE id = ?")) {
            ps.setLong(1, eventId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(rs.getLong(1)) : Optional.empty();
            }
        }
    }


    public Event create(long authorId, String title, String description, Timestamp scheduledAt,
                        String location, Integer maxCapacity, long categoryId, List<String> tags)
            throws SQLException {
        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                long newId;
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO events (title, description, scheduled_at, location, max_capacity, author_id, category_id) " +
                                "VALUES (?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, title);
                    ps.setString(2, description);
                    ps.setTimestamp(3, scheduledAt);
                    ps.setString(4, location);
                    if (maxCapacity == null) ps.setNull(5, Types.INTEGER); else ps.setInt(5, maxCapacity);
                    ps.setLong(6, authorId);
                    ps.setLong(7, categoryId);
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) { keys.next(); newId = keys.getLong(1); }
                }

                upsertEventTags(c, newId, normalizeTags(tags));
                c.commit();
                return findById(newId).orElseThrow();
            } catch (SQLException ex) {
                c.rollback(); throw ex;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    public boolean update(long id, String title, String description, Timestamp scheduledAt,
                          String location, Integer maxCapacity, long categoryId, List<String> tags)
            throws SQLException {
        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE events SET title=?, description=?, scheduled_at=?, location=?, max_capacity=?, category_id=? WHERE id=?")) {
                    ps.setString(1, title);
                    ps.setString(2, description);
                    ps.setTimestamp(3, scheduledAt);
                    ps.setString(4, location);
                    if (maxCapacity == null) ps.setNull(5, Types.INTEGER); else ps.setInt(5, maxCapacity);
                    ps.setLong(6, categoryId);
                    ps.setLong(7, id);
                    if (ps.executeUpdate() == 0) { c.rollback(); return false; }
                }
                try (PreparedStatement del = c.prepareStatement("DELETE FROM event_tags WHERE event_id = ?")) {
                    del.setLong(1, id); del.executeUpdate();
                }
                upsertEventTags(c, id, normalizeTags(tags));
                c.commit();
                return true;
            } catch (SQLException ex) {
                c.rollback(); throw ex;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    public boolean delete(long id) throws SQLException {
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM events WHERE id = ?")) {
            ps.setLong(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    // ------- helpers -------
    private Event mapEvent(ResultSet rs) throws SQLException {
        return new Event(
                rs.getLong("id"),
                rs.getString("title"),
                rs.getString("description"),
                rs.getTimestamp("created_at"),
                rs.getTimestamp("scheduled_at"),
                rs.getString("location"),
                rs.getInt("views_count"),
                rs.getInt("likes_count"),
                rs.getInt("dislikes_count"),
                (Integer) rs.getObject("max_capacity"),
                rs.getLong("author_id"),
                rs.getLong("category_id")
        );
    }

    private void attachTags(List<Event> events) throws SQLException {
        if (events.isEmpty()) return;
        Map<Long, Event> map = new HashMap<>();
        List<Long> ids = new ArrayList<>();
        for (Event e : events) { map.put(e.getId(), e); ids.add(e.getId()); }

        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        String sql = "SELECT et.event_id, t.name FROM event_tags et JOIN tags t ON t.id = et.tag_id " +
                "WHERE et.event_id IN (" + placeholders + ")";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            int i = 1;
            for (Long id : ids) ps.setLong(i++, id);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) map.get(rs.getLong(1)).getTags().add(rs.getString(2));
            }
        }
    }

    private List<String> fetchTagsForEvent(Connection c, long eventId) throws SQLException {
        List<String> list = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT t.name FROM event_tags et JOIN tags t ON t.id = et.tag_id WHERE et.event_id = ? ORDER BY t.name")) {
            ps.setLong(1, eventId);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) list.add(rs.getString(1)); }
        }
        return list;
    }

    private List<String> normalizeTags(List<String> tags) {
        List<String> out = new ArrayList<>();
        if (tags == null) return out;
        for (String t : tags) {
            if (t == null) continue;
            String s = t.trim();
            if (!s.isBlank()) out.add(s);
        }
        return out;
    }

    private void upsertEventTags(Connection c, long eventId, List<String> tags) throws SQLException {
        for (String name : tags) {
            long tagId = tagDao.findOrCreate(c, name);
            try (PreparedStatement link = c.prepareStatement(
                    "INSERT IGNORE INTO event_tags(event_id, tag_id) VALUES (?,?)")) {
                link.setLong(1, eventId);
                link.setLong(2, tagId);
                link.executeUpdate();
            }
        }
    }
}
