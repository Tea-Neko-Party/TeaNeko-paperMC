package org.zexnocs.teaneko.app.sender.api.sender_box;

import org.zexnocs.teaneko.app.response.api.IPlatformUserResponseData;
import org.zexnocs.teaneko.core.actuator.task.TaskFuture;

/**
 * 获取平台用户信息的发送器接口。
 *
 * @see IPlatformUserResponseData
 * @author zExNocs
 * @date 2026/02/24
 */
public interface IPlatformUserGetSender {
    /**
     * 获取平台用户信息。
     *
     * @param userId 用户ID
     * @return 包含用户信息的发送数据对象
     */
    TaskFuture<? extends IPlatformUserResponseData> getPlatformUserInfo(String userId);
}
