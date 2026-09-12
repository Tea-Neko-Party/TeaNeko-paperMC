package org.zexnocs.teaneko.mc.core.command.completion.handler;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.springframework.stereotype.Component;
import org.zexnocs.teaneko.core.command.CommandData;
import org.zexnocs.teaneko.mc.core.command.PaperCommandContext;
import org.zexnocs.teaneko.mc.core.command.completion.PaperCommandCompletionContext;
import org.zexnocs.teaneko.mc.core.command.completion.PaperKnownPlayerDirectory;
import org.zexnocs.teaneko.mc.core.command.completion.interfaces.IPaperCommandArgumentTypeHandler;
import org.zexnocs.teaneko.mc.utils.PaperCommandUtils;

import java.util.*;

/**
 * 将指令参数解析为服务器存在过的玩家，并提供历史玩家名称补全。
 *
 * @author zExNocs
 * @date 2026/09/12
 * @since paperMC-1.0.0alpha
 * @see PaperKnownPlayerDirectory
 */
@Component
public final class KnownPlayerArgumentTypeHandler
        implements IPaperCommandArgumentTypeHandler<OfflinePlayer> {
    private final PaperKnownPlayerDirectory playerDirectory;

    /**
     * 创建历史玩家参数处理器。
     *
     * @param playerDirectory 历史玩家名称缓存
     */
    public KnownPlayerArgumentTypeHandler(PaperKnownPlayerDirectory playerDirectory) {
        this.playerDirectory = playerDirectory;
    }

    /**
     * 获取历史玩家参数类型。
     *
     * @return {@link OfflinePlayer} 类型
     */
    @Override
    public Class<OfflinePlayer> targetType() {
        return OfflinePlayer.class;
    }

    /**
     * 按 UUID 或服务器已缓存的名称解析历史玩家。
     *
     * @param input 玩家名称或 UUID
     * @param commandData 当前指令数据
     * @return 服务器已知玩家；未找到时返回空
     */
    @Override
    public Optional<OfflinePlayer> parse(String input, CommandData<?> commandData) {
        if (!(commandData.getRawData() instanceof PaperCommandContext context)) {
            return Optional.empty();
        }

        return Optional.ofNullable(PaperCommandUtils.callOnMainThread(
                context.plugin(),
                () -> resolveKnownPlayer(context, input)
        ));
    }

    /**
     * 补全历史玩家名称，并对在线玩家继续应用可见性检查。
     *
     * @param context 当前参数补全上下文
     * @return 可补全的历史玩家名称
     */
    @Override
    public Collection<String> complete(PaperCommandCompletionContext context) {
        Player sender = context.sender() instanceof Player player ? player : null;
        Set<String> hiddenOnlinePlayers = sender == null
                ? Set.of()
                : context.plugin().getServer().getOnlinePlayers().stream()
                .filter(player -> !sender.canSee(player))
                .map(player -> player.getName().toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return playerDirectory.getKnownPlayerNames().stream()
                .filter(name -> !hiddenOnlinePlayers.contains(name.toLowerCase(Locale.ROOT)))
                .toList();
    }

    /**
     * 在主线程中解析并验证玩家是否确实存在于服务器记录中。
     */
    private OfflinePlayer resolveKnownPlayer(PaperCommandContext context, String input) {
        UUID uuid = parseUuid(input);
        if (uuid == null) {
            uuid = playerDirectory.findUuid(input).orElse(null);
        }

        OfflinePlayer player;
        if (uuid != null) {
            player = context.plugin().getServer().getOfflinePlayer(uuid);
        } else {
            player = context.plugin().getServer().getOfflinePlayerIfCached(input);
        }
        if (player == null || (!playerDirectory.isKnown(player.getUniqueId())
                && !player.hasPlayedBefore() && !player.isOnline())) {
            return null;
        }
        playerDirectory.remember(player.getUniqueId(), player.getName());
        return player;
    }

    /**
     * 尝试解析 UUID。
     */
    private UUID parseUuid(String input) {
        try {
            return UUID.fromString(input);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
