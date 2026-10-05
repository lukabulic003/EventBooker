package rs.raf.eventbooker.rsvp;

import com.google.gson.JsonObject;
import rs.raf.eventbooker.events.EventDao;
import rs.raf.eventbooker.util.JsonUtil;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static spark.Spark.*;

public class RsvpController {
    private final RsvpDao dao = new RsvpDao();
    private final EventDao eventDao = new EventDao();

    public void registerRoutes() {

        path("/api/events/:eventId/rsvps", () -> {
            before("/*", (req, res) -> res.type("application/json"));

            post("", (req, res) -> {
                long eventId = parseLong(req.params(":eventId"));
                JsonObject body = JsonUtil.GSON.fromJson(req.body(), JsonObject.class);
                String email = str(body, "email");
                if (isBlank(email) || !email.contains("@")) { res.status(400); return err("VALIDATION_ERROR","email je obavezan i mora sadržati @"); }
                try {
                    RsvpDao.CreateResult r = dao.create(email.trim().toLowerCase(), eventId);
                    switch (r) {
                        case CREATED -> { res.status(201); return JsonUtil.toJson(ok("RSVP_CREATED")); }
                        case DUPLICATE -> { res.status(409); return err("DUPLICATE","Već ste prijavljeni na događaj"); }
                        case FULL -> { res.status(409); return err("FULL","Popunjen kapacitet događaja"); }
                        case NO_EVENT -> { res.status(404); return err("NOT_FOUND","Event ne postoji"); }
                    }
                    res.status(500); return err("SERVER_ERROR","Nepoznat rezultat");
                } catch (SQLException e) { res.status(500); return err("DB_ERROR", e.getMessage()); }
            });

            delete("", (req, res) -> {
                long eventId = parseLong(req.params(":eventId"));
                JsonObject body = JsonUtil.GSON.fromJson(req.body(), JsonObject.class);
                String email = str(body, "email");
                if (isBlank(email)) { res.status(400); return err("VALIDATION_ERROR","email je obavezan"); }
                try {
                    boolean ok = dao.deleteByEmail(email.trim().toLowerCase(), eventId);
                    if (!ok) { res.status(404); return err("NOT_FOUND","Prijava nije pronađena"); }
                    res.status(204); return "";
                } catch (SQLException e) { res.status(500); return err("DB_ERROR", e.getMessage()); }
            });

            get("/count", (req, res) -> {
                long eventId = parseLong(req.params(":eventId"));
                try {
                    int c = dao.countForEvent(eventId);
                    Map<String,Integer> payload = new HashMap<>();
                    payload.put("count", c);
                    return JsonUtil.toJson(payload);
                } catch (SQLException e) { res.status(500); return err("DB_ERROR", e.getMessage()); }
            });
        });


        path("/api/ems/events/:eventId/rsvps", () -> {
            before("/*", (req, res) -> res.type("application/json"));
            get("", (req, res) -> {
                long eventId = parseLong(req.params(":eventId"));
                String userType = req.session(false)==null? null : (String) req.session().attribute("userType");
                Long userId = req.session(false)==null? null : (Long) req.session().attribute("userId");
                if (userId == null) { res.status(401); return err("UNAUTHORIZED","Niste prijavljeni"); }
                try {
                    boolean isAdmin = "ADMIN".equalsIgnoreCase(userType);
                    var authorIdOpt = eventDao.getAuthorId(eventId);
                    if (!isAdmin && (authorIdOpt.isEmpty() || !Objects.equals(authorIdOpt.get(), userId))) {
                        res.status(403); return err("FORBIDDEN","Samo autor događaja ili admin");
                    }
                    int page = parseInt(req.queryParams("page"), 1);
                    int size = parseInt(req.queryParams("size"), 20);
                    List<Rsvp> items = dao.listForEvent(eventId, page, size);
                    Map<String,Object> payload = new HashMap<>();
                    payload.put("items", items);
                    payload.put("total", dao.countForEvent(eventId));
                    payload.put("page", page);
                    payload.put("size", size);
                    return JsonUtil.toJson(payload);
                } catch (SQLException e) { res.status(500); return err("DB_ERROR", e.getMessage()); }
            });
        });
    }


    private static boolean isBlank(String s) { return s==null || s.isBlank(); }
    private static String str(JsonObject o, String key) { return (o!=null && o.has(key) && !o.get(key).isJsonNull()) ? o.get(key).getAsString().trim() : null; }
    private static int parseInt(String s, int def) { try { return s == null ? def : Integer.parseInt(s); } catch (NumberFormatException e) { return def; } }
    private static long parseLong(String s) { try { return Long.parseLong(s); } catch (Exception e) { return -1L; } }
    private static String err(String code, String msg) { Map<String,String> m = new HashMap<>(); m.put("error", code); m.put("message", msg); return JsonUtil.toJson(m); }
    private static Map<String,String> ok(String code) { Map<String,String> m = new HashMap<>(); m.put("status", code); return m; }
}
