package org.zexnocs.teanekopapermc.utils;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;
import org.zexnocs.teanekopapermc.core.command.PaperCommandContext;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * 提供 Paper 指令常用的名称处理、线程切换、发送者解析与异常反馈方法。
 *
 * @author zExNocs
 * @date 2026/09/11
 * @since paperMC-1.0.0alpha
 */
public final class PaperCommandUtils {
    private PaperCommandUtils() {
    }

    /**
     * 将 Core 指令名称转换为 Bukkit 使用的不带斜杠小写名称。
     *
     * @param commandName Core 指令名称
     * @return Bukkit 指令名称
     */
    public static String normalizeCommandName(String commandName) {
        String normalized = commandName == null ? "" : commandName.trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        int namespaceIndex = normalized.lastIndexOf(':');
        if (namespaceIndex >= 0) {
            normalized = normalized.substring(namespaceIndex + 1);
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    /**
     * 要求当前发送者为玩家。
     *
     * @param context 指令上下文
     * @return 玩家实例；发送者不是玩家时返回 {@code null}
     */
    public static @Nullable Player requirePlayer(PaperCommandContext context) {
        if (context.sender() instanceof Player player) {
            return player;
        }
        sendOnMainThread(context, "该指令只能由玩家执行。");
        return null;
    }

    /**
     * 在服务器主线程执行任务；当前已在主线程时直接执行。
     *
     * @param plugin Paper 插件实例
     * @param task 主线程任务
     */
    public static void runOnMainThread(JavaPlugin plugin, Runnable task) {
        if (!plugin.isEnabled()) {
            return;
        }
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            plugin.getServer().getScheduler().runTask(plugin, task);
        }
    }

    /**
     * 在服务器主线程计算结果，并让当前异步调用等待计算完成。
     *
     * @param plugin Paper 插件实例
     * @param supplier 主线程计算
     * @param <T> 结果类型
     * @return 计算结果
     * @throws IllegalStateException 插件已停用时抛出
     */
    public static <T> T callOnMainThread(JavaPlugin plugin, Supplier<T> supplier) {
        if (!plugin.isEnabled()) {
            throw new IllegalStateException("插件已经停用，无法提交主线程任务。");
        }
        if (Bukkit.isPrimaryThread()) {
            return supplier.get();
        }

        CompletableFuture<T> future = new CompletableFuture<>();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            try {
                future.complete(supplier.get());
            } catch (Throwable throwable) {
                future.completeExceptionally(throwable);
            }
        });
        return future.join();
    }

    /**
     * 在服务器主线程向指令发送者发送消息。
     *
     * @param context 指令上下文
     * @param message 消息内容
     */
    public static void sendOnMainThread(PaperCommandContext context, String message) {
        runOnMainThread(context.plugin(), () -> context.sender().sendMessage(message));
    }

    /**
     * 记录指令执行异常，并向发送者返回统一错误提示。
     *
     * @param context 指令上下文
     * @param operation 操作说明
     * @param throwable 异常
     */
    public static void reportFailure(PaperCommandContext context,
                                     String operation,
                                     Throwable throwable) {
        context.plugin().getLogger().log(
                Level.SEVERE,
                operation + "，发送者：" + context.sender().getName(),
                throwable
        );
        sendOnMainThread(context, "指令执行失败，请联系服务器管理员。");
    }

    /**
     * 按 UUID、在线玩家名或服务器缓存中的离线玩家名解析玩家。
     *
     * @param context 指令上下文
     * @param identifier 玩家 UUID 或名称
     * @return 玩家标识；无法解析时返回 {@code null}
     */
    public static @Nullable ResolvedPlayer resolvePlayer(PaperCommandContext context,
                                                          String identifier) {
        UUID uuid = parseUuid(identifier);
        if (uuid != null) {
            String displayName = callOnMainThread(context.plugin(), () -> {
                OfflinePlayer offlinePlayer = context.plugin().getServer().getOfflinePlayer(uuid);
                return offlinePlayer.getName();
            });
            return new ResolvedPlayer(uuid, displayName == null ? uuid.toString() : displayName);
        }

        return callOnMainThread(context.plugin(), () -> {
            Player onlinePlayer = context.plugin().getServer().getPlayerExact(identifier);
            OfflinePlayer knownPlayer = onlinePlayer == null
                    ? context.plugin().getServer().getOfflinePlayerIfCached(identifier)
                    : onlinePlayer;
            if (knownPlayer == null) {
                return null;
            }
            String name = knownPlayer.getName();
            return new ResolvedPlayer(
                    knownPlayer.getUniqueId(),
                    name == null ? knownPlayer.getUniqueId().toString() : name
            );
        });
    }

    /**
     * 判断发送者是否是服务器控制台。
     *
     * @param sender 指令发送者
     * @return 是控制台时返回 {@code true}
     */
    public static boolean isConsole(CommandSender sender) {
        return sender instanceof ConsoleCommandSender;
    }

    /**
     * 根据当前输入前缀过滤、去重并排序补全候选项。
     *
     * @param candidates 候选项
     * @param input 当前输入
     * @return 可直接交给 Paper 的补全结果
     */
    public static List<String> completeByPrefix(Collection<String> candidates, String input) {
        String prefix = input.toLowerCase(Locale.ROOT);
        return candidates.stream()
                .distinct()
                .filter(candidate -> candidate.toLowerCase(Locale.ROOT).startsWith(prefix))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    /**
     * 补全当前发送者可见的在线玩家名称。
     * <p>
     * 该方法读取 Bukkit 在线玩家集合，只能从服务器主线程调用。
     *
     * @param context 指令上下文
     * @param input 当前玩家名称前缀
     * @return 在线玩家名称补全
     */
    public static List<String> completeVisiblePlayerNames(PaperCommandContext context,
                                                           String input) {
        Player sender = context.sender() instanceof Player player ? player : null;
        List<String> candidates = context.plugin().getServer().getOnlinePlayers().stream()
                .filter(player -> sender == null || sender.canSee(player))
                .map(Player::getName)
                .toList();
        return completeByPrefix(candidates, input);
    }

    /**
     * 尝试解析 UUID 文本。
     *
     * @param value 待解析文本
     * @return UUID；格式错误时返回 {@code null}
     */
    private static @Nullable UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    /**
     * 保存已解析玩家的 UUID 与显示名称。
     *
     * @param uuid 玩家 UUID
     * @param displayName 玩家显示名称
     * @author zExNocs
     * @date 2026/09/10
     * @since paperMC-1.0.0alpha
     */
    public record ResolvedPlayer(UUID uuid, String displayName) {
    }
}
