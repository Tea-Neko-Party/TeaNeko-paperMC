package org.zexnocs.teanekopapermc.command;

import org.zexnocs.teanekocore.command.CommandData;
import org.zexnocs.teanekocore.command.api.Command;
import org.zexnocs.teanekocore.command.api.CommandPermission;
import org.zexnocs.teanekocore.command.api.CommandScope;
import org.zexnocs.teanekocore.command.api.DefaultCommand;
import org.zexnocs.teanekopapermc.command.api.TeaNekoMCCommand;
import org.zexnocs.teanekopapermc.utils.PaperCommandUtils;

import java.util.List;

/**
 * 提供 TeaNeko Paper 插件状态查询指令。
 *
 * @author zExNocs
 * @date 2026/09/10
 * @since paperMC-1.0.0alpha
 */
@Command(
        value = "/teaneko",
        permission = CommandPermission.ALL,
        scope = CommandScope.ALL,
        supportedClients = PaperCommandClient.class
)
@TeaNekoMCCommand(
        description = "显示 TeaNeko Paper 插件状态。",
        usage = "/teaneko",
        permission = "teaneko.command.status",
        permissionDescription = "允许查看 TeaNeko Paper 插件状态。"
)
public final class TeaNekoStatusCommand {
    /**
     * 返回插件版本与 Core 就绪状态。
     *
     * @param commandData Core 指令数据
     * @param args 指令参数
     */
    @DefaultCommand
    public void showStatus(CommandData<PaperCommandContext> commandData, List<String> args) {
        PaperCommandContext context = commandData.getRawData();
        if (!args.isEmpty()) {
            PaperCommandUtils.sendOnMainThread(context, "用法：/teaneko");
            return;
        }
        PaperCommandUtils.sendOnMainThread(
                context,
                "TeaNeko Paper v" + context.plugin().getPluginMeta().getVersion() + " — Core 已就绪。"
        );
    }
}
