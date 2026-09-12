package org.zexnocs.teaneko.app.response.exception;

import org.zexnocs.teaneko.app.response.interfaces.IResponseService;

/**
 * 如果 echo 已经存在于 {@link IResponseService} 中，则抛出该异常。
 *
 * @author zExNocs
 * @date 2026/02/23
 */
public class ResponseEchoDuplicateException extends RuntimeException {
    /**
     * 构造一个新的 ResponseEchoDuplicateException。
     *
     * @param message 异常信息
     */
    public ResponseEchoDuplicateException(String message) {
        super(message);
    }
}
