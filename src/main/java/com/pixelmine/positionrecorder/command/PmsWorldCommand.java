package com.pixelmine.positionrecorder.command;

import com.pixelmine.positionrecorder.PositionRecorderPlugin;
import com.pixelmine.positionrecorder.database.PositionDatabase;
import com.pixelmine.positionrecorder.util.DefaultPosition;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class PmsWorldCommand implements CommandExecutor, TabCompleter {

    private final PositionRecorderPlugin plugin;
    private final PositionDatabase database;
    private final DefaultPosition defaultPosition;

    public PmsWorldCommand(PositionRecorderPlugin plugin, PositionDatabase database) {
        this.plugin = plugin;
        this.database = database;
        this.defaultPosition = new DefaultPosition(plugin, database);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> handleReload(sender);
            case "get" -> handleGet(sender, args);
            case "list" -> handleList(sender);
            case "save" -> handleSave(sender);
            case "setdefault" -> handleSetDefault(sender, args);
            default -> sendHelp(sender);
        }
        return true;
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("pmsworld.admin")) {
            sender.sendMessage(ChatColor.RED + "你没有权限执行此命令");
            return;
        }
        plugin.reloadConfig();
        boolean debug = plugin.getConfig().getBoolean("debug", false);
        sender.sendMessage(ChatColor.GREEN + "配置已重新加载 (debug=" + debug + ")");
    }

    private void handleGet(CommandSender sender, String[] args) {
        if (args.length == 1) {
            // /pmsworld get - 查看自己的位置
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.RED + "此命令只能玩家执行");
                return;
            }
            if (!sender.hasPermission("pmsworld.get")) {
                sender.sendMessage(ChatColor.RED + "你没有权限执行此命令");
                return;
            }

            var pos = database.getPosition(player.getUniqueId());
            if (pos == null) {
                sender.sendMessage(ChatColor.YELLOW + "未找到你的位置记录，使用默认坐标: " + ChatColor.WHITE + defaultPosition.formatXYZ());
            } else {
                sender.sendMessage(ChatColor.GREEN + "你的最后记录位置: " + ChatColor.WHITE + pos.formatXYZ() + ChatColor.GRAY + " (世界: " + pos.world() + ")");
            }
        } else {
            // /pmsworld get <player> - 查看他人位置
            if (!sender.hasPermission("pmsworld.get.others")) {
                sender.sendMessage(ChatColor.RED + "你没有权限执行此命令");
                return;
            }

            OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
            var pos = database.getPosition(target.getUniqueId());
            if (pos == null) {
                sender.sendMessage(ChatColor.YELLOW + "未找到 " + target.getName() + " 的位置记录，使用默认坐标: " + ChatColor.WHITE + defaultPosition.formatXYZ());
            } else {
                sender.sendMessage(ChatColor.GREEN + target.getName() + " 的最后记录位置: " + ChatColor.WHITE + pos.formatXYZ() + ChatColor.GRAY + " (世界: " + pos.world() + ")");
            }
        }
    }

    private void handleList(CommandSender sender) {
        if (!sender.hasPermission("pmsworld.admin")) {
            sender.sendMessage(ChatColor.RED + "你没有权限执行此命令");
            return;
        }

        var positions = database.getAllPositions();
        if (positions.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "暂无位置记录");
            return;
        }

        sender.sendMessage(ChatColor.GREEN + "--- 位置记录 (" + positions.size() + " 名玩家) ---");
        for (var entry : positions.entrySet()) {
            var pos = entry.getValue();
            sender.sendMessage(ChatColor.WHITE + pos.playerName() + ": " + pos.formatXYZ() + ChatColor.GRAY + " (" + pos.world() + ")");
        }
    }

    private void handleSave(CommandSender sender) {
        if (!sender.hasPermission("pmsworld.save") && !sender.hasPermission("pmsworld.admin")) {
            sender.sendMessage(ChatColor.RED + "你没有权限执行此命令");
            return;
        }

        int onlineCount = Bukkit.getOnlinePlayers().size();
        int cacheSize = database.getAllPositions().size();

        sender.sendMessage(ChatColor.GREEN + "正在保存所有玩家位置... (在线: " + onlineCount + ", 缓存: " + cacheSize + ")");

        database.saveAllNow().thenAccept(saved -> {
            sender.sendMessage(ChatColor.GREEN + "已保存 " + saved + " 名玩家的位置数据到数据库");
        }).exceptionally(ex -> {
            sender.sendMessage(ChatColor.RED + "保存失败: " + ex.getMessage());
            return null;
        });
    }

    private void handleSetDefault(CommandSender sender, String[] args) {
        if (!sender.hasPermission("pmsworld.admin")) {
            sender.sendMessage(ChatColor.RED + "你没有权限执行此命令");
            return;
        }

        // 必须在主世界执行
        if (sender instanceof Player player) {
            if (!database.isRecordWorld(player.getWorld())) {
                sender.sendMessage(ChatColor.RED + "此命令只能在已配置记录的世界中使用");
                sender.sendMessage(ChatColor.GRAY + "当前世界: " + player.getWorld().getName());
                return;
            }
        }

        if (args.length == 1) {
            // 显示当前默认坐标
            sender.sendMessage(ChatColor.GREEN + "当前默认坐标: " + ChatColor.WHITE + defaultPosition.formatXYZ());
            return;
        }

        if (args.length != 4) {
            sender.sendMessage(ChatColor.RED + "用法: /pmsworld setdefault <x> <y> <z>");
            return;
        }

        try {
            double x = Double.parseDouble(args[1]);
            double y = Double.parseDouble(args[2]);
            double z = Double.parseDouble(args[3]);

            defaultPosition.set(x, y, z);
            sender.sendMessage(ChatColor.GREEN + "默认坐标已设置为: " + ChatColor.WHITE + defaultPosition.formatXYZ());
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "坐标必须是数字");
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GREEN + "=== PixelMine 主世界位置记录器 ===");
        sender.sendMessage(ChatColor.WHITE + "/pmsworld get - 查看你的位置");
        sender.sendMessage(ChatColor.WHITE + "/pmsworld get <玩家> - 查看指定玩家的位置");
        sender.sendMessage(ChatColor.WHITE + "/pmsworld list - 列出所有位置记录");
        sender.sendMessage(ChatColor.WHITE + "/pmsworld save - 立即保存所有位置到数据库");
        sender.sendMessage(ChatColor.WHITE + "/pmsworld setdefault [x y z] - 设置/查看默认坐标");
        sender.sendMessage(ChatColor.WHITE + "/pmsworld reload - 重新加载配置");
        sender.sendMessage(ChatColor.GRAY + "更多说明请查看插件目录的 commands.txt 和 variables.txt");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String input = args[0].toLowerCase();
            if ("reload".startsWith(input)) completions.add("reload");
            if ("get".startsWith(input)) completions.add("get");
            if ("list".startsWith(input)) completions.add("list");
            if ("save".startsWith(input)) completions.add("save");
            if ("setdefault".startsWith(input)) completions.add("setdefault");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("get")) {
            String input = args[1].toLowerCase();
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                if (onlinePlayer.getName().toLowerCase().startsWith(input)) {
                    completions.add(onlinePlayer.getName());
                }
            }
            for (OfflinePlayer offlinePlayer : Bukkit.getOfflinePlayers()) {
                if (offlinePlayer.getName() != null && offlinePlayer.getName().toLowerCase().startsWith(input)) {
                    completions.add(offlinePlayer.getName());
                }
            }
        } else if (args.length >= 2 && args[0].equalsIgnoreCase("setdefault")) {
            // 自动补全玩家当前坐标
            if (sender instanceof Player player) {
                String input = args[args.length - 1];
                if (args.length == 2) {
                    completions.add(String.valueOf((int) player.getLocation().getX()));
                } else if (args.length == 3) {
                    completions.add(String.valueOf((int) player.getLocation().getY()));
                } else if (args.length == 4) {
                    completions.add(String.valueOf((int) player.getLocation().getZ()));
                }
            }
        }

        return completions;
    }
}
