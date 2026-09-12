package org.zexnocs.teaneko.mc.core.command.completion.handler;

import org.bukkit.entity.Player;
import org.springframework.stereotype.Component;
import org.zexnocs.teaneko.core.command.CommandData;
import org.zexnocs.teaneko.mc.core.command.PaperCommandContext;
import org.zexnocs.teaneko.mc.core.command.completion.PaperCommandCompletionContext;
import org.zexnocs.teaneko.mc.core.command.completion.interfaces.IPaperCommandArgumentTypeHandler;
import org.zexnocs.teaneko.mc.utils.PaperCommandUtils;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * 将指令参数解析为在线玩家，并补全发送者可见的在线玩家名称。
 *
 * @author zExNocs
 * @date 2026/09/12
 * @since paperMC-1.0.0alpha
 */
@Component
public final class OnlinePlayerArgumentTypeHandler
        implements IPaperCommandArgumentTypeHandler<Player> {
    /**
     * 获取在线玩家参数类型。
     *
     * @return {@link Player} 类型
     */
    @Override
    public Class<Player> targetType() {
        return Player.class;
    }

    /**
     * 按精确名称或 UUID 查找当前在线玩家。
     *
     * @param input 玩家名称或 UUID
     * @param commandData 当前指令数据
     * @return 在线玩家；无法解析或当前客户端不是 Paper 时返回空
     */
    @Override
    public Optional<Player> parse(String input, CommandData<?> commandData) {
        if (!(commandData.getRawData() instanceof PaperCommandContext context)) {
            return Optional.empty();
        }
        return Optional.ofNullable(PaperCommandUtils.callOnMainThread(
                context.plugin(),
                () -> {
                    UUID uuid = parseUuid(input);
                    return uuid == null
                            ? context.plugin().getServer().getPlayerExact(input)
                            : context.plugin().getServer().getPlayer(uuid);
                }
        ));
    }

    /**
     * 补全当前发送者可见的在线玩家名称。
     *
     * @param context 当前参数补全上下文
     * @return 在线玩家名称
     */
    @Override
    public Collection<String> complete(PaperCommandCompletionContext context) {
        Player sender = context.sender() instanceof Player player ? player : null;
        return context.plugin().getServer().getOnlinePlayers().stream()
                .filter(player -> sender == null || sender.canSee(player))
                .map(Player::getName)
                .toList();
    }

    /**
     * 尝试解析 UUID。
     *
     * @param input 输入文本
     * @return UUID；格式错误时返回 {@code null}
     */
    private UUID parseUuid(String input) {
        try {
            return UUID.fromString(input);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
