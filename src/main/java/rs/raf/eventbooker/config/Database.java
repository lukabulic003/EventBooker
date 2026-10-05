package rs.raf.eventbooker.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

public class Database {
    private static HikariDataSource dataSource;

    static {
        try (InputStream in = Database.class.getClassLoader().getResourceAsStream("db.properties")) {
            if (in == null) {
                throw new RuntimeException("db.properties nije pronađen u resources!");
            }
            Properties props = new Properties();
            props.load(in);

            HikariConfig cfg = new HikariConfig();
            cfg.setJdbcUrl(props.getProperty("db.url"));
            cfg.setUsername(props.getProperty("db.user"));
            cfg.setPassword(props.getProperty("db.password"));
            cfg.setMaximumPoolSize(Integer.parseInt(props.getProperty("db.pool.size", "10")));
            cfg.setDriverClassName("com.mysql.cj.jdbc.Driver");

            dataSource = new HikariDataSource(cfg);
        } catch (IOException e) {
            throw new RuntimeException("Greška pri učitavanju db.properties", e);
        }
    }

    public static Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public static void close() {
        if (dataSource != null) dataSource.close();
    }
}
