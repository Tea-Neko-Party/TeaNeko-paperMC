package org.zexnocs.teanekopapermc.home.command;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.zexnocs.teanekocore.command.CommandData;
import org.zexnocs.teanekocore.command.api.Command;
import org.zexnocs.teanekocore.command.api.CommandPermission;
import org.zexnocs.teanekocore.command.api.CommandScope;
import org.zexnocs.teanekocore.command.api.DefaultCommand;
import org.zexnocs.teanekopapermc.command.PaperCommandClient;
import org.zexnocs.teanekopapermc.command.PaperCommandContext;
import org.zexnocs.teanekopapermc.command.api.TeaNekoMCCommand;
import org.zexnocs.teanekopapermc.home.HomeService;
import org.zexnocs.teanekopapermc.home.utils.HomeCommandUtils;
import org.zexnocs.teanekopapermc.utils.PaperCommandUtils;

import java.util.List;

/**
 * 提供在玩家当前位置创建或更新家的 Minecraft 指令。
 *
 * @author zExNocs
 * @date 2026/09/10
 * @since paperMC-1.0.0alpha
 * @see HomeService
 */
@Command(
        value = "/sethome",
        permission = CommandPermission.ALL,
        scope = CommandScope.ALL,
        supportedClients = PaperCommandClient.class,
        taskNamespace = "papermc-home-command"
)
@TeaNekoMCCommand(
        description = "在当前位置创建或更新一个家。",
        usage = "/sethome [名称]",
        permission = "teaneko.home.use",
        permissionDescription = "允许使用玩家家相关指令。"
)
public final class SetHomeCommand {
    private final HomeService homeService;

    /**
     * 创建玩家设置家指令。
     *
     * @param homeService 玩家家服务
     */
    public SetHomeCommand(HomeService homeService) {
        this.homeService = homeService;
    }

    /**
     * 在主线程捕获玩家位置，然后通过 EasyData 保存位置快照。
     *
     * @param commandData Core 指令数据
     * @param args 可选的家名称
     */
    @DefaultCommand
    public void setHome(CommandData<PaperCommandContext> commandData, List<String> args) {
        PaperCommandContext context = commandData.getRawData();
        Player player = PaperCommandUtils.requirePlayer(context);
        if (player == null) {
            return;
        }
        if (args.size() > 1) {
            PaperCommandUtils.sendOnMainThread(context, "用法：/sethome [名称]");
            return;
        }

        String homeName = args.isEmpty()
                ? HomeCommandUtils.DEFAULT_HOME_NAME
                : HomeCommandUtils.normalizeHomeName(args.getFirst());
        if (!HomeCommandUtils.validateHomeName(context, homeName)) {
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
            PaperCommandUtils.sendOnMainThread(context, buildResultMessage(homeName, result));
        } catch (RuntimeException exception) {
            PaperCommandUtils.reportFailure(context, "保存玩家家位置失败", exception);
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
    private String buildResultMessage(String homeName, HomeService.SaveResult result) {
        return switch (result.status()) {
            case CREATED -> "已设置家 \"" + homeName + "\"。";
            case UPDATED -> "已更新家 \"" + homeName + "\"。";
            case LIMIT_REACHED -> "你最多只能设置 " + result.maxHomes()
                    + " 个家；更新已有家不受此限制。";
        };
    }
}
