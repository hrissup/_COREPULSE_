package com.corepulse;

import com.corepulse.PulseEngine.HardwareSnapshot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DatabaseManager {
    private static final String DB_FOLDER = ".corepulse";
    private static final String DB_FILE = "corepulse.db";

    private final Path dbPath;
    private final String dbUrl;

    private ExecutorService dbExecutor;
    private Connection connection;
    private boolean initialized;

    public DatabaseManager() {
        this.dbPath = Paths.get(System.getProperty("user.home"), DB_FOLDER, DB_FILE);
        this.dbUrl = "jdbc:sqlite:" + dbPath.toAbsolutePath();
    }

    public void start() {
        if (dbExecutor != null && !dbExecutor.isShutdown()) {
            return;
        }
        dbExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "CorePulse-DBThread");
            thread.setDaemon(true);
            return thread;
        });
        System.out.println("[Database] Ready at " + dbPath);
    }

    public void stop() {
        if (dbExecutor == null) {
            return;
        }
        dbExecutor.execute(() -> {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException ex) {
                    System.err.println("[Database] Failed to close: " + ex.getMessage());
                }
            }
        });
        dbExecutor.shutdown();
    }

    public void saveSnapshot(HardwareSnapshot snapshot) {
        if (dbExecutor == null || dbExecutor.isShutdown()) {
            return;
        }
        dbExecutor.execute(() -> {
            try {
                ensureInitialized();
                try (PreparedStatement stmt = connection.prepareStatement(
                        "INSERT INTO hardware_samples (recorded_at, cpu_usage, used_ram_gb, total_ram_gb, uptime_seconds) " +
                                "VALUES (?, ?, ?, ?, ?)"
                )) {
                    stmt.setString(1, Instant.now().toString());
                    stmt.setDouble(2, snapshot.cpuUsage());
                    stmt.setDouble(3, snapshot.usedRamGb());
                    stmt.setDouble(4, snapshot.totalRamGb());
                    stmt.setLong(5, snapshot.uptimeSeconds());
                    stmt.executeUpdate();
                }
            } catch (SQLException | IOException ex) {
                System.err.println("[Database] Save failed: " + ex.getMessage());
            }
        });
    }

    private void ensureInitialized() throws SQLException, IOException {
        if (initialized) {
            return;
        }
        Files.createDirectories(dbPath.getParent());
        connection = DriverManager.getConnection(dbUrl);
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS hardware_samples (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                            "recorded_at TEXT NOT NULL, " +
                            "cpu_usage REAL NOT NULL, " +
                            "used_ram_gb REAL NOT NULL, " +
                            "total_ram_gb REAL NOT NULL, " +
                            "uptime_seconds INTEGER NOT NULL" +
                            ")"
            );
        }
        initialized = true;
    }
}
