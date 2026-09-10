package org.zexnocs.teanekopapermc;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.zexnocs.teanekopapermc.core.TeaNekoCoreInjection;

/**
 * TeaNeko Paper 插件入口，负责衔接 Paper 生命周期与 TeaNeko Core。
 *
 * @author zExNocs
 * @date 2026/09/10
 * @since paperMC-1.0.0alpha
 * @see TeaNekoCoreInjection
 */
public final class TeaNekoPaperPlugin extends JavaPlugin {
    private TeaNekoCoreInjection coreInjection;

    /**
     * 初始化 Bukkit 默认配置，并启动 TeaNeko Core 的 Spring Boot 应用上下文。
     */
    @Override
    public void onEnable() {
        saveDefaultConfig();
        coreInjection = new TeaNekoCoreInjection(this);

        try {
            coreInjection.start();
            getLogger().info("TeaNeko Paper 插件与 Spring Boot 应用上下文已启用。");
        } catch (RuntimeException exception) {
            closeCoreInjection();
            throw exception;
        }
    }

    /**
     * 关闭 TeaNeko Core 的 Spring Boot 应用上下文，并取消 Bukkit 调度任务。
     */
    @Override
    public void onDisable() {
        closeCoreInjection();
        getServer().getScheduler().cancelTasks(this);
        getLogger().info("TeaNeko Paper 插件已停用。");
    }

    /**
     * 安全关闭并释放 TeaNeko Core 上下文管理器。
     */
    private void closeCoreInjection() {
        if (coreInjection != null) {
            coreInjection.close();
            coreInjection = null;
        }
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
