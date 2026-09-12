package org.zexnocs.teaneko.app.teauser.interfaces;

import org.jspecify.annotations.NonNull;
import org.zexnocs.teaneko.app.client.api.ITeaNekoClient;
import org.zexnocs.teaneko.core.actuator.task.TaskFuture;
import org.zexnocs.teaneko.core.database.itemdata.interfaces.IItemDataDTO;

import java.util.UUID;

/**
 * 用于获取用户猫猫币的服务接口。
 *
 * @author zExNocs
 * @date 2026/02/27
 * @since 4.0.11
 */
public interface ITeaUserCoinService {

    /**
     * 获取用户的猫猫币 dto future。
     *
     * @param userId 用户的 UUID
     * @return 用户的猫猫币 dto future
     * @see TaskFuture
     */
    @NonNull
    IItemDataDTO<?> getCoin(UUID userId);

    /**
     * 根据 client 和 平台用户 ID 获取用户的猫猫币 dto future。
     *
     * @see TaskFuture
     * @param client 客户端
     * @param userId 平台用户 ID
     * @return 用户的猫猫币 dto future
     */
    TaskFuture<IItemDataDTO<?>> getCoin(ITeaNekoClient client, String userId);
}
