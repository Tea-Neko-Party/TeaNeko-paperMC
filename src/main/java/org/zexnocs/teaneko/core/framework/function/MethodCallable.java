package org.zexnocs.teaneko.core.framework.function;

import org.zexnocs.teaneko.core.actuator.task.api.SetTaskStage;
import org.zexnocs.teaneko.core.utils.MethodCallableUtils;

import java.io.Serializable;
import java.util.concurrent.Callable;

/**
 * 支持从方法中提取注解信息的接口。
 * 使用 {@link MethodCallableUtils} 提取出方法注解
 *
 * @see SetTaskStage
 * @author zExNocs
 * @date 2026/02/18
 */
@FunctionalInterface
public interface MethodCallable<T> extends Callable<T>, Serializable {
}
