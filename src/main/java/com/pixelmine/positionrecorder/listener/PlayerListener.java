package com.pixelmine.positionrecorder.listener;

import com.pixelmine.positionrecorder.PositionRecorderPlugin;
import com.pixelmine.positionrecorder.database.PositionDatabase;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerListener implements Listener {

    private final PositionRecorderPlugin plugin;
    private final PositionDatabase database;

    public PlayerListener(PositionRecorderPlugin plugin, PositionDatabase database) {
        this.plugin = plugin;
        this.database = database;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();
        database.debug("玩家 " + player.getName() + " 加入服务器");
        // 玩家加入时立即记录一次位置（如果在主世界）
        Bukkit.getRegionScheduler().execute(plugin, player.getLocation(), () -> {
            database.savePosition(player);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        var player = event.getPlayer();
        database.debug("玩家 " + player.getName() + " 离开服务器");
        // 玩家退出时记录最后位置
        Bukkit.getRegionScheduler().execute(plugin, player.getLocation(), () -> {
            database.savePosition(player);
        });
    }
}
