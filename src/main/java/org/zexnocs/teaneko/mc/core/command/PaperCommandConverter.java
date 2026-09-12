package org.zexnocs.teaneko.mc.core.command;

import org.bukkit.entity.Player;
import org.springframework.stereotype.Component;
import org.zexnocs.teaneko.core.command.CommandData;
import org.zexnocs.teaneko.core.command.api.CommandPermission;
import org.zexnocs.teaneko.core.command.api.CommandScope;
import org.zexnocs.teaneko.core.command.interfaces.ICommandConverter;
import org.zexnocs.teaneko.mc.utils.PaperCommandUtils;

/**
 * 将 Paper 指令上下文转换为 Core 通用指令数据。
 *
 * @author zExNocs
 * @date 2026/09/10
 * @since paperMC-1.0.0alpha
 * @see ICommandConverter
 */
@Component
public final class PaperCommandConverter implements ICommandConverter<PaperCommandContext> {
    /**
     * 构造 Core 指令分发所需的数据，并映射 Minecraft 发送者权限。
     *
     * @param context Paper 指令上下文
     * @return Core 指令数据
     */
    @Override
    public CommandData<PaperCommandContext> __parse(PaperCommandContext context) {
        String senderId = context.sender() instanceof Player player
                ? player.getUniqueId().toString()
                : context.sender().getName();
        return CommandData.<PaperCommandContext>builder()
                .body(context.coreCommandName())
                .args(context.args())
                .scope(CommandScope.OTHER)
                .clientClass(PaperCommandClient.class)
                .scopeId("papermc@" + context.plugin().getServer().getName())
                .permission(resolvePermission(context))
                .senderId(senderId)
                .rawData(context)
                .build();
    }

    /**
     * 将控制台、OP 和普通玩家映射到 Core 权限等级。
     * Bukkit 明确授予当前指令权限节点时，视为该指令的最高授权。
     *
     * @param context Paper 指令上下文
     * @return Core 权限等级
     */
    private CommandPermission resolvePermission(PaperCommandContext context) {
        if (PaperCommandUtils.isConsole(context.sender())) {
            return CommandPermission.DEBUG;
        }
        String permission = context.minecraftMetadata().permission();
        if (!permission.isBlank() && context.sender().hasPermission(permission)) {
            return CommandPermission.DEBUG;
        }
        return context.sender().isOp() ? CommandPermission.ADMIN : CommandPermission.MEMBER;
    }
}
