package org.zexnocs.teaneko.mc.core.handler;

import lombok.RequiredArgsConstructor;
import org.bukkit.plugin.java.JavaPlugin;
import org.springframework.context.ApplicationContext;
import org.zexnocs.teaneko.mc.core.handler.interfaces.ITeaNekoCoreHandler;
import org.zexnocs.teaneko.mc.core.initializer.TeaNekoInitializerScanner;
import org.zexnocs.teaneko.mc.core.initializer.api.ITeaNekoInitializer;
import org.zexnocs.teaneko.mc.core.logger.JavaLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * 标识 PaperMC 服务端的 TeaNekoCore 核心处理器。
 *
 * @author zExNocs
 * @date 2026/09/12
 * @since paperMC-1.0.0alpha
 */
@RequiredArgsConstructor
public class TeaNekoCoreHandler implements ITeaNekoCoreHandler {

    /// 所隶属的 Paper 插件实例。
    private final JavaPlugin plugin;

    /// Core 注入器
    private TeaNekoCoreInjection injection = null;

    /// 已成功完成初始化的实例，关闭时按照相反顺序释放。
    private final List<ITeaNekoInitializer> initializedInitializers = new ArrayList<>();

    /**
     * 启动核心处理器。
     * <p>
     * 该方法在 Paper 插件的 {@code onEnable()} 方法中调用。
     * <p>
     * 该方法同时会处理 paper 到 tea neko core 之间的初始化，例如指令注入。
     */
    @Override
    public synchronized void start() {
        if (injection != null) {
            throw new IllegalStateException("TeaNeko Core 处理器已经启动。");
        }
        // 开启注入器
        injection = new TeaNekoCoreInjection(plugin);
        try {
            injection.start();
            // 优先初始化 logger
            initializeLogger(injection, plugin);
            // 初始化所有初始化器
            initializeAllInitializers(injection, plugin);
        } catch (RuntimeException | Error exception) {
            // 启动过程保持事务性，调用方即使忘记清理也不会遗留已启动的资源。
            close();
            throw exception;
        }
    }

    /**
     * 关闭核心处理器。
     */
    @Override
    public synchronized void close() {
        // 初始化器可能持有不受 Spring 管理的资源，必须在关闭上下文前逆序释放。
        for (int index = initializedInitializers.size() - 1; index >= 0; index--) {
            ITeaNekoInitializer initializer = initializedInitializers.get(index);
            try {
                initializer.close();
            } catch (Exception exception) {
                plugin.getLogger().log(
                        Level.SEVERE,
                        "关闭初始化器 " + initializer.getClass().getName() + " 失败。",
                        exception
                );
            }
        }
        initializedInitializers.clear();

        // 即使个别初始化器释放失败，也必须继续关闭 Spring 上下文。
        if (injection != null) {
            try {
                injection.close();
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.SEVERE, "关闭 Spring Boot 应用上下文失败。", exception);
            } finally {
                injection = null;
            }
        }
    }

    /**
     * 获取 spring boot application 的上下文。
     *
     * @return spring boot application 的上下文
     * @throws IllegalStateException 核心处理器尚未启动或已经关闭时抛出
     */
    @Override
    public ApplicationContext getApplicationContext() {
        if (injection == null) {
            throw new IllegalStateException("TeaNeko Core 处理器尚未启动或已经关闭。");
        }
        return injection.getApplicationContext();
    }

    /**
     * 初始化 logger。
     */
    private static void initializeLogger(TeaNekoCoreInjection injection, JavaPlugin plugin) {
        var logger = injection.getBean(JavaLogger.class);
        logger.setLogger(plugin.getLogger());
    }

    /**
     * 按声明的优先级初始化全部 Paper 初始化器。
     *
     * @param injection 已启动的 Spring 上下文管理器
     * @param plugin 当前 Paper 插件
     * @throws IllegalStateException 必须初始化器执行失败时抛出
     */
    private void initializeAllInitializers(TeaNekoCoreInjection injection, JavaPlugin plugin) {
        var logger = injection.getBean(JavaLogger.class);
        var initializerScanner = injection.getBean(TeaNekoInitializerScanner.class);
        // 显式保证初始化器扫描已经完成，不再只依赖 ReloadService 的隐式启动顺序。
        initializerScanner.init();
        var initializerDefinitions = initializerScanner.getInitializerDefinitions();
        logger.info(TeaNekoCoreHandler.class.getSimpleName(),
                "发现 " + initializerDefinitions.size() + " 个初始化器，正在初始化...");
        int finishedCount = 0;
        for (var definition : initializerDefinitions) {
            ITeaNekoInitializer initializer = definition.initializer();
            try {
                initializer.initialize(plugin);
                initializedInitializers.add(initializer);
                finishedCount++;
            } catch (Exception exception) {
                logger.error(TeaNekoCoreHandler.class.getName(),
                        "初始化器 " + initializer.getClass().getName() + " 初始化失败。类型："
                                + (definition.metadata().required() ? "必须" : "可选"),
                        exception);
                closeFailedInitializer(initializer);
                if (definition.metadata().required()) {
                    throw new IllegalStateException(
                            "必须初始化器 " + initializer.getClass().getName() + " 初始化失败。",
                            exception
                    );
                }
            }
        }
        logger.info(TeaNekoCoreHandler.class.getName(),
                "初始化完成，共初始化 " + finishedCount + " 个初始化器。");
    }

    /**
     * 尝试清理由于初始化中途失败而可能遗留的部分资源。
     *
     * @param initializer 初始化失败的实例
     */
    private void closeFailedInitializer(ITeaNekoInitializer initializer) {
        try {
            initializer.close();
        } catch (Exception closeException) {
            plugin.getLogger().log(
                    Level.SEVERE,
                    "清理初始化失败的实例 " + initializer.getClass().getName() + " 时发生异常。",
                    closeException
            );
        }
    }
}
