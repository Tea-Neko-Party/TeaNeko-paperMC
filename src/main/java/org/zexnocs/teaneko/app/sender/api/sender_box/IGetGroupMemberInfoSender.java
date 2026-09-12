package org.zexnocs.teaneko.app.sender.api.sender_box;

import org.zexnocs.teaneko.app.response.api.IGroupMemberResponseData;
import org.zexnocs.teaneko.core.actuator.task.TaskFuture;

/**
 * 获取群成员信息的发送器。
 *
 * @author zExNocs
 * @date 2026/03/07
 * @since 4.1.0
 */
public interface IGetGroupMemberInfoSender {
    /**
     * 获取群成员信息。
     *
     * @param groupId 群号
     * @param userId  成员QQ号
     */
    TaskFuture<? extends IGroupMemberResponseData> get(String groupId, String userId);
}
