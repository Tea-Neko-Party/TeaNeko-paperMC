package org.zexnocs.teanekopapermc.home.utils;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.zexnocs.teanekopapermc.command.PaperCommandContext;
import org.zexnocs.teanekopapermc.home.HomeService;
import org.zexnocs.teanekopapermc.utils.PaperCommandUtils;

import java.util.Locale;
import java.util.logging.Level;

/**
 * 提供玩家家指令共享的名称校验和跨世界传送方法。
 *
 * @author zExNocs
 * @date 2026/09/10
 * @since paperMC-1.0.0alpha
 */
public final class HomeCommandUtils {
    /** 无名称参数时使用的默认家名称。 */
    public static final String DEFAULT_HOME_NAME = "home";
    /** 数据库允许的家名称最大长度。 */
    public static final int MAX_HOME_NAME_LENGTH = 32;

    private HomeCommandUtils() {
    }

    /**
     * 统一家名称的大小写与首尾空白。
     *
     * @param homeName 原始家名称
     * @return 规范化后的家名称
     */
    public static String normalizeHomeName(String homeName) {
        return homeName.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 校验家名称是否能够写入数据库，并在失败时提示发送者。
     *
     * @param context 指令上下文
     * @param homeName 经过规范化的家名称
     * @return 名称有效时返回 {@code true}
     */
    public static boolean validateHomeName(PaperCommandContext context, String homeName) {
        if (homeName.isBlank()) {
            PaperCommandUtils.sendOnMainThread(context, "家名称不能为空。");
            return false;
        }
        if (homeName.length() > MAX_HOME_NAME_LENGTH) {
            PaperCommandUtils.sendOnMainThread(
                    context,
                    "家名称不能超过 " + MAX_HOME_NAME_LENGTH + " 个字符。"
            );
            return false;
        }
        return true;
    }

    /**
     * 在 Paper 主线程解析世界，并使用异步区块加载传送玩家。
     *
     * @param context 指令上下文
     * @param player 目标玩家
     * @param home 家位置快照
     */
    public static void teleport(PaperCommandContext context,
                                Player player,
                                HomeService.HomeSnapshot home) {
        PaperCommandUtils.runOnMainThread(context.plugin(), () -> {
            if (!player.isOnline()) {
                return;
            }
            World world = context.plugin().getServer().getWorld(home.worldName());
            if (world == null) {
                player.sendMessage("家 \"" + home.homeName() + "\" 所在世界 \""
                        + home.worldName() + "\" 当前未加载。");
                return;
            }

            Location destination = new Location(
                    world, home.x(), home.y(), home.z(), home.yaw(), home.pitch()
            );
            player.teleportAsync(destination).whenComplete((success, exception) -> {
                if (exception != null) {
                    context.plugin().getLogger().log(
                            Level.WARNING,
                            "传送玩家 " + player.getName() + " 到家时发生异常。",
                            exception
                    );
                    PaperCommandUtils.sendOnMainThread(context, "传送失败，请稍后重试。");
                } else if (Boolean.TRUE.equals(success)) {
                    PaperCommandUtils.sendOnMainThread(
                            context,
                            "已传送到家 \"" + home.homeName() + "\"。"
                    );
                } else {
                    PaperCommandUtils.sendOnMainThread(context, "传送未完成，请确认当前位置允许传送。");
                }
            });
        });
    }
}
