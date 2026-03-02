package cz.yourserver.afkshop;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;

/**
 * SQLite database for pending earnings. All operations run async.
 */
public class Database {

    private static final String TABLE = "shops";
    private static final String CREATE_SQL = "CREATE TABLE IF NOT EXISTS " + TABLE
            + " (owner_uuid TEXT PRIMARY KEY, earnings REAL)";
    private static final String SAVE_SQL = "INSERT OR REPLACE INTO " + TABLE + " (owner_uuid, earnings) VALUES (?, ?)";
    private static final String LOAD_SQL = "SELECT earnings FROM " + TABLE + " WHERE owner_uuid = ?";
    private static final String DELETE_SQL = "DELETE FROM " + TABLE + " WHERE owner_uuid = ?";

    private final JavaPlugin plugin;
    private final File dbFile;
    private final ExecutorService executor;

    public Database(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin);
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        this.dbFile = new File(dataFolder, "data.db");
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "AFKShop-DB");
            t.setDaemon(true);
            return t;
        });
    }

    public void initialize() {
        executor.submit(() -> {
            try (Connection conn = getConnection()) {
                try (PreparedStatement ps = conn.prepareStatement(CREATE_SQL)) {
                    ps.execute();
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to create table", e);
            }
        });
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
    }

    /**
     * Save earnings for owner (async).
     */
    public CompletableFuture<Void> saveEarningsAsync(UUID ownerUuid, double earnings) {
        return CompletableFuture.runAsync(() -> saveEarnings(ownerUuid, earnings), executor);
    }

    public void saveEarnings(UUID ownerUuid, double earnings) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(SAVE_SQL)) {
            ps.setString(1, ownerUuid.toString());
            ps.setDouble(2, earnings);
            ps.execute();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save earnings for " + ownerUuid, e);
        }
    }

    /**
     * Load earnings for owner (async). Returns 0 if not found.
     */
    public CompletableFuture<Double> loadEarningsAsync(UUID ownerUuid) {
        return CompletableFuture.supplyAsync(() -> loadEarnings(ownerUuid), executor);
    }

    public double loadEarnings(UUID ownerUuid) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(LOAD_SQL)) {
            ps.setString(1, ownerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getDouble("earnings") : 0.0;
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load earnings for " + ownerUuid, e);
            return 0.0;
        }
    }

    /**
     * Delete earnings record (async).
     */
    public void deleteEarnings(UUID ownerUuid) {
        executor.submit(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(DELETE_SQL)) {
                ps.setString(1, ownerUuid.toString());
                ps.execute();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to delete earnings for " + ownerUuid, e);
            }
        });
    }

    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
