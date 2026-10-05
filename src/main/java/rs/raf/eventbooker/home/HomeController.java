package rs.raf.eventbooker.home;

import rs.raf.eventbooker.analytics.AnalyticsDao;
import rs.raf.eventbooker.util.JsonUtil;

import java.sql.*;
import java.util.*;

import static spark.Spark.*;

import rs.raf.eventbooker.config.Database;

public class HomeController {

    public void registerRoutes() {
        path("/api/home", () -> {
            before("/*", (req, res) -> res.type("application/json"));


            get("/recent", (req, res) -> {
                String sql = """
                        SELECT id, title, description, created_at, scheduled_at, location,
                               views_count, likes_count, dislikes_count, max_capacity, author_id, category_id
                        FROM events
                        ORDER BY created_at DESC
                        LIMIT 10
                        """;
                try (Connection c = Database.getConnection();
                     PreparedStatement ps = c.prepareStatement(sql);
                     ResultSet rs = ps.executeQuery()) {
                    var list = new ArrayList<Map<String,Object>>();
                    while (rs.next()) list.add(mapEvent(rs));
                    Map<String,Object> payload = new HashMap<>();
                    payload.put("items", list);
                    return JsonUtil.toJson(payload);
                }
            });


            get("/most-visited", (req, res) -> {
                int days = parseInt(req.queryParams("days"), 30);
                int limit = parseInt(req.queryParams("limit"), 10);
                AnalyticsDao adao = new AnalyticsDao();
                try {
                    List<Long> ids = adao.topVisitedEventIds(days, limit);
                    if (ids.isEmpty()) return JsonUtil.toJson(Map.of("items", List.of()));


                    String in = String.join(",", Collections.nCopies(ids.size(), "?"));
                    String sql = "SELECT id, title, description, created_at, scheduled_at, location," +
                            " views_count, likes_count, dislikes_count, max_capacity, author_id, category_id " +
                            "FROM events WHERE id IN ("+in+")";
                    Map<Long, Map<String,Object>> byId = new HashMap<>();
                    try (Connection c = Database.getConnection();
                         PreparedStatement ps = c.prepareStatement(sql)) {
                        for (int i=0;i<ids.size();i++) ps.setLong(i+1, ids.get(i));
                        try (ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) byId.put(rs.getLong("id"), mapEvent(rs));
                        }
                    }
                    List<Map<String,Object>> ordered = new ArrayList<>();
                    for (Long id: ids) if (byId.containsKey(id)) ordered.add(byId.get(id));
                    return JsonUtil.toJson(Map.of("items", ordered));
                } catch (Exception e) {
                    res.status(500);
                    return JsonUtil.toJson(Map.of("error","SERVER_ERROR", "message", e.getMessage()));
                }
            });


            get("/most-reacted", (req, res) -> {
                int limit = parseInt(req.queryParams("limit"), 3);
                String sql = """
                        SELECT id, title, description, created_at, scheduled_at, location,
                               views_count, likes_count, dislikes_count, max_capacity, author_id, category_id
                        FROM events
                        ORDER BY (likes_count + dislikes_count) DESC, id ASC
                        LIMIT ?
                        """;
                try (Connection c = Database.getConnection();
                     PreparedStatement ps = c.prepareStatement(sql)) {
                    ps.setInt(1, Math.max(limit,1));
                    try (ResultSet rs = ps.executeQuery()) {
                        var list = new ArrayList<Map<String,Object>>();
                        while (rs.next()) list.add(mapEvent(rs));
                        return JsonUtil.toJson(Map.of("items", list));
                    }
                }
            });
        });
    }

    private static Map<String,Object> mapEvent(ResultSet rs) throws SQLException {
        Map<String,Object> m = new HashMap<>();
        m.put("id", rs.getLong("id"));
        m.put("title", rs.getString("title"));
        m.put("description", rs.getString("description"));
        m.put("createdAt", rs.getTimestamp("created_at"));
        m.put("scheduledAt", rs.getTimestamp("scheduled_at"));
        m.put("location", rs.getString("location"));
        m.put("viewsCount", rs.getInt("views_count"));
        m.put("likesCount", rs.getInt("likes_count"));
        m.put("dislikesCount", rs.getInt("dislikes_count"));
        m.put("maxCapacity", (Integer) rs.getObject("max_capacity"));
        m.put("authorId", rs.getLong("author_id"));
        m.put("categoryId", rs.getLong("category_id"));
        return m;
    }

    private static int parseInt(String s, int def) {
        try { return s==null? def : Integer.parseInt(s); }
        catch (Exception e) { return def; }
    }
}
