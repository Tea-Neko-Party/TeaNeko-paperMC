package org.zexnocs.teanekopapermc.home;

import org.springframework.stereotype.Service;
import org.zexnocs.teanekocore.database.easydata.core.interfaces.IEasyDataDto;
import org.zexnocs.teanekocore.database.easydata.core.interfaces.IEasyDataDtoTaskConfig;
import org.zexnocs.teanekocore.database.easydata.general.GeneralEasyData;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 使用通用 EasyData 管理玩家家的位置与个人数量上限。
 * <p>
 * 每个玩家 UUID 对应一个数据目标，家位置使用 {@code home:名称} 作为键，
 * 数量上限使用独立键保存。首次读取可能访问数据库，因此调用方不得在 Paper 主线程执行。
 *
 * @author zExNocs
 * @date 2026/09/10
 * @since paperMC-1.0.0alpha
 * @see GeneralEasyData
 */
@Service
public class HomeService {
    /** 未配置个人上限时允许创建的家数量。 */
    public static final int DEFAULT_HOME_LIMIT = 3;

    private static final String HOME_NAMESPACE = "teaneko-paper-home";
    private static final String HOME_KEY_PREFIX = "home:";
    private static final String HOME_LIMIT_KEY = "max-homes";

    /**
     * 同一玩家的“检查数量并写入”必须串行，避免快速重复指令突破数量上限。
     */
    private final ConcurrentMap<UUID, Object> playerLocks = new ConcurrentHashMap<>();

    /**
     * 创建或更新一个家。更新已有家不受数量上限影响。
     * <p>
     * 该方法会等待 EasyData 写入完成，应从异步线程调用。
     *
     * @param playerUuid 玩家 UUID
     * @param homeName 经过规范化的家名称
     * @param worldName 世界名称
     * @param x X 坐标
     * @param y Y 坐标
     * @param z Z 坐标
     * @param yaw 水平朝向
     * @param pitch 垂直朝向
     * @return 保存结果
     */
    public SaveResult saveHome(UUID playerUuid, String homeName, String worldName,
                               double x, double y, double z, float yaw, float pitch) {
        Objects.requireNonNull(playerUuid, "玩家 UUID 不能为空。");
        Objects.requireNonNull(homeName, "家名称不能为空。");
        Objects.requireNonNull(worldName, "世界名称不能为空。");

        synchronized (getPlayerLock(playerUuid)) {
            IEasyDataDto playerData = getPlayerData(playerUuid);
            String homeKey = getHomeKey(homeName);
            int homeLimit = getHomeLimit(playerData);
            boolean updating = playerData.has(homeKey);
            if (!updating && countHomes(playerData) >= homeLimit) {
                return new SaveResult(SaveStatus.LIMIT_REACHED, homeLimit);
            }

            HomeSnapshot snapshot = new HomeSnapshot(
                    homeName, worldName, x, y, z, yaw, pitch
            );
            pushAndWait(playerData.getTaskConfig("保存玩家家位置").set(homeKey, snapshot));
            return new SaveResult(updating ? SaveStatus.UPDATED : SaveStatus.CREATED, homeLimit);
        }
    }

    /**
     * 查询玩家指定名称的家。
     *
     * @param playerUuid 玩家 UUID
     * @param homeName 经过规范化的家名称
     * @return 家位置快照
     */
    public Optional<HomeSnapshot> findHome(UUID playerUuid, String homeName) {
        HomeSnapshot snapshot = getPlayerData(playerUuid)
                .get(getHomeKey(homeName), HomeSnapshot.class);
        return Optional.ofNullable(snapshot);
    }

    /**
     * 查询玩家的全部家。
     *
     * @param playerUuid 玩家 UUID
     * @return 按名称排序的位置快照
     */
    public List<HomeSnapshot> findAllHomes(UUID playerUuid) {
        IEasyDataDto playerData = getPlayerData(playerUuid);
        return playerData.keySet().stream()
                .filter(key -> key.startsWith(HOME_KEY_PREFIX))
                .map(key -> playerData.get(key, HomeSnapshot.class))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(HomeSnapshot::homeName))
                .toList();
    }

    /**
     * 获取玩家家数量上限，未配置时返回默认值。
     *
     * @param playerUuid 玩家 UUID
     * @return 最大家数量
     */
    public int getHomeLimit(UUID playerUuid) {
        return getHomeLimit(getPlayerData(playerUuid));
    }

    /**
     * 设置玩家家数量上限。降低上限不会删除其已有的家。
     * <p>
     * 该方法会等待 EasyData 写入完成，应从异步线程调用。
     *
     * @param playerUuid 玩家 UUID
     * @param maxHomes 新的最大家数量，允许为零
     * @throws IllegalArgumentException 最大数量为负数时抛出
     */
    public void setHomeLimit(UUID playerUuid, int maxHomes) {
        if (maxHomes < 0) {
            throw new IllegalArgumentException("玩家家数量上限不能为负数。");
        }

        synchronized (getPlayerLock(playerUuid)) {
            IEasyDataDto playerData = getPlayerData(playerUuid);
            pushAndWait(playerData.getTaskConfig("修改玩家家数量上限")
                    .set(HOME_LIMIT_KEY, maxHomes));
        }
    }

    /**
     * 获取玩家对应的通用 EasyData 数据目标。
     *
     * @param playerUuid 玩家 UUID
     * @return 玩家数据目标
     */
    private IEasyDataDto getPlayerData(UUID playerUuid) {
        return GeneralEasyData.of(HOME_NAMESPACE).get(playerUuid.toString());
    }

    /**
     * 从玩家数据中获取家数量上限，并防御外部手工写入的负数。
     *
     * @param playerData 玩家数据目标
     * @return 非负的最大家数量
     */
    private int getHomeLimit(IEasyDataDto playerData) {
        Integer limit = playerData.get(HOME_LIMIT_KEY, Integer.class, DEFAULT_HOME_LIMIT);
        return limit == null ? DEFAULT_HOME_LIMIT : Math.max(0, limit);
    }

    /**
     * 统计数据目标中的家位置键，不把上限等配置计入数量。
     *
     * @param playerData 玩家数据目标
     * @return 当前家数量
     */
    private long countHomes(IEasyDataDto playerData) {
        return playerData.keySet().stream()
                .filter(key -> key.startsWith(HOME_KEY_PREFIX))
                .count();
    }

    /**
     * 将家名称转换为 EasyData 键。
     *
     * @param homeName 家名称
     * @return 数据键
     */
    private String getHomeKey(String homeName) {
        return HOME_KEY_PREFIX + homeName;
    }

    /**
     * 获取玩家级互斥锁。
     *
     * @param playerUuid 玩家 UUID
     * @return 该玩家固定使用的锁对象
     */
    private Object getPlayerLock(UUID playerUuid) {
        return playerLocks.computeIfAbsent(playerUuid, ignored -> new Object());
    }

    /**
     * 提交通用 EasyData 任务并等待数据库和缓存更新完成。
     *
     * @param taskConfig EasyData 写入任务
     */
    private void pushAndWait(IEasyDataDtoTaskConfig taskConfig) {
        taskConfig.pushWithFuture()
                .finish(ignored -> {
                    // 异常会由 join 重新抛给指令层统一记录并提示玩家。
                })
                .join();
    }

    /**
     * 表示创建家、更新家或达到数量上限三种保存状态。
     *
     * @author zExNocs
     * @date 2026/09/10
     * @since paperMC-1.0.0alpha
     */
    public enum SaveStatus {
        CREATED,
        UPDATED,
        LIMIT_REACHED
    }

    /**
     * 保存玩家家的结果。
     *
     * @param status 保存状态
     * @param maxHomes 当前玩家家数量上限
     * @author zExNocs
     * @date 2026/09/10
     * @since paperMC-1.0.0alpha
     */
    public record SaveResult(SaveStatus status, int maxHomes) {
    }

    /**
     * 可由 EasyData JSON 序列化的玩家家位置快照。
     *
     * @param homeName 家名称
     * @param worldName 世界名称
     * @param x X 坐标
     * @param y Y 坐标
     * @param z Z 坐标
     * @param yaw 水平朝向
     * @param pitch 垂直朝向
     * @author zExNocs
     * @date 2026/09/10
     * @since paperMC-1.0.0alpha
     */
    public record HomeSnapshot(String homeName, String worldName,
                               double x, double y, double z, float yaw, float pitch) {
    }
}
