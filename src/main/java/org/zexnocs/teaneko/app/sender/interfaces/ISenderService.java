package org.zexnocs.teaneko.app.sender.interfaces;

import org.zexnocs.teaneko.app.response.ResponseEvent;
import org.zexnocs.teaneko.app.response.exception.ResponseEchoDuplicateException;
import org.zexnocs.teaneko.app.response.interfaces.IResponseService;
import org.zexnocs.teaneko.app.sender.SentEvent;
import org.zexnocs.teaneko.app.sender.api.ISendData;
import org.zexnocs.teaneko.core.actuator.task.TaskFuture;
import org.zexnocs.teaneko.core.actuator.task.interfaces.ITaskResult;
import org.zexnocs.teaneko.core.event.interfaces.IEvent;

import java.time.Duration;
import java.util.List;

/**
 * 用于统一推送发送给客户端事件的服务。
 * 发送流程：
 * <p>准备好发送的信息
 * <p>→ 提交给 {@link ISenderService} 发送信息
 * <p>→ {@link ISenderService} 将信息注册给 {@link IResponseService}
 * <p>→ 推送 {@link SentEvent} 事件
 * <p>→ 客户端响应信息后触发 {@link ResponseEvent} 事件
 * <p>→ {@link IResponseService} 监听到事件后处理响应信息
 *
 * @see IResponseService
 * @see SentEvent
 * @see ResponseEvent
 * @author zExNocs
 * @date 2026/02/22
 * @since 4.0.8
 */
public interface ISenderService {
    /**
     * 发送信息，并返回 future 来允许处理响应信息。
     * <br>默认使用 {@link SentEvent} 来处理该消息。
     *
     * @param <R>           响应数据类型
     * @param <S>           发送数据类型，必须实现 {@link ISendData} 接口
     * @param sendData      要发送的数据
     * @param delay         发送延迟的时间，单位毫秒
     * @param maxRetryCount 最大重试次数
     * @param retryDelay    重试延迟的时间，单位毫秒
     * @return {@link TaskFuture }<{@link ITaskResult }<{@link List }<{@link R }>>>}
     * @throws ResponseEchoDuplicateException 如果 echo 已经存在于注册表中，则抛出该异常
     * @see ISendData
     * @see TaskFuture
     * @see ITaskResult
     */
    default <R, S extends ISendData<R>> TaskFuture<ITaskResult<List<R>>> send(
            S sendData,
            Duration delay,
            int maxRetryCount,
            Duration retryDelay) throws ResponseEchoDuplicateException {
        return send(new SentEvent<>(sendData), delay, maxRetryCount, retryDelay);
    }

    /**
     * 使用指定的
     * {@link SentEvent}
     * 来发送信息
     *
     *
     * @param <R>           响应数据类型
     * @param <S>           发送数据类型，必须实现 {@link ISendData} 接口
     * @param event         包含该数据的事件
     * @param delay         发送延迟的时间，单位毫秒
     * @param maxRetryCount 最大重试次数
     * @param retryDelay    重试延迟的时间，单位毫秒
     * @return {@link TaskFuture }<{@link ITaskResult }<{@link List }<{@link R }>>>}
     * @throws ResponseEchoDuplicateException 如果 echo 已经存在于注册表中，则抛出该异常
     */
    <R, S extends ISendData<R>> TaskFuture<ITaskResult<List<R>>> send(
            IEvent<S> event,
            Duration delay,
            int maxRetryCount,
            Duration retryDelay) throws ResponseEchoDuplicateException;
}
