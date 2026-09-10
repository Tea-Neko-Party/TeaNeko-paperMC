package org.zexnocs.teanekopapermc.command.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明一个 Core 指令需要注册为 Minecraft 指令，并提供插件描述文件所需元数据。
 * <p>
 * 该注解必须与 {@link org.zexnocs.teanekocore.command.api.Command} 同时使用。
 * 未标注本注解的 Core 指令不会写入 plugin.yml，也不会绑定到 Paper。
 *
 * @author zExNocs
 * @date 2026/09/10
 * @since paperMC-1.0.0alpha
 * @see org.zexnocs.teanekocore.command.api.Command
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface TeaNekoMCCommand {
    /**
     * 获取显示在插件帮助中的指令说明。
     *
     * @return 指令说明
     */
    String description() default "";

    /**
     * 获取参数错误时显示的用法。
     *
     * @return 指令用法；留空时自动使用主指令名称
     */
    String usage() default "";

    /**
     * 获取 Bukkit 权限节点。
     *
     * @return 权限节点；留空时仅使用 Core 权限系统
     */
    String permission() default "";

    /**
     * 获取权限节点的说明。
     *
     * @return 权限说明
     */
    String permissionDescription() default "";

    /**
     * 获取权限节点的默认授权策略。
     *
     * @return 默认授权策略
     */
    PermissionDefault permissionDefault() default PermissionDefault.TRUE;

    /**
     * 定义 Bukkit 权限节点支持的默认授权策略。
     *
     * @author zExNocs
     * @date 2026/09/10
     * @since paperMC-1.0.0alpha
     */
    enum PermissionDefault {
        TRUE("true"),
        FALSE("false"),
        OP("op"),
        NOT_OP("not op");

        private final String yamlValue;

        PermissionDefault(String yamlValue) {
            this.yamlValue = yamlValue;
        }

        /**
         * 获取 plugin.yml 使用的文本值。
         *
         * @return YAML 权限默认值
         */
        public String getYamlValue() {
            return yamlValue;
        }
    }
}
