package org.zexnocs.teanekopapermc.core.handler.interfaces;

import org.springframework.context.ApplicationContext;

/**
 * 标识一个 Paper 插件的核心处理器。
 *
 * @author zExNocs
 * @date 2026/09/11
 * @since paperMC-1.0.0alpha
 */
public interface ITeaNekoCoreHandler {
    /**
     * 启动核心处理器。
     * <p>
     * 该方法在 Paper 插件的 {@code onEnable()} 方法中调用。
     * <p>
     * 该方法同时会处理 paper 到 tea neko core 之间的初始化，例如指令注入。
     */
    void start();

    /**
     * 关闭核心处理器。
     */
    void close();

    /**
     * 获取 spring boot application 的上下文。
     *
     * @return spring boot application 的上下文
     */
    ApplicationContext getApplicationContext();
}
