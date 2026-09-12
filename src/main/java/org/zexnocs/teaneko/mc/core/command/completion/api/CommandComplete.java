package org.zexnocs.teaneko.mc.core.command.completion.api;

import org.zexnocs.teaneko.mc.core.command.completion.interfaces.IPaperCommandCompletionProvider;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 为指令方法的单个参数声明静态或动态补全来源。
 * <p>
 * 静态值、显式提供器和参数类型提供器默认合并，并由框架统一过滤与排序。
 *
 * @author zExNocs
 * @date 2026/09/12
 * @since paperMC-1.0.0alpha
 * @see IPaperCommandCompletionProvider
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface CommandComplete {
    /**
     * 获取固定补全候选值。
     *
     * @return 固定候选值
     */
    String[] value() default {};

    /**
     * 获取需要调用的 Spring 补全提供器类型。
     *
     * @return 补全提供器类型
     */
    Class<? extends IPaperCommandCompletionProvider>[] providers() default {};

    /**
     * 判断是否合并参数 Java 类型自带的补全候选。
     *
     * @return 使用类型补全时返回 {@code true}
     */
    boolean useTypeProvider() default true;
}
