package org.zexnocs.teaneko.mc.feature.command.home;

import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.zexnocs.teaneko.core.command.CommandData;
import org.zexnocs.teaneko.core.command.api.*;
import org.zexnocs.teaneko.mc.core.command.PaperCommandClient;
import org.zexnocs.teaneko.mc.core.command.PaperCommandContext;
import org.zexnocs.teaneko.mc.core.command.api.TeaNekoMCCommand;
import org.zexnocs.teaneko.mc.core.command.api.TeaNekoMCSubCommand;
import org.zexnocs.teaneko.mc.core.command.completion.api.CommandComplete;
import org.zexnocs.teaneko.mc.utils.PaperCommandUtils;

import java.util.List;
import java.util.Locale;

/**
 * 通过单一 {@code /home} 顶级入口提供传送、设置、删除、列表和数量管理功能。
 *
 * @author zExNocs
 * @date 2026/09/12
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
        description = "管理并传送到玩家的家。",
        usage = "/home；/home <tp|set|remove|list> [参数]",
        permission = "teaneko.home.use",
        permissionDescription = "允许使用玩家家相关指令。"
)
public final class HomeCommand {
    private static final String PLAYER_USAGE =
            "/home；/home tp [名称]；/home set [名称]；/home remove [名称]；/home list";
    private final HomeService homeService;

    /**
     * 创建玩家家聚合指令。
     *
     * @param homeService 玩家家服务
     */
    public HomeCommand(HomeService homeService) {
        this.homeService = homeService;
    }

    /**
     * 未指定子指令时传送到默认家；输入未知子指令时仅显示玩家用法。
     * <p>
     * 默认入口固定查询名为 {@code home} 的家，不把额外参数解释为家名称。
     *
     * @param commandData Core 指令数据
     * @param unmatchedArgs 未匹配的参数
     */
    @DefaultCommand
    public void teleportDefaultHome(CommandData<PaperCommandContext> commandData,
                                    List<String> unmatchedArgs) {
        PaperCommandContext context = commandData.getRawData();
        if (!unmatchedArgs.isEmpty()) {
            PaperCommandUtils.sendOnMainThread(
                    context,
                    "其他名称的家必须使用 /home tp <名称> 传送。用法：" + PLAYER_USAGE
            );
            return;
        }

        Player player = PaperCommandUtils.requirePlayer(context);
        if (player == null) {
            return;
        }
        teleportExistingHome(context, player, HomeCommandUtils.DEFAULT_HOME_NAME, true);
    }

    /**
     * 传送到指定的家，未提供名称时使用 {@code home}。
     *
     * @param commandData Core 指令数据
     * @param rawHomeName 原始家名称
     * @param remainingArgs 多余参数，用于拒绝不符合格式的调用
     */
    @SubCommand("tp")
    public void teleportHome(CommandData<PaperCommandContext> commandData,
                             @CommandComplete(providers = HomeNameCompletionProvider.class)
                             @DefaultValue(HomeCommandUtils.DEFAULT_HOME_NAME) String rawHomeName,
                             List<String> remainingArgs) {
        PaperCommandContext context = commandData.getRawData();
        Player player = requirePlayerAndNoRemainingArgs(context, remainingArgs, "/home tp [名称]");
        if (player == null) {
            return;
        }

        String homeName = normalizeAndValidateHomeName(context, rawHomeName);
        if (homeName == null) {
            return;
        }
        teleportExistingHome(context, player, homeName, false);
    }

    /**
     * 在玩家当前位置创建或更新一个家，未提供名称时使用 {@code home}。
     *
     * @param commandData Core 指令数据
     * @param rawHomeName 原始家名称
     * @param remainingArgs 多余参数，用于拒绝不符合格式的调用
     */
    @SubCommand("set")
    public void setHome(CommandData<PaperCommandContext> commandData,
                        @CommandComplete(providers = HomeNameCompletionProvider.class)
                        @DefaultValue(HomeCommandUtils.DEFAULT_HOME_NAME) String rawHomeName,
                        List<String> remainingArgs) {
        PaperCommandContext context = commandData.getRawData();
        Player player = requirePlayerAndNoRemainingArgs(context, remainingArgs, "/home set [名称]");
        if (player == null) {
            return;
        }

        String homeName = normalizeAndValidateHomeName(context, rawHomeName);
        if (homeName == null) {
            return;
        }
        try {
            HomeService.HomeSnapshot snapshot = captureLocation(context, player, homeName);
            if (snapshot == null) {
                PaperCommandUtils.sendOnMainThread(context, "当前世界不可用，无法设置家。");
                return;
            }
            HomeService.SaveResult result = homeService.saveHome(
                    player.getUniqueId(),
                    snapshot.homeName(),
                    snapshot.worldName(),
                    snapshot.x(),
                    snapshot.y(),
                    snapshot.z(),
                    snapshot.yaw(),
                    snapshot.pitch()
            );
            PaperCommandUtils.sendOnMainThread(context, buildSaveResultMessage(homeName, result));
        } catch (RuntimeException exception) {
            PaperCommandUtils.reportFailure(context, "保存玩家家位置失败", exception);
        }
    }

    /**
     * 删除指定的家，未提供名称时使用 {@code home}。
     *
     * @param commandData Core 指令数据
     * @param rawHomeName 原始家名称
     * @param remainingArgs 多余参数，用于拒绝不符合格式的调用
     */
    @SubCommand("remove")
    public void removeHome(CommandData<PaperCommandContext> commandData,
                           @CommandComplete(providers = HomeNameCompletionProvider.class)
                           @DefaultValue(HomeCommandUtils.DEFAULT_HOME_NAME) String rawHomeName,
                           List<String> remainingArgs) {
        PaperCommandContext context = commandData.getRawData();
        Player player = requirePlayerAndNoRemainingArgs(
                context,
                remainingArgs,
                "/home remove [名称]"
        );
        if (player == null) {
            return;
        }

        String homeName = normalizeAndValidateHomeName(context, rawHomeName);
        if (homeName == null) {
            return;
        }
        try {
            if (homeService.deleteHome(player.getUniqueId(), homeName)) {
                PaperCommandUtils.sendOnMainThread(context, "已删除家 \"" + homeName + "\"。");
            } else {
                PaperCommandUtils.sendOnMainThread(
                        context,
                        "不存在名为 \"" + homeName + "\" 的家。"
                );
            }
        } catch (RuntimeException exception) {
            PaperCommandUtils.reportFailure(context, "删除玩家家位置失败", exception);
        }
    }

    /**
     * 显示玩家当前全部家的名称、世界、坐标与数量上限。
     *
     * @param commandData Core 指令数据
     * @param remainingArgs 多余参数，用于拒绝不符合格式的调用
     */
    @SubCommand("list")
    public void listHomes(CommandData<PaperCommandContext> commandData,
                          List<String> remainingArgs) {
        PaperCommandContext context = commandData.getRawData();
        Player player = requirePlayerAndNoRemainingArgs(context, remainingArgs, "/home list");
        if (player == null) {
            return;
        }

        try {
            List<HomeService.HomeSnapshot> homes = homeService.findAllHomes(player.getUniqueId());
            if (homes.isEmpty()) {
                PaperCommandUtils.sendOnMainThread(
                        context,
                        "你还没有设置家，请先使用 /home set [名称]。"
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
     * 修改服务器已知玩家的家数量上限。
     *
     * @param commandData Core 指令数据
     * @param targetPlayer 由类型处理器解析的服务器已知玩家
     * @param maxHomes 新的非负数量上限
     * @param remainingArgs 多余参数，用于拒绝不符合格式的调用
     */
    @SubCommand(value = "set-num", permission = CommandPermission.ADMIN)
    @TeaNekoMCSubCommand(
            permission = "teaneko.home.admin",
            permissionDescription = "允许修改玩家家数量上限。",
            permissionDefault = TeaNekoMCCommand.PermissionDefault.OP
    )
    public void setHomeNumber(CommandData<PaperCommandContext> commandData,
                              OfflinePlayer targetPlayer,
                              @CommandComplete({"0", "1", "3", "5", "10"}) int maxHomes,
                              List<String> remainingArgs) {
        PaperCommandContext context = commandData.getRawData();
        if (!remainingArgs.isEmpty()) {
            PaperCommandUtils.sendOnMainThread(
                    context,
                    "用法：/home set-num <玩家名或 UUID> <数量>"
            );
            return;
        }
        if (maxHomes < 0) {
            PaperCommandUtils.sendOnMainThread(context, "家数量必须是大于或等于零的整数。");
            return;
        }

        try {
            String targetName = targetPlayer.getName() == null
                    ? targetPlayer.getUniqueId().toString()
                    : targetPlayer.getName();
            homeService.setHomeLimit(targetPlayer.getUniqueId(), maxHomes);
            PaperCommandUtils.sendOnMainThread(
                    context,
                    "已将 " + targetName + " 的家数量上限设置为 " + maxHomes
                            + "；已有的超额家不会被删除。"
            );
        } catch (RuntimeException exception) {
            PaperCommandUtils.reportFailure(context, "修改玩家家数量上限失败", exception);
        }
    }

    /**
     * 校验玩家身份和多余参数。
     *
     * @param context 指令上下文
     * @param remainingArgs 多余参数
     * @param usage 当前子指令用法
     * @return 玩家实例；校验失败时返回 {@code null}
     */
    private Player requirePlayerAndNoRemainingArgs(PaperCommandContext context,
                                                    List<String> remainingArgs,
                                                    String usage) {
        Player player = PaperCommandUtils.requirePlayer(context);
        if (player == null) {
            return null;
        }
        if (!remainingArgs.isEmpty()) {
            PaperCommandUtils.sendOnMainThread(context, "用法：" + usage);
            return null;
        }
        return player;
    }

    /**
     * 规范化并校验家名称。
     *
     * @param context 指令上下文
     * @param rawHomeName 原始家名称
     * @return 合法的家名称；校验失败时返回 {@code null}
     */
    private String normalizeAndValidateHomeName(PaperCommandContext context, String rawHomeName) {
        String homeName = HomeCommandUtils.normalizeHomeName(rawHomeName);
        return HomeCommandUtils.validateHomeName(context, homeName) ? homeName : null;
    }

    /**
     * 查询并传送到已有的家，在默认入口找不到家时补充普通玩家用法。
     *
     * @param context 指令上下文
     * @param player 玩家
     * @param homeName 经过规范化的家名称
     * @param explainDefaultHomeMissing 是否说明默认家不存在后的普通玩家操作方式
     */
    private void teleportExistingHome(PaperCommandContext context,
                                      Player player,
                                      String homeName,
                                      boolean explainDefaultHomeMissing) {
        try {
            homeService.findHome(player.getUniqueId(), homeName).ifPresentOrElse(
                    home -> HomeCommandUtils.teleport(context, player, home),
                    () -> PaperCommandUtils.sendOnMainThread(
                            context,
                            explainDefaultHomeMissing
                                    ? "不存在默认家 \"home\"。用法：" + PLAYER_USAGE
                                    : "不存在名为 \"" + homeName + "\" 的家。"
                    )
            );
        } catch (RuntimeException exception) {
            PaperCommandUtils.reportFailure(context, "查询玩家家位置失败", exception);
        }
    }

    /**
     * 在 Paper 主线程读取玩家当前世界、坐标与朝向。
     *
     * @param context 指令上下文
     * @param player 玩家
     * @param homeName 家名称
     * @return 位置快照；玩家离线或世界不可用时返回 {@code null}
     */
    private HomeService.HomeSnapshot captureLocation(PaperCommandContext context,
                                                      Player player,
                                                      String homeName) {
        return PaperCommandUtils.callOnMainThread(context.plugin(), () -> {
            if (!player.isOnline()) {
                return null;
            }
            Location location = player.getLocation();
            World world = location.getWorld();
            if (world == null) {
                return null;
            }
            return new HomeService.HomeSnapshot(
                    homeName,
                    world.getName(),
                    location.getX(),
                    location.getY(),
                    location.getZ(),
                    location.getYaw(),
                    location.getPitch()
            );
        });
    }

    /**
     * 根据 EasyData 保存状态构造玩家反馈。
     *
     * @param homeName 家名称
     * @param result 保存结果
     * @return 中文反馈消息
     */
    private String buildSaveResultMessage(String homeName, HomeService.SaveResult result) {
        return switch (result.status()) {
            case CREATED -> "已设置家 \"" + homeName + "\"。";
            case UPDATED -> "已更新家 \"" + homeName + "\"。";
            case LIMIT_REACHED -> "你最多只能设置 " + result.maxHomes()
                    + " 个家；更新已有家不受此限制。";
        };
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
}
