package org.zexnocs.teanekopapermc.core.command;

import org.zexnocs.teanekocore.command.CommandData;
import org.zexnocs.teanekocore.command.interfaces.ICommandErrorHandler;
import org.zexnocs.teanekopapermc.utils.PaperCommandUtils;

/**
 * 将 Core 指令分发错误转换成 Minecraft 消息。
 *
 * @author zExNocs
 * @date 2026/09/10
 * @since paperMC-1.0.0alpha
 * @see ICommandErrorHandler
 */
public final class PaperCommandErrorHandler implements ICommandErrorHandler {
    private final PaperCommandContext context;

    /**
     * 创建绑定到一次指令调用的错误处理器。
     *
     * @param context Paper 指令上下文
     */
    public PaperCommandErrorHandler(PaperCommandContext context) {
        this.context = context;
    }

    /**
     * 提示指令已经关闭。
     *
     * @param commandData Core 指令数据
     */
    @Override
    public void handleCommandClosed(CommandData<?> commandData) {
        PaperCommandUtils.sendOnMainThread(context, "该指令当前已关闭。");
    }

    /**
     * 提示未找到可执行的方法。
     *
     * @param commandData Core 指令数据
     */
    @Override
    public void handleMethodNotFound(CommandData<?> commandData) {
        PaperCommandUtils.sendOnMainThread(context, "未找到对应的指令用法：" + getUsage());
    }

    /**
     * 提示参数不符合方法签名。
     *
     * @param commandData Core 指令数据
     */
    @Override
    public void handleArgsError(CommandData<?> commandData) {
        PaperCommandUtils.sendOnMainThread(context, "指令参数不正确，用法：" + getUsage());
    }

    /**
     * 提示发送者没有权限。
     *
     * @param commandData Core 指令数据
     */
    @Override
    public void handleNoPermission(CommandData<?> commandData) {
        PaperCommandUtils.sendOnMainThread(context, "你没有执行该指令的权限。");
    }

    /**
     * 提示当前 Paper 作用域不允许执行指令。
     *
     * @param commandData Core 指令数据
     */
    @Override
    public void handleNotInScope(CommandData<?> commandData) {
        PaperCommandUtils.sendOnMainThread(context, "该指令不能在当前 Minecraft 作用域执行。");
    }

    /**
     * 获取注解声明的用法，未声明时回退到实际指令标签。
     *
     * @return 指令用法
     */
    private String getUsage() {
        String usage = context.minecraftMetadata().usage();
        return usage.isBlank() ? "/" + context.label() : usage;
    }
}
