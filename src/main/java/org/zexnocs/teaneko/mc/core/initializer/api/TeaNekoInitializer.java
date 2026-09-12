package org.zexnocs.teaneko.mc.core.initializer.api;

import org.springframework.stereotype.Component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 将 Spring Bean 标记为 Paper 初始化器，并声明失败策略与初始化顺序。
 * <p>
 * 被标记的类必须实现 {@link ITeaNekoInitializer}。初始化器默认是可选组件、优先级为零；
 * 必须组件失败时会终止插件启动，优先级数值越大越早执行。
 *
 * @author zExNocs
 * @date 2026/09/12
 * @since paperMC-1.0.0alpha
 */
@Component
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface TeaNekoInitializer {
    /**
     * 表示该初始化器失败时是否必须终止插件启动。
     *
     * @return 必须成功时返回 {@code true}，默认作为可选初始化器处理
     */
    boolean required() default false;

    /**
     * 获取初始化优先级，数值越大越早执行。
     *
     * @return 初始化优先级
     */
    int priority() default 0;
}
