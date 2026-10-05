package rs.raf.eventbooker;

import rs.raf.eventbooker.auth.AuthController;
import rs.raf.eventbooker.auth.Security;
import rs.raf.eventbooker.auth.UsersController;
import rs.raf.eventbooker.categories.CategoryController;
import rs.raf.eventbooker.config.Database;
import rs.raf.eventbooker.events.EventController;
import rs.raf.eventbooker.tags.TagController;
import rs.raf.eventbooker.comments.CommentController;
import rs.raf.eventbooker.rsvp.RsvpController;
import rs.raf.eventbooker.home.HomeController;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static spark.Spark.*;

public class Main {
    public static void main(String[] args) {

        initExceptionHandler(e -> {
            System.err.println("Spark failed to start: " + e.getMessage());
            e.printStackTrace();
        });

        port(8081);


        staticFiles.location("/public");

        get("/health", (req, res) -> { res.type("text/plain"); return "OK"; });

        get("/db-ping", (req, res) -> {
            try (Connection conn = Database.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) AS c FROM users");
                 ResultSet rs = ps.executeQuery()) {
                rs.next();
                res.type("text/plain");
                return "Users count = " + rs.getInt("c");
            } catch (Exception e) {
                e.printStackTrace();
                res.status(500);
                return "DB error: " + e.getMessage();
            }
        });

        // -------- SECURITY FILTERS --------
        // /api/users → samo prijavljen ADMIN
        before("/api/users",   Security.requireLogin);
        before("/api/users/*", Security.requireLogin);
        before("/api/users",   Security.requireAdmin);
        before("/api/users/*", Security.requireAdmin);

        // EMS rute → login + CREATOR/ADMIN + CREATOR mora biti ACTIVE
        before("/api/ems/*", Security.requireLogin);
        before("/api/ems/*", Security.requireCreatorOrAdmin);
        before("/api/ems/*", Security.requireActiveCreatorIfCreator);

        // -------- CONTROLLERS --------
        new AuthController().registerRoutes();       // /api/auth (login/logout/me)
        new UsersController().registerRoutes();      // /api/users
        new CategoryController().registerRoutes();   // /api/categories
        new TagController().registerRoutes();        // /api/tags
        new EventController().registerRoutes();      // /api/events, /api/ems/events
        new CommentController().registerRoutes();    // /api/events/{id}/comments...
        new RsvpController().registerRoutes();       // /api/events/{id}/rsvps...
        new HomeController().registerRoutes();       // /api/home/*

        // Force JSON for /api/*
        after("/api/*", (req, res) -> {
            String ct = res.type();
            if (ct == null || ct.startsWith("text/")) res.type("application/json");
        });

        // SPA fallback
        get("/", (req, res) -> { res.redirect("/index.html"); return null; });
        notFound((req, res) -> {
            String uri = req.uri();
            if (!uri.startsWith("/api") && !uri.equals("/health") && !uri.equals("/db-ping")) {
                res.type("text/html");
                try (InputStream in = Main.class.getResourceAsStream("/public/index.html")) {
                    if (in != null) return new String(in.readAllBytes(), StandardCharsets.UTF_8);
                } catch (Exception ignored) {}
                return "<!doctype html><title>EventBooker</title><h1>Index not found</h1>";
            }
            res.type("application/json");
            return "{\"error\":\"NOT_FOUND\",\"message\":\"Route not found\"}";
        });

        init();
        awaitInitialization();
        System.out.println("Server started on http://localhost:" + port());

        Runtime.getRuntime().addShutdownHook(new Thread(Database::close));
    }
}
