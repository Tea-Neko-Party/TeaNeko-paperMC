package org.zexnocs.teanekopapermc.command.interfaces;

import org.bukkit.plugin.java.JavaPlugin;
import org.zexnocs.teanekopapermc.command.PaperCommandContext;

/**
 * 定义 Spring Core 指令到 Paper 指令系统的注册与分发能力。
 *
 * @author zExNocs
 * @date 2026/09/10
 * @since paperMC-1.0.0alpha
 */
public interface IPaperCommandService {
    /**
     * 扫描并绑定全部标注 {@code @TeaNekoMCCommand} 的 Core 指令。
     *
     * @param plugin Paper 插件实例
     */
    void registerAll(JavaPlugin plugin);

    /**
     * 将一次 Paper 指令调用交给 Core 指令分发链。
     *
     * @param context Paper 指令上下文
     */
    void dispatch(PaperCommandContext context);
}
