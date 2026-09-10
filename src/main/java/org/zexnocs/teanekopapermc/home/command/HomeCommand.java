package org.zexnocs.teanekopapermc.home.command;

import org.bukkit.entity.Player;
import org.zexnocs.teanekocore.command.CommandData;
import org.zexnocs.teanekocore.command.api.Command;
import org.zexnocs.teanekocore.command.api.CommandPermission;
import org.zexnocs.teanekocore.command.api.CommandScope;
import org.zexnocs.teanekocore.command.api.DefaultCommand;
import org.zexnocs.teanekocore.command.api.SubCommand;
import org.zexnocs.teanekopapermc.command.PaperCommandClient;
import org.zexnocs.teanekopapermc.command.PaperCommandContext;
import org.zexnocs.teanekopapermc.command.api.TeaNekoMCCommand;
import org.zexnocs.teanekopapermc.home.HomeService;
import org.zexnocs.teanekopapermc.home.utils.HomeCommandUtils;
import org.zexnocs.teanekopapermc.utils.PaperCommandUtils;

import java.util.List;
import java.util.Locale;

/**
 * 提供玩家查询并传送到家的 Minecraft 指令。
 *
 * @author zExNocs
 * @date 2026/09/10
 * @since paperMC-1.0.0alpha
 * @see HomeService
 */
@Command(
        value = "/home",
        permission = CommandPermission.ALL,
        scope = CommandScope.ALL,
        supportedClients = PaperCommandClient.class,
        taskNamespace = "papermc-home-command"
)
@TeaNekoMCCommand(
        description = "传送到指定的家。",
        usage = "/home [名称]",
        permission = "teaneko.home.use",
        permissionDescription = "允许使用玩家家相关指令。"
)
public final class HomeCommand {
    private final HomeService homeService;

    /**
     * 创建玩家回家指令。
     *
     * @param homeService 玩家家服务
     */
    public HomeCommand(HomeService homeService) {
        this.homeService = homeService;
    }

    /**
     * 按名称查询家；未指定名称时应用零个、一个或多个家的默认选择规则。
     *
     * @param commandData Core 指令数据
     * @param args 家名称参数
     */
    @DefaultCommand
    public void teleportHome(CommandData<PaperCommandContext> commandData, List<String> args) {
        PaperCommandContext context = commandData.getRawData();
        Player player = PaperCommandUtils.requirePlayer(context);
        if (player == null) {
            return;
        }
        if (args.size() > 1) {
            PaperCommandUtils.sendOnMainThread(context, "用法：/home [名称]");
            return;
        }

        try {
            if (args.size() == 1) {
                teleportNamedHome(context, player, args.getFirst());
            } else {
                teleportDefaultHome(context, player);
            }
        } catch (RuntimeException exception) {
            PaperCommandUtils.reportFailure(context, "查询玩家家位置失败", exception);
        }
    }

    /**
     * 显示玩家当前全部家的名称、世界、坐标与数量上限。
     *
     * @param commandData Core 指令数据
     * @param args 子指令后的剩余参数
     */
    @SubCommand({"list", "列表"})
    public void listHomes(CommandData<PaperCommandContext> commandData, List<String> args) {
        PaperCommandContext context = commandData.getRawData();
        Player player = PaperCommandUtils.requirePlayer(context);
        if (player == null) {
            return;
        }
        if (!args.isEmpty()) {
            PaperCommandUtils.sendOnMainThread(context, "用法：/home list");
            return;
        }

        try {
            List<HomeService.HomeSnapshot> homes = homeService.findAllHomes(player.getUniqueId());
            if (homes.isEmpty()) {
                PaperCommandUtils.sendOnMainThread(
                        context,
                        "你还没有设置家，请先使用 /sethome [名称]。"
                );
                return;
            }

            int homeLimit = homeService.getHomeLimit(player.getUniqueId());
            StringBuilder message = new StringBuilder("你的家（")
                    .append(homes.size())
                    .append('/')
                    .append(homeLimit)
                    .append("）：");
            for (HomeService.HomeSnapshot home : homes) {
                message.append("\n- ")
                        .append(home.homeName())
                        .append("：")
                        .append(home.worldName())
                        .append(" (")
                        .append(formatCoordinate(home.x()))
                        .append(", ")
                        .append(formatCoordinate(home.y()))
                        .append(", ")
                        .append(formatCoordinate(home.z()))
                        .append(')');
            }
            PaperCommandUtils.sendOnMainThread(context, message.toString());
        } catch (RuntimeException exception) {
            PaperCommandUtils.reportFailure(context, "查询玩家家列表失败", exception);
        }
    }

    /**
     * 将坐标格式化到小数点后两位，并移除无意义的末尾零。
     *
     * @param coordinate 原始坐标
     * @return 便于玩家阅读的坐标文本
     */
    private String formatCoordinate(double coordinate) {
        String formatted = String.format(Locale.ROOT, "%.2f", coordinate);
        return formatted.replaceAll("\\.?0+$", "");
    }

    /**
     * 查询并传送到指定名称的家。
     *
     * @param context 指令上下文
     * @param player 目标玩家
     * @param rawHomeName 原始家名称
     */
    private void teleportNamedHome(PaperCommandContext context, Player player, String rawHomeName) {
        String homeName = HomeCommandUtils.normalizeHomeName(rawHomeName);
        if (!HomeCommandUtils.validateHomeName(context, homeName)) {
            return;
        }
        homeService.findHome(player.getUniqueId(), homeName).ifPresentOrElse(
                home -> HomeCommandUtils.teleport(context, player, home),
                () -> PaperCommandUtils.sendOnMainThread(
                        context,
                        "不存在名为 \"" + homeName + "\" 的家。"
                )
        );
    }

    /**
     * 根据玩家当前家数量选择唯一家或默认 home。
     *
     * @param context 指令上下文
     * @param player 目标玩家
     */
    private void teleportDefaultHome(PaperCommandContext context, Player player) {
        List<HomeService.HomeSnapshot> homes = homeService.findAllHomes(player.getUniqueId());
        if (homes.isEmpty()) {
            PaperCommandUtils.sendOnMainThread(context, "你还没有设置家，请先使用 /sethome [名称]。");
            return;
        }
        if (homes.size() == 1) {
            HomeCommandUtils.teleport(context, player, homes.getFirst());
            return;
        }

        homes.stream()
                .filter(home -> HomeCommandUtils.DEFAULT_HOME_NAME.equals(home.homeName()))
                .findFirst()
                .ifPresentOrElse(
                        home -> HomeCommandUtils.teleport(context, player, home),
                        () -> PaperCommandUtils.sendOnMainThread(
                                context,
                                "你有多个家，但不存在默认名称 \"home\"；请使用 /home <名称>。"
                        )
                );
    }
}
