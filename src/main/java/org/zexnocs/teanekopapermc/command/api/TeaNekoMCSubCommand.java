package org.zexnocs.teanekopapermc.command.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 为 Core 子指令声明独立的 Minecraft 权限元数据。
 * <p>
 * 该注解必须与 {@link org.zexnocs.teanekocore.command.api.SubCommand} 同时使用。
 * 未标注时，子指令沿用顶级 {@link TeaNekoMCCommand} 的 Bukkit 权限。
 *
 * @author zExNocs
 * @date 2026/09/11
 * @since paperMC-1.0.0alpha
 * @see TeaNekoMCCommand
 * @see org.zexnocs.teanekocore.command.api.SubCommand
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface TeaNekoMCSubCommand {
    /**
     * 获取子指令要求的 Bukkit 权限节点。
     *
     * @return 权限节点；留空时沿用顶级指令权限
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
    TeaNekoMCCommand.PermissionDefault permissionDefault()
            default TeaNekoMCCommand.PermissionDefault.TRUE;
}
