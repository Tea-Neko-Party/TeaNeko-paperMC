package org.zexnocs.teaneko.core.command;

import org.springframework.stereotype.Service;
import org.zexnocs.teaneko.core.command.interfaces.ICommandArgumentTypeHandler;

import java.util.*;

/**
 * 保存 Spring 中注册的指令参数类型处理器，并校验类型声明唯一性。
 *
 * @author zExNocs
 * @date 2026/09/12
 * @since paperMC-1.0.0alpha
 * @see ICommandArgumentTypeHandler
 */
@Service
public final class CommandArgumentTypeHandlerRegistry {
    private final Map<Class<?>, ICommandArgumentTypeHandler<?>> handlers;

    /**
     * 创建类型处理器注册表。
     *
     * @param registeredHandlers Spring 中全部类型处理器
     * @throws IllegalStateException 多个处理器声明同一目标类型时抛出
     */
    public CommandArgumentTypeHandlerRegistry(
            List<ICommandArgumentTypeHandler<?>> registeredHandlers) {
        Map<Class<?>, ICommandArgumentTypeHandler<?>> collected = new LinkedHashMap<>();
        for (ICommandArgumentTypeHandler<?> handler : registeredHandlers) {
            Class<?> targetType = Objects.requireNonNull(
                    handler.targetType(),
                    "指令参数类型处理器的目标类型不能为空。"
            );
            ICommandArgumentTypeHandler<?> previous = collected.putIfAbsent(targetType, handler);
            if (previous != null) {
                throw new IllegalStateException("指令参数类型 " + targetType.getName()
                        + " 存在多个处理器：" + previous.getClass().getName()
                        + "、" + handler.getClass().getName());
            }
        }
        handlers = Map.copyOf(collected);
    }

    /**
     * 获取精确目标类型对应的处理器。
     *
     * @param targetType 目标参数类型
     * @return 已注册的处理器
     */
    public Optional<ICommandArgumentTypeHandler<?>> findHandler(Class<?> targetType) {
        return Optional.ofNullable(handlers.get(targetType));
    }
}
