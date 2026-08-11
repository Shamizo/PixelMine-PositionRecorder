package com.pixelmine.positionrecorder;

import com.pixelmine.positionrecorder.command.PmsWorldCommand;
import com.pixelmine.positionrecorder.database.PositionDatabase;
import com.pixelmine.positionrecorder.listener.PlayerListener;
import com.pixelmine.positionrecorder.placeholder.PapiExpansion;
import com.pixelmine.positionrecorder.util.HelpFiles;
import org.bukkit.plugin.java.JavaPlugin;

public class PositionRecorderPlugin extends JavaPlugin {

    private PositionDatabase database;
    private io.papermc.paper.threadedregions.scheduler.ScheduledTask recordTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        // 初始化数据库
        database = new PositionDatabase(this);
        database.init();

        // 生成说明文件
        new HelpFiles(this).generate();

        // 注册监听器
        getServer().getPluginManager().registerEvents(new PlayerListener(this, database), this);

        // 注册命令
        var command = new PmsWorldCommand(this, database);
        getCommand("pmsworld").setExecutor(command);
        getCommand("pmsworld").setTabCompleter(command);

        // 启动定时记录任务（从配置读取间隔）
        int intervalMinutes = getConfig().getInt("record-interval", 2);
        long intervalTicks = intervalMinutes * 60L * 20L;

        recordTask = getServer().getGlobalRegionScheduler().runAtFixedRate(
                this,
                (task) -> recordAllPlayers(),
                intervalTicks,
                intervalTicks
        );

        database.debug("定时记录任务已启动，间隔: " + intervalMinutes + " 分钟 (" + intervalTicks + " ticks)");
        database.debug("记录世界: " + database.getRecordWorldName());

        // 注册 PlaceholderAPI 扩展
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new PapiExpansion(this, database).register();
            getLogger().info("PlaceholderAPI 扩展已注册");
        }

        getLogger().info("PixelMine 主世界位置记录器已启用 (记录间隔: " + intervalMinutes + " 分钟)");
    }

    @Override
    public void onDisable() {
        if (recordTask != null) {
            recordTask.cancel();
        }
        if (database != null) {
            database.close();
        }
        getLogger().info("PixelMine 主世界位置记录器已禁用");
    }

    public PositionDatabase getDatabase() {
        return database;
    }

    private void recordAllPlayers() {
        var onlinePlayers = getServer().getOnlinePlayers();
        if (onlinePlayers.isEmpty()) return;

        database.debug("开始记录 " + onlinePlayers.size() + " 名在线玩家的位置");

        for (org.bukkit.entity.Player player : onlinePlayers) {
            // Folia: 每个玩家在自己的实体区域调度
            getServer().getRegionScheduler().execute(this, player.getLocation(), () -> {
                database.savePosition(player);
            });
        }
    }
}
