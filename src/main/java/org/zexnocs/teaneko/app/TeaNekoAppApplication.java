package org.zexnocs.teaneko.app;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.zexnocs.teaneko.mc.TeaNekoPaperPlugin;

/**
 * TeaNeko Spring Boot 应用配置入口，扫描 core 与应用交互层的全部组件。
 * <p>
 * Paper 插件通过 {@code TeaNekoPaperPlugin} 创建本应用上下文，不使用独立的 {@code main} 方法，
 * 以确保生命周期由 Paper 管理。
 *
 * @author zExNocs
 * @date 2026/09/12
 * @since paperMC-1.0.0alpha
 * @see TeaNekoPaperPlugin
 */
@SpringBootApplication(scanBasePackages = TeaNekoAppApplication.ROOT_SCAN_PACKAGE)
public class TeaNekoAppApplication {
    /** Spring 运行期与 Paper 指令构建期共同使用的根扫描包。 */
    public static final String ROOT_SCAN_PACKAGE = "org.zexnocs";
}
