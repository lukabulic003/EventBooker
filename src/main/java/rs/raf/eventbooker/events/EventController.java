package rs.raf.eventbooker.events;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import rs.raf.eventbooker.analytics.AnalyticsDao;
import rs.raf.eventbooker.util.JsonUtil;

import static spark.Spark.*;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

public class EventController {

    private final EventDao dao = new EventDao();
    private final AnalyticsDao analytics = new AnalyticsDao();

    public void registerRoutes() {

        path("/api/events", () -> {
            before("/*", (req, res) -> res.type("application/json"));


            get("", (req, res) -> {
                int page = parseInt(req.queryParams("page"), 1);
                int size = parseInt(req.queryParams("size"), 10);

                String q = req.queryParams("q");
                Long categoryId = l(req.queryParams("categoryId"));
                String tag = req.queryParams("tag");
                LocalDateTime from = dt(req.queryParams("from"));
                LocalDateTime to   = dt(req.queryParams("to"));
                String sort = orDefault(req.queryParams("sort"), "scheduled_at");
                String order = orDefault(req.queryParams("order"), "asc");

                try {
                    var items = dao.list(page, size, q, categoryId, tag, from, to, sort, order);
                    int total = dao.count(q, categoryId, tag, from, to);
                    return JsonUtil.toJson(Map.of("items", items, "total", total, "page", page, "size", size));
                } catch (SQLException e) { res.status(500); return err("DB_ERROR", e.getMessage()); }
            });


            get("/search", (req, res) -> {
                int page = parseInt(req.queryParams("page"), 1);
                int size = parseInt(req.queryParams("size"), 10);
                String q = req.queryParams("q");
                try {
                    var items = dao.list(page, size, q, null, null, null, null, "scheduled_at", "asc");
                    int total = dao.count(q, null, null, null, null);
                    return JsonUtil.toJson(Map.of("items", items, "total", total, "page", page, "size", size));
                } catch (SQLException e) { res.status(500); return err("DB_ERROR", e.getMessage()); }
            });


            get("/:id", (req, res) -> {
                long id = parseLong(req.params(":id"));
                String sessionId = req.session(true).id();
                try {
                    analytics.registerEventView(id, sessionId);
                    return dao.findById(id)
                            .map(JsonUtil::toJson)
                            .orElseGet(() -> { res.status(404); return err("NOT_FOUND","Event not found"); });
                } catch (SQLException e) { res.status(500); return err("DB_ERROR", e.getMessage()); }
            });


            get("/:id/details", (req, res) -> {
                long id = parseLong(req.params(":id"));
                String sessionId = req.session(true).id();
                try {
                    analytics.registerEventView(id, sessionId);
                    return dao.findDetails(id)
                            .map(JsonUtil::toJson)
                            .orElseGet(() -> { res.status(404); return err("NOT_FOUND","Event not found"); });
                } catch (SQLException e) { res.status(500); return err("DB_ERROR", e.getMessage()); }
            });


            get("/:id/related", (req, res) -> {
                long id = parseLong(req.params(":id"));
                int limit = parseInt(req.queryParams("limit"), 3);
                try {
                    var items = dao.related(id, limit);
                    return JsonUtil.toJson(Map.of("items", items));
                } catch (SQLException e) { res.status(500); return err("DB_ERROR", e.getMessage()); }
            });


            post("/:id/like", (req, res) -> reactEvent(req, res, true));
            post("/:id/dislike", (req, res) -> reactEvent(req, res, false));
        });


        get("/api/categories/:catId/events", (req, res) -> {
            res.type("application/json");
            long catId = parseLong(req.params(":catId"));
            int page = parseInt(req.queryParams("page"), 1);
            int size = parseInt(req.queryParams("size"), 10);
            try {
                var items = dao.list(page, size, null, catId, null, null, null, "scheduled_at", "asc");
                int total = dao.count(null, catId, null, null, null);
                return JsonUtil.toJson(Map.of("items", items, "total", total, "page", page, "size", size));
            } catch (SQLException e) { res.status(500); return err("DB_ERROR", e.getMessage()); }
        });


        path("/api/ems/events", () -> {
            before("/*", (req, res) -> res.type("application/json"));

            post("", (req, res) -> {
                JsonObject body = JsonUtil.GSON.fromJson(req.body(), JsonObject.class);
                String title = s(body, "title");
                String description = s(body, "description");
                String scheduledAt = s(body, "scheduledAt");
                String location = s(body, "location");
                Integer maxCap = i(body, "maxCapacity");
                Long categoryId = l(body, "categoryId");
                List<String> tags = arr(body, "tags");

                if (blank(title) || blank(description) || blank(scheduledAt) || blank(location) || categoryId == null) {
                    res.status(400); return err("VALIDATION_ERROR","title, description, scheduledAt, location, categoryId su obavezni");
                }
                long authorId = currentUserId(req);
                if (authorId <= 0) { res.status(401); return err("UNAUTHORIZED","Niste prijavljeni"); }

                try {
                    var created = dao.create(
                            authorId, title, description,
                            Timestamp.valueOf(LocalDateTime.parse(scheduledAt)),
                            location, maxCap, categoryId, tags
                    );
                    res.status(201);
                    return JsonUtil.toJson(created);
                } catch (SQLException e) { res.status(500); return err("DB_ERROR", e.getMessage()); }
            });

            put("/:id", (req, res) -> {
                long id = parseLong(req.params(":id"));
                JsonObject body = JsonUtil.GSON.fromJson(req.body(), JsonObject.class);
                String title = s(body, "title");
                String description = s(body, "description");
                String scheduledAt = s(body, "scheduledAt");
                String location = s(body, "location");
                Integer maxCap = i(body, "maxCapacity");
                Long categoryId = l(body, "categoryId");
                List<String> tags = arr(body, "tags");

                if (blank(title) || blank(description) || blank(scheduledAt) || blank(location) || categoryId == null) {
                    res.status(400); return err("VALIDATION_ERROR","title, description, scheduledAt, location, categoryId su obavezni");
                }

                try {
                    String utype = currentUserType(req);
                    long uid = currentUserId(req);
                    if (!isAdmin(utype)) {
                        var authorOpt = dao.getAuthorId(id);
                        if (authorOpt.isEmpty() || !Objects.equals(authorOpt.get(), uid)) {
                            res.status(403); return err("FORBIDDEN","Samo autor događaja ili admin");
                        }
                    }

                    boolean ok = dao.update(
                            id, title, description,
                            Timestamp.valueOf(LocalDateTime.parse(scheduledAt)),
                            location, maxCap, categoryId, tags
                    );
                    if (!ok) { res.status(404); return err("NOT_FOUND","Event not found"); }
                    return JsonUtil.toJson(dao.findById(id).orElse(null));
                } catch (SQLException e) { res.status(500); return err("DB_ERROR", e.getMessage()); }
            });

            delete("/:id", (req, res) -> {
                long id = parseLong(req.params(":id"));
                try {
                    String utype = currentUserType(req);
                    long uid = currentUserId(req);
                    if (!isAdmin(utype)) {
                        var authorOpt = dao.getAuthorId(id);
                        if (authorOpt.isEmpty() || !Objects.equals(authorOpt.get(), uid)) {
                            res.status(403); return err("FORBIDDEN","Samo autor događaja ili admin");
                        }
                    }
                    boolean ok = dao.delete(id);
                    if (!ok) { res.status(404); return err("NOT_FOUND","Event not found"); }
                    res.status(204); return "";
                } catch (SQLException e) { res.status(500); return err("DB_ERROR", e.getMessage()); }
            });
        });
    }


    private String reactEvent(spark.Request req, spark.Response res, boolean like) {
        long id = parseLong(req.params(":id"));
        String sessionId = req.session(true).id();
        try {
            var rr = analytics.reactEvent(id, sessionId, like ? AnalyticsDao.Reaction.LIKE : AnalyticsDao.Reaction.DISLIKE);
            switch (rr) { case CREATED, UNCHANGED, SWITCHED -> { res.status(204); return ""; } default -> { res.status(404); return err("NOT_FOUND","Event not found"); } }
        } catch (SQLException e) { res.status(500); return err("DB_ERROR", e.getMessage()); }
    }


    private static boolean blank(String s){ return s==null || s.isBlank(); }
    private static String s(JsonObject o, String k){ return (o!=null && o.has(k) && !o.get(k).isJsonNull()) ? o.get(k).getAsString().trim() : null; }
    private static Integer i(JsonObject o, String k){ try{ return (o!=null && o.has(k) && !o.get(k).isJsonNull()) ? o.get(k).getAsInt() : null; }catch(Exception e){ return null; } }
    private static Long l(JsonObject o, String k){ try{ return (o!=null && o.has(k) && !o.get(k).isJsonNull()) ? o.get(k).getAsLong() : null; }catch(Exception e){ return null; } }
    private static Long l(String v){ try{ return v==null? null : Long.parseLong(v); }catch(Exception e){ return null; } }
    private static LocalDateTime dt(String v){ try{ return v==null? null : LocalDateTime.parse(v); }catch(Exception e){ return null; } }
    private static String err(String code, String msg){ return JsonUtil.toJson(Map.of("error", code, "message", msg)); }
    private static int parseInt(String s, int def){ try{ return s==null?def:Integer.parseInt(s);}catch(Exception e){ return def; } }
    private static long parseLong(String s){ try{ return Long.parseLong(s);}catch(Exception e){ return -1L; } }
    private static String orDefault(String s, String d){ return (s==null || s.isBlank()) ? d : s; }
    private static long currentUserId(spark.Request req){ return req.session(false)!=null && req.session().attribute("userId")!=null ? (Long) req.session().attribute("userId") : -1L; }
    private static String currentUserType(spark.Request req){ return req.session(false)!=null ? (String) req.session().attribute("userType") : null; }
    private static boolean isAdmin(String t){ return "ADMIN".equalsIgnoreCase(t); }

    private static List<String> arr(JsonObject o, String key) {
        List<String> out = new ArrayList<>();
        if (o == null || !o.has(key) || o.get(key).isJsonNull()) return out;
        try {
            if (o.get(key).isJsonArray()) {
                JsonArray a = o.get(key).getAsJsonArray();
                a.forEach(el -> {
                    if (el != null && !el.isJsonNull()) {
                        String s = el.getAsString().trim();
                        if (!s.isBlank()) out.add(s);
                    }
                });
            } else {
                String s = o.get(key).getAsString().trim();
                if (!s.isBlank()) out.add(s);
            }
        } catch (Exception ignored) {}
        return out;
    }
}
