package org.zexnocs.teanekopapermc.core.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bukkit.plugin.java.JavaPlugin;
import org.springframework.context.ApplicationContext;
import org.zexnocs.teanekopapermc.core.handler.interfaces.ITeaNekoCoreHandler;
import org.zexnocs.teanekopapermc.core.initializer.TeaNekoInitializerScanner;
import org.zexnocs.teanekopapermc.core.logger.JavaLogger;

/**
 * 标识 PaperMC 服务端的 TeaNekoCore 核心处理器。
 *
 * @author zExNocs
 * @date 2026/09/11
 * @since paperMC-1.0.0alpha
 */
@Slf4j
@RequiredArgsConstructor
public class TeaNekoCoreHandler implements ITeaNekoCoreHandler {

    /// 所隶属的 Paper 插件实例。
    private final JavaPlugin plugin;

    /// Core 注入器
    private TeaNekoCoreInjection injection = null;

    /**
     * 启动核心处理器。
     * <p>
     * 该方法在 Paper 插件的 {@code onEnable()} 方法中调用。
     * <p>
     * 该方法同时会处理 paper 到 tea neko core 之间的初始化，例如指令注入。
     */
    @Override
    public void start() {
        // 开启注入器
        injection = new TeaNekoCoreInjection(plugin);
        injection.start();
        // 优先初始化 logger
        _initializeLogger(injection, plugin);
        // 初始化所有初始化器
        _initializeAllInitializers(injection, plugin);
    }

    /**
     * 关闭核心处理器。
     */
    @Override
    public void close() {
        // 安全关闭注入器
        if(injection != null) {
            injection.close();
            injection = null;
        }
    }

    /**
     * 获取 spring boot application 的上下文。
     *
     * @return spring boot application 的上下文
     */
    @Override
    public ApplicationContext getApplicationContext() {
        return injection.getApplicationContext();
    }

    /**
     * 初始化 logger。
     */
    private static void _initializeLogger(TeaNekoCoreInjection injection, JavaPlugin plugin) {
        var logger = injection.getBean(JavaLogger.class);
        logger.setLogger(plugin.getLogger());
    }

    /**
     * 初始化所有初始化器
     */
    private static void _initializeAllInitializers(TeaNekoCoreInjection injection, JavaPlugin plugin) {
        var logger = injection.getBean(JavaLogger.class);
        var initializerScanner = injection.getBean(TeaNekoInitializerScanner.class);
        var initializerSet = initializerScanner.getInitializerSet();
        logger.info(TeaNekoCoreHandler.class.getSimpleName(),
                "发现 " + initializerSet.size() + " 个初始化器，正在初始化...");
        int finishedCount = 0;
        for(var initializer : initializerSet) {
            try {
                initializer.initialize(plugin);
                finishedCount++;
            } catch (Exception ex) {
                logger.error(TeaNekoCoreHandler.class.getName(),
                        "初始化器 " + initializer.getClass().getName() + " 初始化失败",
                        ex);
            }
        }
        logger.info(TeaNekoCoreHandler.class.getName(),
                "初始化完成，共初始化 " + finishedCount + " 个初始化器。");
    }
}
