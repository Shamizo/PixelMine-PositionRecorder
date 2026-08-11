package com.pixelmine.positionrecorder.database;

import com.pixelmine.positionrecorder.PositionRecorderPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;

public class PositionDatabase {

    private final PositionRecorderPlugin plugin;
    private Connection connection;

    // 独立线程池用于数据库写入，避免阻塞游戏线程
    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "PMSWorld-DB-Writer");
        t.setDaemon(true);
        return t;
    });

    // 内存缓存，避免频繁读取数据库
    private final Map<UUID, PlayerPosition> positionCache = new ConcurrentHashMap<>();

    // 待写入队列，批量合并写入
    private final BlockingQueue<PlayerPosition> writeQueue = new LinkedBlockingQueue<>();
    private volatile boolean running = true;

    public PositionDatabase(PositionRecorderPlugin plugin) {
        this.plugin = plugin;
    }

    public void init() {
        try {
            Class.forName("org.sqlite.JDBC");

            // 数据库存储在 data/ 子目录
            String dataFolderName = plugin.getConfig().getString("database.data-folder", "data");
            File dataDir = new File(plugin.getDataFolder(), dataFolderName);
            if (!dataDir.exists() && !dataDir.mkdirs()) {
                throw new RuntimeException("无法创建数据目录: " + dataDir.getAbsolutePath());
            }
            String dbPath = new File(dataDir, "positions.db").getAbsolutePath();
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);

            var config = plugin.getConfig();
            boolean walMode = config.getBoolean("database.wal-mode", true);
            String syncMode = config.getString("database.synchronous", "NORMAL");
            int busyTimeout = config.getInt("database.busy-timeout", 5000);

            try (Statement stmt = connection.createStatement()) {
                if (walMode) stmt.execute("PRAGMA journal_mode=WAL");
                stmt.execute("PRAGMA synchronous=" + syncMode);
                stmt.execute("PRAGMA busy_timeout=" + busyTimeout);
                stmt.execute("PRAGMA foreign_keys=ON");
                stmt.execute("PRAGMA cache_size=-2000"); // 2MB 缓存

                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS player_positions (
                        uuid TEXT PRIMARY KEY,
                        player_name TEXT NOT NULL,
                        x REAL NOT NULL,
                        y REAL NOT NULL,
                        z REAL NOT NULL,
                        world TEXT NOT NULL,
                        last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )
                """);
            }

            // 加载缓存
            loadCache();

            // 启动批量写入消费者线程
            startBatchWriter();

            debug("数据库初始化完成 (WAL=" + walMode + ", sync=" + syncMode + ")");

        } catch (ClassNotFoundException e) {
            throw new RuntimeException("SQLite JDBC 驱动未找到", e);
        } catch (SQLException e) {
            throw new RuntimeException("数据库初始化失败", e);
        }
    }

    private void loadCache() {
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM player_positions")) {

            positionCache.clear();
            int count = 0;
            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("uuid"));
                positionCache.put(uuid, new PlayerPosition(
                        uuid,
                        rs.getString("player_name"),
                        rs.getDouble("x"),
                        rs.getDouble("y"),
                        rs.getDouble("z"),
                        rs.getString("world"),
                        rs.getTimestamp("last_updated")
                ));
                count++;
            }
            debug("从数据库加载 " + count + " 条位置记录到缓存");
        } catch (SQLException e) {
            Bukkit.getLogger().log(Level.WARNING, "加载位置缓存失败", e);
        }
    }

    /**
     * 启动批量写入消费者，定期将队列中的数据批量写入数据库
     */
    private void startBatchWriter() {
        Thread writerThread = new Thread(() -> {
            List<PlayerPosition> batch = new ArrayList<>(64);
            while (running || !writeQueue.isEmpty()) {
                try {
                    // 等待队列中有数据，超时1秒后检查是否需要退出
                    PlayerPosition pos = writeQueue.poll(1, TimeUnit.SECONDS);
                    if (pos != null) {
                        batch.add(pos);
                        // 收集最多64条或等待100ms后批量写入
                        writeQueue.drainTo(batch, 64);
                    }

                    if (!batch.isEmpty()) {
                        flushBatch(batch);
                        batch.clear();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (SQLException e) {
                    Bukkit.getLogger().log(Level.WARNING, "批量写入位置数据失败", e);
                }
            }
            // 退出前写入剩余数据
            try {
                List<PlayerPosition> remaining = new ArrayList<>();
                writeQueue.drainTo(remaining);
                if (!remaining.isEmpty()) {
                    flushBatch(remaining);
                }
            } catch (SQLException e) {
                Bukkit.getLogger().log(Level.WARNING, "关闭时写入剩余数据失败", e);
            }
        }, "PMSWorld-Batch-Writer");
        writerThread.setDaemon(true);
        writerThread.start();
    }

    private void flushBatch(List<PlayerPosition> batch) throws SQLException {
        String sql = """
            INSERT INTO player_positions (uuid, player_name, x, y, z, world, last_updated)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(uuid) DO UPDATE SET
                player_name = excluded.player_name,
                x = excluded.x, y = excluded.y, z = excluded.z,
                world = excluded.world, last_updated = excluded.last_updated
        """;

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            connection.setAutoCommit(false);
            for (PlayerPosition pos : batch) {
                pstmt.setString(1, pos.uuid().toString());
                pstmt.setString(2, pos.playerName());
                pstmt.setDouble(3, pos.x());
                pstmt.setDouble(4, pos.y());
                pstmt.setDouble(5, pos.z());
                pstmt.setString(6, pos.world());
                pstmt.setTimestamp(7, pos.lastUpdated());
                pstmt.addBatch();
            }
            pstmt.executeBatch();
            connection.commit();
            debug("批量写入 " + batch.size() + " 条位置数据");
        } finally {
            connection.setAutoCommit(true);
        }
    }

    /**
     * 获取配置的记录世界（唯一）
     */
    public World getRecordWorld() {
        String worldName = plugin.getConfig().getString("record-world", "");
        if (worldName == null || worldName.isEmpty()) {
            return Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null && !Bukkit.getWorlds().isEmpty()) {
            return Bukkit.getWorlds().get(0);
        }
        return world;
    }

    /**
     * 获取配置的记录世界名称
     */
    public String getRecordWorldName() {
        World world = getRecordWorld();
        return world != null ? world.getName() : "world";
    }

    /**
     * 检查指定世界是否为配置的记录世界
     */
    public boolean isRecordWorld(World world) {
        if (world == null) return false;
        World recordWorld = getRecordWorld();
        return recordWorld != null && world.equals(recordWorld);
    }

    /**
     * 记录玩家位置（仅更新缓存 + 加入写入队列，不阻塞调用线程）
     */
    public void savePosition(Player player) {
        if (player == null) return;

        Location loc = player.getLocation();
        World world = loc.getWorld();
        if (world == null) return;

        // 只记录配置中允许的世界
        if (!isRecordWorld(world)) return;

        PlayerPosition pos = new PlayerPosition(
                player.getUniqueId(),
                player.getName(),
                loc.getX(),
                loc.getY(),
                loc.getZ(),
                world.getName(),
                new Timestamp(System.currentTimeMillis())
        );

        // 仅更新缓存（ConcurrentHashMap 操作是线程安全的）
        positionCache.put(player.getUniqueId(), pos);

        // 加入写入队列，由独立线程批量处理
        writeQueue.offer(pos);
    }

    /**
     * 立即保存所有缓存中的数据到数据库（用于手动保存命令）
     */
    public CompletableFuture<Integer> saveAllNow() {
        return CompletableFuture.supplyAsync(() -> {
            List<PlayerPosition> all = new ArrayList<>(positionCache.values());
            if (all.isEmpty()) return 0;
            try {
                flushBatch(all);
                return all.size();
            } catch (SQLException e) {
                Bukkit.getLogger().log(Level.WARNING, "立即保存所有位置数据失败", e);
                return 0;
            }
        }, dbExecutor);
    }

    public PlayerPosition getPosition(UUID uuid) {
        return positionCache.get(uuid);
    }

    public Map<UUID, PlayerPosition> getAllPositions() {
        return Collections.unmodifiableMap(positionCache);
    }

    public void close() {
        running = false;
        try {
            // 等待批量写入线程完成
            if (connection != null && !connection.isClosed()) {
                // 先写入队列中剩余的数据
                List<PlayerPosition> remaining = new ArrayList<>();
                writeQueue.drainTo(remaining);
                if (!remaining.isEmpty()) {
                    flushBatch(remaining);
                }
                connection.close();
            }
        } catch (SQLException e) {
            Bukkit.getLogger().log(Level.WARNING, "关闭数据库失败", e);
        }
        dbExecutor.shutdown();
        try {
            if (!dbExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                dbExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            dbExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public void debug(String message) {
        if (plugin.getConfig().getBoolean("debug", false)) {
            plugin.getLogger().info("[DEBUG] " + message);
        }
    }

    public record PlayerPosition(
            UUID uuid,
            String playerName,
            double x,
            double y,
            double z,
            String world,
            Timestamp lastUpdated
    ) {
        public String formatXYZ() {
            return String.format("%d %d %d", (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
        }

        public Location toLocation() {
            World worldObj = Bukkit.getWorld(world);
            if (worldObj == null) return null;
            return new Location(worldObj, x, y, z);
        }
    }
}
