package org.zexnocs.teanekoapp;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * TeaNeko Spring Boot 应用配置入口，扫描 core 与应用交互层的全部组件。
 * <p>
 * Paper 插件通过 {@code TeaNekoPaperPlugin} 创建本应用上下文，不使用独立的 {@code main} 方法，
 * 以确保生命周期由 Paper 管理。
 *
 * @author zExNocs
 * @date 2026/09/09
 * @since paperMC-1.0.0alpha
 * @see org.zexnocs.teanekopapermc.TeaNekoPaperPlugin
 */
@SpringBootApplication(scanBasePackages = {"org.zexnocs"})
public class TeaNekoAppApplication {
}
