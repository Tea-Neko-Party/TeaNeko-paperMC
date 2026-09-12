package org.zexnocs.teaneko.mc;

import org.bukkit.plugin.java.JavaPlugin;
import org.zexnocs.teaneko.mc.core.handler.TeaNekoCoreHandler;
import org.zexnocs.teaneko.mc.core.handler.interfaces.ITeaNekoCoreHandler;

/**
 * TeaNeko Paper 插件入口，负责衔接 Paper 生命周期与 TeaNeko Core。
 *
 * @author zExNocs
 * @date 2026/09/10
 * @since paperMC-1.0.0alpha
 */
public final class TeaNekoPaperPlugin extends JavaPlugin {

    private ITeaNekoCoreHandler teaNekoCoreHandler = null;


    /**
     * 初始化 Bukkit 默认配置，并启动 TeaNeko Core 的 Spring Boot 应用上下文。
     */
    @Override
    public void onEnable() {
        // 保存初始 config
        saveDefaultConfig();

        // 尝试启动 spring boot 上下文
        try {
            getLogger().info("正在启动 TeaNeko Core 的 Spring Boot 应用上下文...");
            this.teaNekoCoreHandler = new TeaNekoCoreHandler(this);
            // 启动 Spring Boot 应用上下文，并注册所有 Core 指令到 Paper 指令系统。
            this.teaNekoCoreHandler.start();
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
        if (teaNekoCoreHandler != null) {
            teaNekoCoreHandler.close();
            teaNekoCoreHandler = null;
        }
    }
}
