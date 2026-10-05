package rs.raf.eventbooker.auth;

import com.google.gson.JsonObject;
import rs.raf.eventbooker.util.JsonUtil;
import spark.Filter;
import spark.Request;

import java.sql.SQLException;

import static spark.Spark.halt;

public class Security {

    private static final UserDao userDao = new UserDao();

    public static Filter requireLogin = (req, res) -> {
        if (req.session(false) == null || req.session().attribute("userId") == null) {
            res.type("application/json");
            halt(401, JsonUtil.toJson(error("UNAUTHORIZED","Prijavite se.")));
        }
    };

    public static Filter requireAdmin = (req, res) -> {
        String role = role(req);
        if (role == null || !"ADMIN".equals(role)) {
            res.type("application/json");
            halt(403, JsonUtil.toJson(error("FORBIDDEN","Potrebna je ADMIN uloga.")));
        }
    };

    public static Filter requireCreatorOrAdmin = (req, res) -> {
        String role = role(req);
        if (role == null || !(role.equals("ADMIN") || role.equals("CREATOR"))) {
            res.type("application/json");
            halt(403, JsonUtil.toJson(error("FORBIDDEN","Potrebna je CREATOR ili ADMIN uloga.")));
        }
    };

    public static Filter requireActiveCreatorIfCreator = (req, res) -> {
        String role = role(req);
        if (!"CREATOR".equals(role)) return;
        Long uid = userId(req);
        if (uid == null) {
            res.type("application/json");
            halt(401, JsonUtil.toJson(error("UNAUTHORIZED", "Prijavite se.")));
        }
        try {
            var uOpt = userDao.findById(uid);
            if (uOpt.isEmpty() || !"ACTIVE".equalsIgnoreCase(uOpt.get().getStatus())) {
                res.type("application/json");
                halt(403, JsonUtil.toJson(error("FORBIDDEN","Vaš CREATOR nalog je deaktiviran.")));
            }
        } catch (SQLException e) {
            res.type("application/json");
            halt(500, JsonUtil.toJson(error("DB_ERROR", e.getMessage())));
        }
    };

    public static String role(Request req) {
        return req.session(false) == null ? null : req.session().attribute("userType");
    }

    public static Long userId(Request req) {
        if (req.session(false) == null) return null;
        Object o = req.session().attribute("userId");
        if (o instanceof Long) return (Long) o;
        if (o instanceof Number) return ((Number) o).longValue();
        return null;
    }

    private static Object error(String code, String msg) {
        JsonObject o = new JsonObject();
        o.addProperty("error", code);
        o.addProperty("message", msg);
        return o;
    }
}
