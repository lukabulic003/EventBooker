package rs.raf.eventbooker.categories;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import rs.raf.eventbooker.util.JsonUtil;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static spark.Spark.*;

public class CategoryController {

    private final CategoryDao dao = new CategoryDao();

    public void registerRoutes() {
        path("/api/categories", () -> {
            before("/*", (req, res) -> res.type("application/json"));

            get("", (req, res) -> {
                int page = parseInt(req.queryParams("page"), 1);
                int size = parseInt(req.queryParams("size"), 10);
                String q = req.queryParams("q");

                try {
                    List<Category> items = dao.list(page, size, q);
                    int total = dao.count(q);
                    Map<String, Object> payload = new HashMap<>();
                    payload.put("items", items);
                    payload.put("total", total);
                    payload.put("page", page);
                    payload.put("size", size);
                    res.status(200);
                    return JsonUtil.toJson(payload);
                } catch (SQLException e) {
                    res.status(500);
                    return jsonError("DB_ERROR", e.getMessage());
                }
            });


            post("", (req, res) -> {
                if (!isCreatorOrAdmin(req)) { res.status(403); return jsonError("FORBIDDEN","Potrebna je CREATOR ili ADMIN uloga."); }
                Long uid = sessionUserId(req);
                if (uid == null) { res.status(401); return jsonError("UNAUTHORIZED","Niste prijavljeni"); }

                try {
                    JsonObject body = JsonUtil.GSON.fromJson(req.body(), JsonObject.class);
                    String name = trimOrNull(body, "name");
                    String description = trimOrNull(body, "description");
                    if (blank(name) || blank(description)) { res.status(400); return jsonError("VALIDATION_ERROR", "name i description su obavezni"); }
                    try {
                        Category created = dao.create(name, description, uid);
                        res.status(201);
                        return JsonUtil.toJson(created);
                    } catch (SQLException ex) {
                        if ("23000".equals(ex.getSQLState())) {
                            res.status(409);
                            return jsonError("DUPLICATE", "Kategorija sa tim nazivom već postoji");
                        }
                        throw ex;
                    }
                } catch (JsonSyntaxException e) {
                    res.status(400); return jsonError("BAD_JSON", "Neispravan JSON");
                } catch (SQLException e) {
                    res.status(500); return jsonError("DB_ERROR", e.getMessage());
                }
            });


            put("/:id", (req, res) -> {
                if (!isCreatorOrAdmin(req)) { res.status(403); return jsonError("FORBIDDEN","Potrebna je CREATOR ili ADMIN uloga."); }
                long id = parseLong(req.params(":id"));
                Long uid = sessionUserId(req);
                String role = sessionRole(req);

                try {
                    Category existing = dao.findById(id).orElse(null);
                    if (existing == null) { res.status(404); return jsonError("NOT_FOUND", "Kategorija ne postoji"); }

                    boolean isOwner = existing.getCreatedBy() != null && existing.getCreatedBy().equals(uid);
                    boolean isAdmin = "ADMIN".equals(role);
                    if (!isAdmin && !isOwner) { res.status(403); return jsonError("FORBIDDEN", "Samo vlasnik ili admin može da menja kategoriju"); }

                    JsonObject body = JsonUtil.GSON.fromJson(req.body(), JsonObject.class);
                    String name = trimOrNull(body, "name");
                    String description = trimOrNull(body, "description");
                    if (blank(name) || blank(description)) { res.status(400); return jsonError("VALIDATION_ERROR", "name i description su obavezni"); }

                    try {
                        boolean ok = dao.update(id, name, description);
                        if (!ok) { res.status(404); return jsonError("NOT_FOUND", "Kategorija ne postoji"); }
                        res.status(200);
                        return JsonUtil.toJson(dao.findById(id).orElse(null));
                    } catch (SQLException ex) {
                        if ("23000".equals(ex.getSQLState())) {
                            res.status(409);
                            return jsonError("DUPLICATE", "Kategorija sa tim nazivom već postoji");
                        }
                        throw ex;
                    }
                } catch (JsonSyntaxException e) {
                    res.status(400); return jsonError("BAD_JSON", "Neispravan JSON");
                } catch (SQLException e) {
                    res.status(500); return jsonError("DB_ERROR", e.getMessage());
                }
            });


            delete("/:id", (req, res) -> {
                if (!isCreatorOrAdmin(req)) { res.status(403); return jsonError("FORBIDDEN","Potrebna je CREATOR ili ADMIN uloga."); }
                long id = parseLong(req.params(":id"));
                Long uid = sessionUserId(req);
                String role = sessionRole(req);

                try {
                    Category existing = dao.findById(id).orElse(null);
                    if (existing == null) { res.status(404); return jsonError("NOT_FOUND", "Kategorija ne postoji"); }

                    boolean isOwner = existing.getCreatedBy() != null && existing.getCreatedBy().equals(uid);
                    boolean isAdmin = "ADMIN".equals(role);
                    if (!isAdmin && !isOwner) { res.status(403); return jsonError("FORBIDDEN", "Samo vlasnik ili admin može da briše kategoriju"); }

                    int result = dao.delete(id);
                    if (result == 1) { res.status(204); return ""; }
                    else if (result == 0) { res.status(404); return jsonError("NOT_FOUND", "Kategorija ne postoji"); }
                    else { res.status(409); return jsonError("IN_USE", "Kategorija se ne može obrisati jer postoji barem jedan događaj u njoj"); }
                } catch (SQLException e) {
                    res.status(500); return jsonError("DB_ERROR", e.getMessage());
                }
            });
        });
    }


    private static boolean isCreatorOrAdmin(spark.Request req) {
        String role = sessionRole(req);
        return role != null && (role.equals("ADMIN") || role.equals("CREATOR"));
    }
    private static String sessionRole(spark.Request req) {
        return req.session(false)==null ? null : (String) req.session().attribute("userType");
    }
    private static Long sessionUserId(spark.Request req) {
        if (req.session(false) == null) return null;
        Object o = req.session().attribute("userId");
        if (o instanceof Long) return (Long) o;
        if (o instanceof Number) return ((Number)o).longValue();
        return null;
    }
    private static boolean blank(String s){ return s==null || s.isBlank(); }
    private static int parseInt(String s, int def) { try { return s==null? def : Integer.parseInt(s); } catch (NumberFormatException e) { return def; } }
    private static long parseLong(String s) { try { return Long.parseLong(s); } catch (NumberFormatException e) { return -1L; } }
    private static String trimOrNull(JsonObject obj, String key) { if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return null; return obj.get(key).getAsString().trim(); }
    private static String jsonError(String code, String message) { Map<String, String> m = new HashMap<>(); m.put("error", code); m.put("message", message); return JsonUtil.toJson(m); }
}
