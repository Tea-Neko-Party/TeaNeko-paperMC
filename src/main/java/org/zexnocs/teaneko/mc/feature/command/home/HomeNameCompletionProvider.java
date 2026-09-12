package org.zexnocs.teaneko.mc.feature.command.home;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.zexnocs.teaneko.core.logger.ILogger;
import org.zexnocs.teaneko.mc.core.command.completion.PaperCommandCompletionContext;
import org.zexnocs.teaneko.mc.core.command.completion.interfaces.IPaperCommandCompletionProvider;
import org.zexnocs.teaneko.mc.core.initializer.api.ITeaNekoInitializer;
import org.zexnocs.teaneko.mc.core.initializer.api.TeaNekoInitializer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 提供当前玩家的家名称补全，并在登录阶段异步预加载名称缓存。
 *
 * @author zExNocs
 * @date 2026/09/12
 * @since paperMC-1.0.0alpha
 * @see HomeService
 */
@TeaNekoInitializer(priority = 150)
public final class HomeNameCompletionProvider
        implements IPaperCommandCompletionProvider, ITeaNekoInitializer {
    private final HomeService homeService;
    private final ILogger logger;
    private final Set<BukkitTask> reloadTasks = ConcurrentHashMap.newKeySet();
    private volatile boolean active;
    private Listener listener;

    /**
     * 创建 Home 名称补全提供器。
     *
     * @param homeService Home 数据服务
     * @param logger 日志接口
     */
    public HomeNameCompletionProvider(HomeService homeService, ILogger logger) {
        this.homeService = homeService;
        this.logger = logger;
    }

    /**
     * 注册登录监听器，并为插件重载时已经在线的玩家提交异步预加载。
     *
     * @param plugin 当前 Paper 插件
     */
    @Override
    public void initialize(JavaPlugin plugin) {
        active = true;
        listener = new HomePreloadListener(this);
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            BukkitTask task = plugin.getServer().getScheduler().runTaskAsynchronously(
                    plugin,
                    () -> preloadSafely(player.getUniqueId())
            );
            reloadTasks.add(task);
        }
    }

    /**
     * 返回默认家名称和当前玩家已经缓存的全部家名称。
     *
     * @param context 当前参数补全上下文
     * @return Home 名称候选值
     */
    @Override
    public Collection<String> complete(PaperCommandCompletionContext context) {
        Collection<String> candidates = new ArrayList<>();
        candidates.add(HomeCommandUtils.DEFAULT_HOME_NAME);
        if (context.sender() instanceof Player player) {
            candidates.addAll(homeService.findCachedHomeNames(player.getUniqueId()));
        }
        return candidates;
    }

    /**
     * 加载玩家名称快照，并将数据库错误限制在补全功能内。
     */
    private void preloadSafely(UUID playerUuid) {
        try {
            homeService.preloadHomeNames(playerUuid);
            if (!active) {
                // 关闭过程中完成的异步任务不得在资源释放后留下缓存。
                homeService.clearCachedHomeNames();
            }
        } catch (RuntimeException exception) {
            logger.warn(
                    getClass().getName(),
                    "预加载玩家 " + playerUuid + " 的 Home 名称失败，将使用已有补全缓存。",
                    exception
            );
        }
    }

    /**
     * 注销监听器、取消重载预加载任务并清理名称缓存。
     */
    @Override
    public void close() {
        active = false;
        if (listener != null) {
            HandlerList.unregisterAll(listener);
            listener = null;
        }
        reloadTasks.forEach(BukkitTask::cancel);
        reloadTasks.clear();
        homeService.clearCachedHomeNames();
    }

    /**
     * 隔离 Spring Bean 与 Bukkit Listener 接口，避免 Spring 解析 Paper 外部类型元数据。
     *
     * @author zExNocs
     * @date 2026/09/12
     * @since paperMC-1.0.0alpha
     */
    private static final class HomePreloadListener implements Listener {
        private final HomeNameCompletionProvider completionProvider;

        private HomePreloadListener(HomeNameCompletionProvider completionProvider) {
            this.completionProvider = completionProvider;
        }

        /**
         * 在异步登录事件中加载玩家家名称，使首次补全无需访问数据库。
         *
         * @param event 玩家异步预登录事件
         */
        @EventHandler
        public void onPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
            if (completionProvider.active) {
                completionProvider.preloadSafely(event.getUniqueId());
            }
        }
    }
}
