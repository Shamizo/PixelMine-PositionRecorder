package com.pixelmine.positionrecorder.util;

import com.pixelmine.positionrecorder.PositionRecorderPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.logging.Level;

public class HelpFiles {

    private final PositionRecorderPlugin plugin;

    public HelpFiles(PositionRecorderPlugin plugin) {
        this.plugin = plugin;
    }

    public void generate() {
        File folder = plugin.getDataFolder();
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("无法创建插件目录: " + folder.getAbsolutePath());
            return;
        }

        // 帮助文件存放在 help/ 子目录
        File helpDir = new File(folder, "help");
        if (!helpDir.exists() && !helpDir.mkdirs()) {
            plugin.getLogger().warning("无法创建帮助目录: " + helpDir.getAbsolutePath());
            return;
        }

        writeFile(new File(helpDir, "commands.txt"), getCommandsContent());
        writeFile(new File(helpDir, "variables.txt"), getVariablesContent());
        writeFile(new File(helpDir, "permissions.txt"), getPermissionsContent());
    }

    private void writeFile(File file, String content) {
        try {
            if (!file.exists()) {
                Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);
                plugin.getLogger().info("已生成文件: " + file.getName());
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "生成文件失败: " + file.getName(), e);
        }
    }

    private String getCommandsContent() {
        return """
                ============================================
                  PixelMine 主世界位置记录器 - 指令列表
                ============================================

                /pmsworld get
                  - 查看自己的主世界最后位置
                  - 权限: pmsworld.get (默认: true)

                /pmsworld get <玩家>
                  - 查看指定玩家的主世界最后位置
                  - 权限: pmsworld.get.others (默认: op)

                /pmsworld list
                  - 列出所有已记录的玩家位置
                  - 权限: pmsworld.admin (默认: op)

                /pmsworld save
                  - 立即将所有缓存的位置数据写入数据库
                  - 不会刷掉未上线玩家的数据
                  - 权限: pmsworld.save (默认: op)

                /pmsworld setdefault <x> <y> <z>
                  - 设置获取不到玩家数据时返回的默认坐标
                  - 只能在主世界执行
                  - 权限: pmsworld.admin (默认: op)

                /pmsworld reload
                  - 重新加载配置文件
                  - 权限: pmsworld.admin (默认: op)

                别名: /pms, /pmsr
                """;
    }

    private String getVariablesContent() {
        return """
                ============================================
                  PixelMine 位置记录器 - 变量列表
                ============================================
                需要安装 PlaceholderAPI

                %pmsworld_pos%
                  - 有记录时返回玩家最后记录坐标: 128 64 1
                  - 无记录时输出空字符串

                %pmsworld_default_pos%
                  - 有记录时返回玩家最后记录坐标: 128 64 1
                  - 无记录时返回默认坐标: 0 64 0

                %pmsworld_x%  /  %pmsworld_y%  /  %pmsworld_z%
                  - 有记录时返回单个坐标值 (整数)
                  - 无记录时输出空字符串

                %pmsworld_default_x%  /  %pmsworld_default_y%  /  %pmsworld_default_z%
                  - 有记录时返回玩家坐标值
                  - 无记录时返回默认坐标值

                %pmsworld_player%
                  - 玩家名称

                %pmsworld_status%
                  - 状态: "recorded" 已记录 / "default" 默认值

                示例用法 (大厅插件):
                  /mvtp %player_name% world %pmsworld_default_x% %pmsworld_default_y% %pmsworld_default_z%
                """;
    }

    private String getPermissionsContent() {
        return """
                ============================================
                  PixelMine 主世界位置记录器 - 权限列表
                ============================================

                pmsworld.admin
                  - 管理权限 (reload, setdefault, list, save)
                  - 默认: op

                pmsworld.get
                  - 查看自己位置权限
                  - 默认: true

                pmsworld.get.others
                  - 查看他人位置权限
                  - 默认: op

                pmsworld.save
                  - 手动保存所有数据权限
                  - 默认: op
                """;
    }
}
