package rs.raf.eventbooker.tags;

import rs.raf.eventbooker.util.JsonUtil;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static spark.Spark.*;

public class TagController {
    private final TagDao dao = new TagDao();

    public void registerRoutes() {
        path("/api/tags", () -> {
            before("/*", (req, res) -> res.type("application/json"));

            get("", (req, res) -> {
                int page = parseInt(req.queryParams("page"), 1);
                int size = parseInt(req.queryParams("size"), 20);
                String q = req.queryParams("q");

                try {
                    List<Tag> items = dao.list(page, size, q);
                    int total = dao.count(q);

                    Map<String,Object> payload = new HashMap<>();
                    payload.put("items", items);
                    payload.put("total", total);
                    payload.put("page", page);
                    payload.put("size", size);
                    return JsonUtil.toJson(payload);
                } catch (SQLException e) {
                    res.status(500);
                    Map<String,String> err = new HashMap<>();
                    err.put("error", "DB_ERROR");
                    err.put("message", e.getMessage());
                    return JsonUtil.toJson(err);
                }
            });
        });
    }

    private static int parseInt(String s, int def) {
        try { return s == null ? def : Integer.parseInt(s); }
        catch (NumberFormatException e) { return def; }
    }
}
