package org.zexnocs.teaneko.mc.core.command.completion.interfaces;

import org.zexnocs.teaneko.mc.core.command.completion.PaperCommandCompletionContext;

import java.util.Collection;

/**
 * 定义一个由 Spring 管理的 Paper 指令参数补全提供器。
 * <p>
 * 补全回调位于服务器主线程，实现不得访问数据库或执行阻塞操作。
 *
 * @author zExNocs
 * @date 2026/09/12
 * @since paperMC-1.0.0alpha
 */
public interface IPaperCommandCompletionProvider {
    /**
     * 返回当前参数可用的原始候选值。
     * <p>
     * 调用方负责统一执行前缀过滤、去重和排序。
     *
     * @param context 当前参数补全上下文
     * @return 原始候选值
     */
    Collection<String> complete(PaperCommandCompletionContext context);
}
