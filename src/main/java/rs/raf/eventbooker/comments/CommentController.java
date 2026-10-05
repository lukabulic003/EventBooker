package rs.raf.eventbooker.comments;

import com.google.gson.JsonObject;
import rs.raf.eventbooker.analytics.AnalyticsDao;
import rs.raf.eventbooker.events.EventDao;
import rs.raf.eventbooker.util.JsonUtil;

import java.sql.SQLException;
import java.util.*;

import static spark.Spark.*;

public class CommentController {
    private final CommentDao dao = new CommentDao();
    private final EventDao eventDao = new EventDao();
    private final AnalyticsDao analytics = new AnalyticsDao();

    public void registerRoutes() {
        path("/api/events/:eventId/comments", () -> {
            before("/*", (req, res) -> res.type("application/json"));

            get("", (req, res) -> {
                long eventId = parseLong(req.params(":eventId"));
                int page = parseInt(req.queryParams("page"), 1);
                int size = parseInt(req.queryParams("size"), 10);
                try {
                    var items = dao.listByEvent(eventId, page, size);
                    int total = dao.countByEvent(eventId);
                    return JsonUtil.toJson(Map.of("items", items, "total", total, "page", page, "size", size));
                } catch (SQLException e) { res.status(500); return err("DB_ERROR", e.getMessage()); }
            });

            post("", (req, res) -> {
                long eventId = parseLong(req.params(":eventId"));
                JsonObject body = JsonUtil.GSON.fromJson(req.body(), JsonObject.class);
                String author = str(body,"authorName");
                String text = str(body,"body");
                if (blank(author) || blank(text)) { res.status(400); return err("VALIDATION_ERROR","authorName i body su obavezni"); }
                if (author.length()>120) author = author.substring(0,120);
                if (text.length()>4000) text = text.substring(0,4000);
                try {
                    var created = dao.create(eventId, author, text);
                    res.status(201); return JsonUtil.toJson(created);
                } catch (SQLException e){ res.status(500); return err("DB_ERROR", e.getMessage()); }
            });


            post("/:commentId/like", (req, res) -> react(req, res, true));
            post("/:commentId/dislike", (req, res) -> react(req, res, false));
        });


        path("/api/ems/comments", () -> {
            before("/*", (req, res) -> res.type("application/json"));
            delete("/:id", (req, res) -> {
                long id = parseLong(req.params(":id"));
                Long currentUser = req.session(false)==null? null : (Long) req.session().attribute("userId");
                String userType  = req.session(false)==null? null : (String) req.session().attribute("userType");
                if (currentUser==null){ res.status(401); return err("UNAUTHORIZED","Niste prijavljeni"); }
                try {
                    var evIdOpt = dao.getEventIdForComment(id);
                    if (evIdOpt.isEmpty()){ res.status(404); return err("NOT_FOUND","Comment not found"); }
                    var authorOpt = eventDao.getAuthorId(evIdOpt.get());
                    if (authorOpt.isEmpty()){ res.status(404); return err("NOT_FOUND","Event not found"); }
                    boolean isAdmin = "ADMIN".equalsIgnoreCase(userType);
                    if (!isAdmin && !Objects.equals(authorOpt.get(), currentUser)){ res.status(403); return err("FORBIDDEN","Samo autor događaja ili admin"); }
                    boolean ok = dao.delete(id);
                    if (!ok){ res.status(404); return err("NOT_FOUND","Comment not found"); }
                    res.status(204); return "";
                } catch (SQLException e){ res.status(500); return err("DB_ERROR", e.getMessage()); }
            });
        });
    }

    private String react(spark.Request req, spark.Response res, boolean like) {
        long cid = parseLong(req.params(":commentId"));
        String sid = req.session(true).id();
        try {
            var rr = analytics.reactComment(cid, sid, like? AnalyticsDao.Reaction.LIKE : AnalyticsDao.Reaction.DISLIKE);
            switch (rr){
                case CREATED, UNCHANGED, SWITCHED -> { res.status(204); return ""; }
                default -> { res.status(404); return err("NOT_FOUND","Comment not found"); }
            }
        } catch (SQLException e){ res.status(500); return err("DB_ERROR", e.getMessage()); }
    }

    // utils
    private static boolean blank(String s){ return s==null || s.isBlank(); }
    private static String str(JsonObject o, String k){ return (o!=null && o.has(k) && !o.get(k).isJsonNull())? o.get(k).getAsString().trim(): null; }
    private static int parseInt(String s, int d){ try{ return s==null?d:Integer.parseInt(s);}catch(Exception e){return d;} }
    private static long parseLong(String s){ try{ return Long.parseLong(s);}catch(Exception e){return -1L;} }
    private static String err(String code,String msg){ return JsonUtil.toJson(Map.of("error",code,"message",msg)); }
}
