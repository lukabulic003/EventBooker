package rs.raf.eventbooker.auth;

import com.google.gson.JsonObject;
import rs.raf.eventbooker.util.JsonUtil;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static spark.Spark.*;

public class UsersController {
    private final UserDao dao = new UserDao();

    public void registerRoutes() {
        path("/api/users", () -> {


            get("", (req, res) -> {
                res.type("application/json");
                int page = parseInt(req.queryParams("page"), 1);
                int size = parseInt(req.queryParams("size"), 10);
                String q = req.queryParams("q");
                try {
                    List<User> items = dao.list(page, size, q);
                    int total = dao.count(q);
                    Map<String,Object> payload = new HashMap<>();
                    payload.put("items", items); // passwordHash je transient -> ne ide u JSON
                    payload.put("total", total);
                    payload.put("page", page);
                    payload.put("size", size);
                    return JsonUtil.toJson(payload);
                } catch (SQLException e) { res.status(500); return err("DB_ERROR", e.getMessage()); }
            });


            post("", (req, res) -> {
                res.type("application/json");
                JsonObject b = JsonUtil.GSON.fromJson(req.body(), JsonObject.class);

                String email = str(b,"email");
                String first = str(b,"firstName");
                String last  = str(b,"lastName");
                String type  = normalizeType(str(b,"userType"));
                String pass  = str(b,"password");

                boolean activeRequested = !"INACTIVE".equalsIgnoreCase(str(b,"status"));

                if (blank(email)||blank(first)||blank(last)||blank(type)||blank(pass)) {
                    res.status(400); return err("VALIDATION_ERROR","email, firstName, lastName, userType i password su obavezni");
                }
                if (!isValidType(type)) {
                    res.status(400); return err("VALIDATION_ERROR","userType mora biti ADMIN ili CREATOR");
                }

                boolean active = "ADMIN".equals(type) || activeRequested;

                try {
                    if (dao.existsByEmail(email)) { res.status(409); return err("DUPLICATE","Email je zauzet"); }
                    User u = dao.create(email, first, last, type, pass, active);
                    res.status(201);
                    return JsonUtil.toJson(u); // passwordHash je transient
                } catch (SQLException ex) {
                    if ("23000".equals(ex.getSQLState())) { res.status(409); return err("DUPLICATE","Email je zauzet"); }
                    res.status(500); return err("DB_ERROR", ex.getMessage());
                }
            });


            put("/:id", (req, res) -> {
                res.type("application/json");
                long id = Long.parseLong(req.params(":id"));
                JsonObject b = JsonUtil.GSON.fromJson(req.body(), JsonObject.class);

                String email = str(b,"email");
                String first = str(b,"firstName");
                String last  = str(b,"lastName");
                String type  = normalizeType(str(b,"userType"));

                if (blank(email)||blank(first)||blank(last)||blank(type)) {
                    res.status(400); return err("VALIDATION_ERROR","email, firstName, lastName i userType su obavezni");
                }
                if (!isValidType(type)) {
                    res.status(400); return err("VALIDATION_ERROR","userType mora biti ADMIN ili CREATOR");
                }

                try {
                    boolean ok = dao.updateBasic(id, email, first, last, type);
                    if (!ok) { res.status(404); return err("NOT_FOUND","Korisnik ne postoji"); }

                    if ("ADMIN".equals(type)) {
                        dao.setActive(id, true);
                    }
                    return JsonUtil.toJson(dao.findById(id).orElse(null));
                } catch (SQLException ex) {
                    if ("23000".equals(ex.getSQLState())) { res.status(409); return err("DUPLICATE","Email je zauzet"); }
                    res.status(500); return err("DB_ERROR", ex.getMessage());
                }
            });


            patch("/:id/status", (req, res) -> {
                res.type("application/json");
                long id = Long.parseLong(req.params(":id"));
                JsonObject b = JsonUtil.GSON.fromJson(req.body(), JsonObject.class);
                boolean active = b != null && b.has("active") && b.get("active").getAsBoolean();

                try {
                    var uOpt = dao.findById(id);
                    if (uOpt.isEmpty()) { res.status(404); return err("NOT_FOUND","Korisnik ne postoji"); }
                    var u = uOpt.get();
                    if ("ADMIN".equalsIgnoreCase(u.getUserType())) {
                        res.status(400);
                        return err("VALIDATION_ERROR","Admin nalog mora ostati aktivan");
                    }
                    boolean ok = dao.setActive(id, active);
                    if (!ok) { res.status(404); return err("NOT_FOUND","Korisnik ne postoji"); }
                    return "{\"ok\":true}";
                } catch (SQLException e) { res.status(500); return err("DB_ERROR", e.getMessage()); }
            });
        });
    }


    private static boolean blank(String s){ return s==null || s.isBlank(); }
    private static int parseInt(String s, int def){ try { return s==null?def:Integer.parseInt(s);} catch(Exception e){return def;} }
    private static String str(JsonObject o, String k){ return (o!=null && o.has(k) && !o.get(k).isJsonNull())? o.get(k).getAsString().trim():null; }
    private static String err(String code, String msg){ Map<String,String> m=new HashMap<>(); m.put("error",code); m.put("message",msg); return JsonUtil.toJson(m); }
    private static String normalizeType(String t){ return t==null?null:t.trim().toUpperCase(); }
    private static boolean isValidType(String t){ return "ADMIN".equals(t) || "CREATOR".equals(t); }
}
