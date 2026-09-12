package org.zexnocs.teaneko.mc.core.command.completion;

import org.bukkit.OfflinePlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.zexnocs.teaneko.mc.core.initializer.api.ITeaNekoInitializer;
import org.zexnocs.teaneko.mc.core.initializer.api.TeaNekoInitializer;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 缓存服务器存在过的玩家名称与 UUID，供参数解析和主线程补全读取。
 *
 * @author zExNocs
 * @date 2026/09/12
 * @since paperMC-1.0.0alpha
 */
@TeaNekoInitializer(priority = 200)
public final class PaperKnownPlayerDirectory implements ITeaNekoInitializer {
    private final Map<UUID, String> namesByUuid = new ConcurrentHashMap<>();
    private final Map<String, UUID> uuidsByName = new ConcurrentHashMap<>();
    private Listener listener;

    /**
     * 读取服务器已有玩家，并监听之后的登录事件。
     *
     * @param plugin 当前 Paper 插件
     */
    @Override
    public void initialize(JavaPlugin plugin) {
        for (OfflinePlayer player : plugin.getServer().getOfflinePlayers()) {
            remember(player.getUniqueId(), player.getName());
        }
        for (org.bukkit.entity.Player player : plugin.getServer().getOnlinePlayers()) {
            remember(player.getUniqueId(), player.getName());
        }
        listener = new KnownPlayerListener(this);
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
    }

    /**
     * 按玩家名称查找已经存在过的玩家 UUID。
     *
     * @param playerName 玩家名称
     * @return 玩家 UUID
     */
    public Optional<UUID> findUuid(String playerName) {
        if (playerName == null || playerName.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(uuidsByName.get(playerName.toLowerCase(Locale.ROOT)));
    }

    /**
     * 判断 UUID 是否属于服务器已知玩家。
     *
     * @param playerUuid 玩家 UUID
     * @return 已经存在过时返回 {@code true}
     */
    public boolean isKnown(UUID playerUuid) {
        return namesByUuid.containsKey(playerUuid);
    }

    /**
     * 获取全部已知且具有有效名称的玩家名。
     *
     * @return 按名称排序的只读快照
     */
    public List<String> getKnownPlayerNames() {
        return namesByUuid.values().stream()
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    /**
     * 记录玩家当前名称，并清理该 UUID 的旧名称映射。
     *
     * @param playerUuid 玩家 UUID
     * @param playerName 玩家名称；为空时忽略
     */
    public void remember(UUID playerUuid, String playerName) {
        if (playerUuid == null || playerName == null || playerName.isBlank()) {
            return;
        }
        String previousName = namesByUuid.put(playerUuid, playerName);
        if (previousName != null && !previousName.equalsIgnoreCase(playerName)) {
            uuidsByName.remove(previousName.toLowerCase(Locale.ROOT), playerUuid);
        }
        uuidsByName.put(playerName.toLowerCase(Locale.ROOT), playerUuid);
    }

    /**
     * 注销监听器并释放玩家名称快照。
     */
    @Override
    public void close() {
        if (listener != null) {
            HandlerList.unregisterAll(listener);
            listener = null;
        }
        namesByUuid.clear();
        uuidsByName.clear();
    }

    /**
     * 隔离 Spring Bean 与 Bukkit Listener 接口，避免 Spring 解析 Paper 外部类型元数据。
     *
     * @author zExNocs
     * @date 2026/09/12
     * @since paperMC-1.0.0alpha
     */
    private static final class KnownPlayerListener implements Listener {
        private final PaperKnownPlayerDirectory playerDirectory;

        private KnownPlayerListener(PaperKnownPlayerDirectory playerDirectory) {
            this.playerDirectory = playerDirectory;
        }

        /**
         * 在玩家进入服务器前更新名称缓存。
         *
         * @param event 异步登录事件
         */
        @EventHandler
        public void onPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
            playerDirectory.remember(event.getUniqueId(), event.getName());
        }
    }
}
