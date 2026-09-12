package org.zexnocs.teaneko.core.command.interfaces;

import org.zexnocs.teaneko.core.command.CommandData;

import java.util.Optional;

/**
 * 定义可扩展的指令参数类型转换器。
 * <p>
 * 每个实现只处理一个精确的目标类型，并作为 Spring Bean 自动注册。
 *
 * @param <T> 目标参数类型
 * @author zExNocs
 * @date 2026/09/12
 * @since paperMC-1.0.0alpha
 */
public interface ICommandArgumentTypeHandler<T> {
    /**
     * 获取当前处理器负责的精确参数类型。
     *
     * @return 目标参数类型
     */
    Class<T> targetType();

    /**
     * 将输入文本转换为目标参数。
     *
     * @param input 原始参数文本
     * @param commandData 当前指令数据
     * @return 转换后的参数；输入不合法时返回空
     */
    Optional<T> parse(String input, CommandData<?> commandData);
}
