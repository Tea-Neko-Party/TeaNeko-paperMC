package org.zexnocs.teanekopapermc;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

/**
 * TeaNeko Paper 插件入口，负责管理插件生命周期与基础状态指令。
 * <p>
 * core 模块以库的形式保留，不在此处启动 Spring Boot 应用，避免插件在服务器中创建 Web 服务、
 * 数据库自动配置或独立应用生命周期。
 *
 * @author zExNocs
 * @date 2026/09/09
 * @since paperMC-1.0.0alpha
 */
public final class TeaNekoPaperPlugin extends JavaPlugin {

    /**
     * 启用插件并初始化默认配置。
     */
    @Override
    public void onEnable() {
        saveDefaultConfig();
        getLogger().info("TeaNeko Paper 插件已启用，core 库可供使用。");
    }

    /**
     * 停用插件并取消由当前插件注册的 Bukkit 调度任务。
     */
    @Override
    public void onDisable() {
        getServer().getScheduler().cancelTasks(this);
        getLogger().info("TeaNeko Paper 插件已停用。");
    }

    /**
     * 处理 TeaNeko 的基础状态指令。
     *
     * @param sender 指令发送者
     * @param command 被执行的 Bukkit 指令
     * @param label 实际使用的指令别名
     * @param args 指令参数
     * @return 已处理时返回 {@code true}，否则返回 {@code false}
     */
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String @NotNull [] args) {
        if (!command.getName().equalsIgnoreCase("teaneko")) {
            return false;
        }

        sender.sendRichMessage("<green>TeaNeko Paper</green> <gray>v" + getPluginMeta().getVersion()
                + " — core 已就绪。</gray>");
        return true;
    }
}
