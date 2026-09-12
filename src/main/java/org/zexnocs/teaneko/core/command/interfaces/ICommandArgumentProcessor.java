package org.zexnocs.teaneko.core.command.interfaces;

import org.zexnocs.teaneko.core.command.CommandData;
import org.zexnocs.teaneko.core.command.exception.CommandDataTypeMismatchException;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.List;

/**
 * 指令参数处理器接口，用于处理指令参数并将其转换为方法参数。
 *
 * @author zExNocs
 * @date 2026/09/12
 * @since paperMC-1.0.0alpha
 */
public interface ICommandArgumentProcessor {
    /**
     * 处理指令参数。
     * @param method 指令方法
     * @param args 指令参数
     * @param commandData 指令数据。如果有参数是 CommandData 的话，可以直接使用
     * @return 处理后的参数
     */
    Object[] process(Method method, String[] args, CommandData<?> commandData) throws CommandDataTypeMismatchException;

    /**
     * 根据光标前已经提交的参数，查找所有可能接收当前输入的 Java 参数。
     *
     * @param method 指令方法
     * @param committedArgs 光标前已经提交的参数
     * @param commandData 当前指令数据
     * @return 可接收当前输入的参数集合
     * @throws CommandDataTypeMismatchException CommandData 泛型与当前客户端不兼容时抛出
     */
    List<Parameter> findCompletionParameters(Method method,
                                             String[] committedArgs,
                                             CommandData<?> commandData)
            throws CommandDataTypeMismatchException;
}
