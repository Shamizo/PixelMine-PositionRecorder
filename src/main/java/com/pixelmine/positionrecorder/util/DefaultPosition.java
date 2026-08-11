package com.pixelmine.positionrecorder.util;

import com.pixelmine.positionrecorder.PositionRecorderPlugin;
import com.pixelmine.positionrecorder.database.PositionDatabase;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * 默认坐标服务 - 当玩家无位置记录时返回
 */
public class DefaultPosition {

    private final PositionRecorderPlugin plugin;
    private final PositionDatabase database;

    public DefaultPosition(PositionRecorderPlugin plugin, PositionDatabase database) {
        this.plugin = plugin;
        this.database = database;
    }

    /**
     * 获取默认坐标（作为 Location 对象）
     */
    public Location getAsLocation() {
        double x = plugin.getConfig().getDouble("default-position.x", 0);
        double y = plugin.getConfig().getDouble("default-position.y", 64);
        double z = plugin.getConfig().getDouble("default-position.z", 0);

        World world = database.getRecordWorld();
        if (world == null && !Bukkit.getWorlds().isEmpty()) {
            world = Bukkit.getWorlds().get(0);
        }
        return new Location(world, x, y, z);
    }

    /**
     * 获取默认坐标的 XYZ 字符串
     */
    public String formatXYZ() {
        double x = plugin.getConfig().getDouble("default-position.x", 0);
        double y = plugin.getConfig().getDouble("default-position.y", 64);
        double z = plugin.getConfig().getDouble("default-position.z", 0);
        return String.format("%d %d %d", (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
    }

    /**
     * 设置默认坐标
     */
    public void set(double x, double y, double z) {
        plugin.getConfig().set("default-position.x", x);
        plugin.getConfig().set("default-position.y", y);
        plugin.getConfig().set("default-position.z", z);
        plugin.saveConfig();
        plugin.getLogger().info("默认坐标已更新为: " + formatXYZ());
    }

    public double getX() {
        return plugin.getConfig().getDouble("default-position.x", 0);
    }

    public double getY() {
        return plugin.getConfig().getDouble("default-position.y", 64);
    }

    public double getZ() {
        return plugin.getConfig().getDouble("default-position.z", 0);
    }
}
