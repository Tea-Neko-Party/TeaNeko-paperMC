package org.zexnocs.teanekopapermc.command;

import java.util.List;

/**
 * 允许 Minecraft 指令 Bean 自定义 Paper 参数补全。
 * <p>
 * 参数补全会在服务器主线程执行，实现中不得直接访问数据库或执行阻塞操作。
 *
 * @author zExNocs
 * @date 2026/09/10
 * @since paperMC-1.0.0alpha
 */
public interface IPaperCommandTabCompleter {
    /**
     * 生成当前输入对应的补全项。
     *
     * @param context Paper 指令上下文
     * @return 补全项；返回空列表表示没有补全
     */
    List<String> complete(PaperCommandContext context);
}
