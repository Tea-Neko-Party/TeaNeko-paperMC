package org.zexnocs.teaneko.mc.core.command.completion.interfaces;

import org.zexnocs.teaneko.core.command.interfaces.ICommandArgumentTypeHandler;

/**
 * 同时支持参数转换和自动补全的 Paper 特殊参数类型处理器。
 * <p>
 * 新增特殊类型时实现本接口并注册为 Spring Bean，即可同时接入 Core 参数解析和
 * Paper 参数补全，不需要修改中央类型判断代码。
 *
 * @param <T> 目标参数类型
 * @author zExNocs
 * @date 2026/09/12
 * @since paperMC-1.0.0alpha
 * @see ICommandArgumentTypeHandler
 * @see IPaperCommandCompletionProvider
 */
public interface IPaperCommandArgumentTypeHandler<T>
        extends ICommandArgumentTypeHandler<T>, IPaperCommandCompletionProvider {
}
