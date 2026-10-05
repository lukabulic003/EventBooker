package rs.raf.eventbooker.auth;

import com.google.gson.JsonObject;
import rs.raf.eventbooker.util.JsonUtil;
import rs.raf.eventbooker.util.PasswordUtil;

import java.sql.SQLException;

import static spark.Spark.*;

public class AuthController {
    private final UserDao dao = new UserDao();

    private static final boolean AUTO_ACTIVATE_CREATORS = true;

    public void registerRoutes() {
        path("/api/auth", () -> {

            // REGISTER
            post("/register", (req, res) -> {
                res.type("application/json");
                JsonObject b = JsonUtil.GSON.fromJson(req.body(), JsonObject.class);

                String email = str(b,"email");
                String pass  = str(b,"password");
                String first = str(b,"firstName");
                String last  = str(b,"lastName");

                if (blank(email) || blank(pass) || blank(first) || blank(last)) {
                    res.status(400);
                    return JsonUtil.toJson(error("VALIDATION_ERROR","email, password, firstName i lastName su obavezni"));
                }

                try {
                    if (dao.existsByEmail(email)) {
                        res.status(409);
                        return JsonUtil.toJson(error("DUPLICATE","Email je već zauzet"));
                    }

                    User u = dao.create(email, first, last, "CREATOR", pass, AUTO_ACTIVATE_CREATORS);

                    if ("ACTIVE".equalsIgnoreCase(u.getStatus())) {
                        req.session(true).attribute("userId",   u.getId());
                        req.session().attribute("userType",     u.getUserType());
                        req.session().attribute("email",        u.getEmail());
                        req.session().attribute("firstName",    u.getFirstName());
                        req.session().attribute("lastName",     u.getLastName());
                    }

                    res.status(201);
                    JsonObject me = new JsonObject();
                    me.addProperty("id",       u.getId());
                    me.addProperty("email",    u.getEmail());
                    me.addProperty("firstName",u.getFirstName());
                    me.addProperty("lastName", u.getLastName());
                    me.addProperty("userType", u.getUserType());
                    me.addProperty("status",   u.getStatus());
                    me.addProperty("loggedIn", "ACTIVE".equalsIgnoreCase(u.getStatus()));
                    return JsonUtil.toJson(me);

                } catch (SQLException ex) {
                    res.status(500);
                    return JsonUtil.toJson(error("DB_ERROR", ex.getMessage()));
                }
            });

            // LOGIN
            post("/login", (req, res) -> {
                res.type("application/json");
                JsonObject body = JsonUtil.GSON.fromJson(req.body(), JsonObject.class);
                String email = body != null && body.has("email") ? body.get("email").getAsString().trim() : null;
                String pass  = body != null && body.has("password") ? body.get("password").getAsString() : null;

                if (email == null || email.isBlank() || pass == null || pass.isBlank()) {
                    res.status(400);
                    return JsonUtil.toJson(error("VALIDATION_ERROR","email i password su obavezni"));
                }

                try {
                    return dao.findActiveByEmail(email).map(u -> {
                        if (!PasswordUtil.verify(pass, u.getPasswordHash())) {
                            res.status(401);
                            return JsonUtil.toJson(error("UNAUTHORIZED","Pogrešan email ili lozinka"));
                        }
                        // upiši u sesiju
                        req.session(true).attribute("userId",   u.getId());
                        req.session().attribute("userType",     u.getUserType());
                        req.session().attribute("email",        u.getEmail());
                        req.session().attribute("firstName",    u.getFirstName());
                        req.session().attribute("lastName",     u.getLastName());

                        res.status(200);
                        JsonObject me = new JsonObject();
                        me.addProperty("id",       u.getId());
                        me.addProperty("email",    u.getEmail());
                        me.addProperty("firstName",u.getFirstName());
                        me.addProperty("lastName", u.getLastName());
                        me.addProperty("userType", u.getUserType());
                        return JsonUtil.toJson(me);
                    }).orElseGet(() -> {
                        res.status(401);
                        return JsonUtil.toJson(error("UNAUTHORIZED","Pogrešan email ili lozinka ili neaktivan korisnik"));
                    });
                } catch (SQLException e) {
                    res.status(500);
                    return JsonUtil.toJson(error("DB_ERROR", e.getMessage()));
                }
            });

            post("/logout", (req, res) -> {
                if (req.session(false) != null) req.session().invalidate();
                res.status(204);
                return "";
            });

            get("/me", (req, res) -> {
                res.type("application/json");
                if (req.session(false) == null || req.session().attribute("userId") == null) {
                    res.status(401);
                    return JsonUtil.toJson(error("UNAUTHORIZED","Niste prijavljeni"));
                }
                JsonObject me = new JsonObject();
                me.addProperty("id", (Number) req.session().attribute("userId"));
                me.addProperty("email",     (String) req.session().attribute("email"));
                me.addProperty("firstName", (String) req.session().attribute("firstName"));
                me.addProperty("lastName",  (String) req.session().attribute("lastName"));
                me.addProperty("userType",  (String) req.session().attribute("userType"));
                return JsonUtil.toJson(me);
            });
        });
    }

    private static boolean blank(String s){ return s==null || s.isBlank(); }
    private static String str(JsonObject o, String k){ return (o!=null && o.has(k) && !o.get(k).isJsonNull())? o.get(k).getAsString().trim():null; }

    private static JsonObject error(String code, String message) {
        JsonObject o = new JsonObject();
        o.addProperty("error", code);
        o.addProperty("message", message);
        return o;
    }
}
