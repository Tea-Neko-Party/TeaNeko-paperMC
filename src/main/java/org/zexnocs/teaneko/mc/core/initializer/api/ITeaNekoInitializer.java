package org.zexnocs.teaneko.mc.core.initializer.api;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * 定义 TeaNeko 的 Paper 初始化与资源释放生命周期。
 * <p>
 * 自动加载的实现类必须同时标注 {@link TeaNekoInitializer}；该注解已经包含 Spring
 * {@code @Component} 语义，无需重复标注组件注解。
 *
 * @author zExNocs
 * @date 2026/09/12
 * @since paperMC-1.0.0alpha
 */
public interface ITeaNekoInitializer {
    /**
     * 初始化方法，用于在插件启动时进行必要的初始化操作。
     *
     * @param plugin 当前的 JavaPlugin 实例
     * @throws Exception 初始化失败时抛出
     */
    void initialize(JavaPlugin plugin) throws Exception;

    /**
     * 释放初始化阶段创建或注册的资源。
     * <p>
     * 初始化成功的实例会在插件关闭时按照初始化顺序的相反顺序调用本方法；
     * 初始化中途失败时也可能立即调用本方法清理已经创建的部分资源。
     * 无需额外清理资源的初始化器可以使用默认空实现。
     *
     * @throws Exception 资源释放失败时抛出
     */
    default void close() throws Exception {
    }
}
