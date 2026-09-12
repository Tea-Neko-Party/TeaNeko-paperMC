package org.zexnocs.teaneko.mc.core.command.completion;

import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.zexnocs.teaneko.mc.core.command.PaperCommandContext;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.List;
import java.util.Objects;

/**
 * 保存一次具体方法参数的 Paper 自动补全上下文。
 *
 * @param commandContext 原始 Paper 指令上下文
 * @param commandBean 当前指令 Spring Bean
 * @param method 当前指令方法
 * @param parameter 当前待补全参数
 * @param parameterIndex 当前参数在方法中的索引
 * @param subCommand 当前子指令名称；默认方法使用空字符串
 * @param currentInput 光标所在参数的当前文本
 * @param committedArguments 光标前已经提交给当前方法的参数
 * @author zExNocs
 * @date 2026/09/12
 * @since paperMC-1.0.0alpha
 */
public record PaperCommandCompletionContext(
        PaperCommandContext commandContext,
        Object commandBean,
        Method method,
        Parameter parameter,
        int parameterIndex,
        String subCommand,
        String currentInput,
        List<String> committedArguments) {

    /**
     * 验证上下文并冻结参数列表。
     */
    public PaperCommandCompletionContext {
        Objects.requireNonNull(commandContext, "原始指令上下文不能为空。");
        Objects.requireNonNull(commandBean, "指令 Bean 不能为空。");
        Objects.requireNonNull(method, "指令方法不能为空。");
        Objects.requireNonNull(parameter, "待补全参数不能为空。");
        Objects.requireNonNull(subCommand, "子指令名称不能为空。");
        Objects.requireNonNull(currentInput, "当前输入不能为空。");
        committedArguments = List.copyOf(committedArguments);
    }

    /**
     * 获取当前指令发送者。
     *
     * @return 指令发送者
     */
    public CommandSender sender() {
        return commandContext.sender();
    }

    /**
     * 获取当前 Paper 插件。
     *
     * @return Paper 插件
     */
    public JavaPlugin plugin() {
        return commandContext.plugin();
    }
}
