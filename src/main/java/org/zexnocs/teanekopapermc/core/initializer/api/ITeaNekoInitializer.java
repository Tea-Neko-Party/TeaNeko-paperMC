package org.zexnocs.teanekopapermc.core.initializer.api;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * 用于标识一个类是 TeaNeko 的初始化器，以便于使用 PaperMC 相关类进行加载。
 * <br>如需全自动加载，请在类上添加 {@link org.springframework.stereotype.Component} 注解。
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
     */
    void initialize(JavaPlugin plugin) throws Exception;
}
