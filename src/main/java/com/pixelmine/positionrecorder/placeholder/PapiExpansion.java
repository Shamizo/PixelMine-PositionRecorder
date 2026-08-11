package com.pixelmine.positionrecorder.placeholder;

import com.pixelmine.positionrecorder.PositionRecorderPlugin;
import com.pixelmine.positionrecorder.database.PositionDatabase;
import com.pixelmine.positionrecorder.util.DefaultPosition;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PapiExpansion extends PlaceholderExpansion {

    private final PositionRecorderPlugin plugin;
    private final PositionDatabase database;
    private final DefaultPosition defaultPosition;

    public PapiExpansion(PositionRecorderPlugin plugin, PositionDatabase database) {
        this.plugin = plugin;
        this.database = database;
        this.defaultPosition = new DefaultPosition(plugin, database);
    }

    @Override
    public @NotNull String getIdentifier() {
        return "pmsworld";
    }

    @Override
    public @NotNull String getAuthor() {
        return "PixelMine";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) return "";

        var pos = database.getPosition(player.getUniqueId());
        boolean hasRecord = (pos != null);

        return switch (params.toLowerCase()) {
            // %pmsworld_pos% - 有记录输出记录，无记录输出空
            case "pos" -> hasRecord ? pos.formatXYZ() : "";

            // %pmsworld_default_pos% - 有记录输出记录，无记录输出默认坐标
            case "default_pos" -> hasRecord ? pos.formatXYZ() : defaultPosition.formatXYZ();

            // %pmsworld_x% / y / z - 有记录输出值，无记录输出空
            case "x" -> hasRecord ? String.valueOf((int) Math.floor(pos.x())) : "";
            case "y" -> hasRecord ? String.valueOf((int) Math.floor(pos.y())) : "";
            case "z" -> hasRecord ? String.valueOf((int) Math.floor(pos.z())) : "";

            // %pmsworld_default_x% / y / z - 有记录输出记录值，无记录输出默认值
            case "default_x" -> hasRecord ? String.valueOf((int) Math.floor(pos.x())) : String.valueOf((int) Math.floor(defaultPosition.getX()));
            case "default_y" -> hasRecord ? String.valueOf((int) Math.floor(pos.y())) : String.valueOf((int) Math.floor(defaultPosition.getY()));
            case "default_z" -> hasRecord ? String.valueOf((int) Math.floor(pos.z())) : String.valueOf((int) Math.floor(defaultPosition.getZ()));

            case "player" -> hasRecord ? pos.playerName() : player.getName();
            case "status" -> hasRecord ? "recorded" : "default";

            default -> null;
        };
    }
}
