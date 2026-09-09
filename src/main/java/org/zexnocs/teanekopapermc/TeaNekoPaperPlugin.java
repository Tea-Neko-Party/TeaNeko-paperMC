package org.zexnocs.teanekopapermc;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.DefaultResourceLoader;
import org.zexnocs.teanekoapp.TeaNekoAppApplication;

import java.io.File;

/**
 * TeaNeko Paper 插件入口，负责管理插件生命周期与基础状态指令。
 * <p>
 * 该类创建 Spring Boot 应用上下文，使 core 服务与应用交互层在 Paper 生命周期内运行。
 *
 * @author zExNocs
 * @date 2026/09/09
 * @since paperMC-1.0.0alpha
 */
public final class TeaNekoPaperPlugin extends JavaPlugin {
    private ConfigurableApplicationContext applicationContext;

    /**
     * 启用插件、初始化默认配置并启动 Spring Boot 应用上下文。
     * <p>
     * 应用上下文以 Servlet 模式启动，以便加载 {@code teanekocore} 的服务、
     * {@code teanekoapp} 的客户端扫描器及 WebSocket 外部交互端点。
     */
    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("application.properties", false);

        try {
            applicationContext = startApplicationContext();
            getLogger().info("TeaNeko Paper 插件与 Spring Boot 应用上下文已启用。");
        } catch (RuntimeException exception) {
            closeApplicationContext();
            throw exception;
        }
    }

    /**
     * 使用插件类加载器创建 Spring Boot 应用上下文。
     * <p>
     * Paper 的服务器线程上下文类加载器不会暴露插件 JAR。启动期间必须显式指定插件类加载器，
     * 否则 Spring Boot 无法读取 {@code META-INF/spring} 下的自动配置资源。
     *
     * @return 已完成组件扫描和自动配置的应用上下文
     * @see DefaultResourceLoader
     */
    private ConfigurableApplicationContext startApplicationContext() {
        ClassLoader pluginClassLoader = getClass().getClassLoader();
        Thread currentThread = Thread.currentThread();
        ClassLoader originalClassLoader = currentThread.getContextClassLoader();

        currentThread.setContextClassLoader(pluginClassLoader);
        try {
            return new SpringApplicationBuilder(TeaNekoAppApplication.class)
                    .resourceLoader(new DefaultResourceLoader(pluginClassLoader))
                    .web(WebApplicationType.SERVLET)
                    .registerShutdownHook(false)
                    .run(
                            "--spring.config.additional-location=" + getPluginDataLocation(),
                            "--spring.main.register-shutdown-hook=false",
                            "--spring.main.banner-mode=off",
                            "--teaneko.paper.data-directory=" + getDataFolder().getAbsolutePath().replace('\\', '/'),
                            "--teaneko.plugin.version=" + getPluginMeta().getVersion()
                    );
        } finally {
            currentThread.setContextClassLoader(originalClassLoader);
        }
    }

    /**
     * 停用插件，先关闭 Spring Boot 应用上下文，再取消 Bukkit 调度任务。
     * <p>
     * Spring 上下文关闭时会依次停止嵌入式 Web 服务、JPA 连接池、定时任务和其余 Bean。
     */
    @Override
    public void onDisable() {
        closeApplicationContext();
        getServer().getScheduler().cancelTasks(this);
        getLogger().info("TeaNeko Paper 插件已停用。");
    }

    /**
     * 获取插件数据目录对应的 Spring 外部配置位置。
     *
     * @return 带有 {@code optional:} 前缀的文件配置位置
     */
    private String getPluginDataLocation() {
        File dataFolder = getDataFolder();
        return "optional:" + dataFolder.toURI();
    }

    /**
     * 安全关闭已创建的 Spring Boot 应用上下文。
     */
    private void closeApplicationContext() {
        if (applicationContext != null) {
            applicationContext.close();
            applicationContext = null;
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
