package org.zexnocs.teanekopapermc.core.logger;

import org.springframework.stereotype.Service;
import org.zexnocs.teanekocore.logger.ILogger;
import org.zexnocs.teanekocore.logger.LoggerReportData;
import org.zexnocs.teanekocore.utils.ExceptionUtils;

import java.util.Objects;
import java.util.logging.Logger;

/**
 * 将 TeaNeko Core 日志接口适配到当前 Paper 插件的 Java Logger。
 * <p>
 * Spring 上下文创建期间使用临时 Logger；核心处理器启动后会注入插件 Logger，
 * 后续初始化器和业务日志因而能够显示正确的插件前缀。
 *
 * @author zExNocs
 * @date 2026/09/12
 * @since paperMC-1.0.0alpha
 * @see ILogger
 */
@Service
public class JavaLogger implements ILogger {

    private volatile Logger logger;

    /**
     * 创建使用临时 Logger 的适配器。
     */
    public JavaLogger() {
        // 临时使用一个默认的 Logger，避免空指针异常
        logger = Logger.getLogger("NoLogger");
    }

    /**
     * 切换为当前 Paper 插件提供的 Logger。
     *
     * @param logger Paper 插件 Logger
     */
    public void setLogger(Logger logger) {
        this.logger = Objects.requireNonNull(logger, "Paper 插件 Logger 不能为空。");
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
