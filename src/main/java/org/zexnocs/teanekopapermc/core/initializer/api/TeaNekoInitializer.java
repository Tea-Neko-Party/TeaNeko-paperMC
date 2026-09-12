package org.zexnocs.teanekopapermc.core.initializer.api;

import org.springframework.stereotype.Component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 用于标记一个类为 PaperMC 初始化器的注解。被标记的类将在应用程序启动时被扫描和注册，以便在 PaperMC 环境中进行初始化操作。
 *
 * @author zExNocs
 * @date 2026/09/12
 * @since paperMC-1.0.0alpha
 */
@Component
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface TeaNekoInitializer {
}
