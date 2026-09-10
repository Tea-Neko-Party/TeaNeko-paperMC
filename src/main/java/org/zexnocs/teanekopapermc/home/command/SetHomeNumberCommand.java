package org.zexnocs.teanekopapermc.home.command;

import org.bukkit.entity.Player;
import org.zexnocs.teanekocore.command.CommandData;
import org.zexnocs.teanekocore.command.api.Command;
import org.zexnocs.teanekocore.command.api.CommandPermission;
import org.zexnocs.teanekocore.command.api.CommandScope;
import org.zexnocs.teanekocore.command.api.DefaultCommand;
import org.zexnocs.teanekopapermc.command.interfaces.IPaperCommandTabCompleter;
import org.zexnocs.teanekopapermc.command.PaperCommandClient;
import org.zexnocs.teanekopapermc.command.PaperCommandContext;
import org.zexnocs.teanekopapermc.command.api.TeaNekoMCCommand;
import org.zexnocs.teanekopapermc.home.HomeService;
import org.zexnocs.teanekopapermc.utils.PaperCommandUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 提供管理员修改玩家家数量上限的 Minecraft 指令。
 *
 * @author zExNocs
 * @date 2026/09/10
 * @since paperMC-1.0.0alpha
 * @see HomeService
 */
@Command(
        value = "/sethomenum",
        permission = CommandPermission.ADMIN,
        scope = CommandScope.ALL,
        supportedClients = PaperCommandClient.class,
        permissionPackage = "teaneko.home.admin",
        taskNamespace = "papermc-home-command"
)
@TeaNekoMCCommand(
        description = "修改玩家可以设置的家数量上限。",
        usage = "/sethomenum <玩家名或 UUID> <数量>",
        permission = "teaneko.home.admin",
        permissionDescription = "允许修改玩家家数量上限。",
        permissionDefault = TeaNekoMCCommand.PermissionDefault.OP
)
public final class SetHomeNumberCommand implements IPaperCommandTabCompleter {
    private final HomeService homeService;

    /**
     * 创建玩家家数量管理指令。
     *
     * @param homeService 玩家家服务
     */
    public SetHomeNumberCommand(HomeService homeService) {
        this.homeService = homeService;
    }

    /**
     * 解析玩家标识和非负数量，并等待 EasyData 保存完成。
     *
     * @param commandData Core 指令数据
     * @param args 玩家标识与数量
     */
    @DefaultCommand
    public void setHomeNumber(CommandData<PaperCommandContext> commandData, List<String> args) {
        PaperCommandContext context = commandData.getRawData();
        if (args.size() != 2) {
            PaperCommandUtils.sendOnMainThread(
                    context,
                    "用法：/sethomenum <玩家名或 UUID> <数量>"
            );
            return;
        }

        int maxHomes;
        try {
            maxHomes = Integer.parseInt(args.get(1));
        } catch (NumberFormatException exception) {
            PaperCommandUtils.sendOnMainThread(context, "家数量必须是大于或等于零的整数。");
            return;
        }
        if (maxHomes < 0) {
            PaperCommandUtils.sendOnMainThread(context, "家数量必须是大于或等于零的整数。");
            return;
        }

        try {
            PaperCommandUtils.ResolvedPlayer target = PaperCommandUtils.resolvePlayer(context, args.getFirst());
            if (target == null) {
                PaperCommandUtils.sendOnMainThread(
                        context,
                        "找不到该玩家，未缓存的离线玩家请使用 UUID。"
                );
                return;
            }
            homeService.setHomeLimit(target.uuid(), maxHomes);
            PaperCommandUtils.sendOnMainThread(
                    context,
                    "已将 " + target.displayName() + " 的家数量上限设置为 " + maxHomes
                            + "；已有的超额家不会被删除。"
            );
        } catch (RuntimeException exception) {
            PaperCommandUtils.reportFailure(context, "修改玩家家数量上限失败", exception);
        }
    }

    /**
     * 在第一个参数位置补全当前可见的在线玩家名称。
     *
     * @param context Paper 指令上下文
     * @return 在线玩家名称补全
     */
    @Override
    public List<String> complete(PaperCommandContext context) {
        String[] args = context.args();
        if (args.length != 1) {
            return Collections.emptyList();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> names = new ArrayList<>();
        for (Player player : context.plugin().getServer().getOnlinePlayers()) {
            if (player.getName().toLowerCase(Locale.ROOT).startsWith(prefix)
                    && (!(context.sender() instanceof Player sender) || sender.canSee(player))) {
                names.add(player.getName());
            }
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }
}
