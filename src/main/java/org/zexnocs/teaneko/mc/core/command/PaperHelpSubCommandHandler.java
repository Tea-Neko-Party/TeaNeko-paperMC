package org.zexnocs.teaneko.mc.core.command;

import org.zexnocs.teaneko.core.command.CommandData;
import org.zexnocs.teaneko.core.command.CommandMapData;
import org.zexnocs.teaneko.core.command.interfaces.IHelpSubCommandHandler;
import org.zexnocs.teaneko.mc.utils.PaperCommandUtils;

/**
 * 为 Paper 指令提供统一的 help 子指令反馈。
 *
 * @author zExNocs
 * @date 2026/09/10
 * @since paperMC-1.0.0alpha
 * @see IHelpSubCommandHandler
 */
public final class PaperHelpSubCommandHandler implements IHelpSubCommandHandler {
    private final PaperCommandContext context;

    /**
     * 创建绑定到一次指令调用的帮助处理器。
     *
     * @param context Paper 指令上下文
     */
    public PaperHelpSubCommandHandler(PaperCommandContext context) {
        this.context = context;
    }

    /**
     * 显示注解中声明的指令说明与用法。
     *
     * @param commandData Core 指令数据
     * @param mapData 指令映射数据
     * @param args help 后的剩余参数
     */
    @Override
    public void handleSubHelp(CommandData<?> commandData, CommandMapData mapData, String[] args) {
        String description = context.minecraftMetadata().description();
        String usage = context.minecraftMetadata().usage();
        if (!description.isBlank()) {
            PaperCommandUtils.sendOnMainThread(context, description);
        }
        PaperCommandUtils.sendOnMainThread(
                context,
                "用法：" + (usage.isBlank() ? "/" + context.label() : usage)
        );
    }
}
