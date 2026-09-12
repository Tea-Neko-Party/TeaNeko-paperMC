package org.zexnocs.teanekopapermc.core.logger;

import lombok.Setter;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekocore.logger.ILogger;
import org.zexnocs.teanekocore.logger.LoggerReportData;
import org.zexnocs.teanekocore.utils.ExceptionUtils;

import java.util.logging.Logger;

@Service
public class JavaLogger implements ILogger {

    @Setter
    private Logger logger;

    public JavaLogger() {
        // 临时使用一个默认的 Logger，避免空指针异常
        logger = Logger.getLogger("NoLogger");
    }

    /**
     * 记录一般日志。
     *
     * @param namespace 日志命名空间
     * @param message   日志信息
     */
    @Override
    public void info(String namespace, String message) {
        logger.info("""
                %s: %s"""
                .formatted(namespace, message));
    }

    /**
     * 使用 report 数据记录错误日志并报告给监护人。
     *
     * @param data 日志报告数据
     */
    @Override
    public void errorWithReport(LoggerReportData data) {
        this.error(data.getNamespace(),
                data.getMessage(),
                data.getThrowable());
    }

    /**
     * 记录错误日志。
     *
     * @param namespace 日志命名空间
     * @param message   错误信息
     * @param throwable 异常
     */
    @Override
    public void error(String namespace, String message, Throwable throwable) {
        logger.severe("""
                %s: %s
                %s"""
                .formatted(namespace, message, ExceptionUtils.instance.buildExceptionMessage(throwable)));
    }

    /**
     * 记录警告日志。
     *
     * @param namespace 日志命名空间
     * @param message   警告信息
     * @param throwable 异常
     */
    @Override
    public void warn(String namespace, String message, Throwable throwable) {
        logger.warning("""
                %s: %s
                %s"""
                .formatted(namespace, message, ExceptionUtils.instance.buildExceptionMessage(throwable)));
    }

    /**
     * 记录调试信息。
     *
     * @param namespace 日志命名空间
     * @param message   调试信息
     * @param throwable 异常
     */
    @Override
    public void debug(String namespace, String message, Throwable throwable) {
        logger.fine("""
                %s: %s
                %s"""
                .formatted(namespace, message, ExceptionUtils.instance.buildExceptionMessage(throwable)));
    }
}
