package org.zexnocs.teanekopapermc.utils;

import org.zexnocs.teanekocore.command.CommandData;
import org.zexnocs.teanekocore.command.api.DefaultCommand;
import org.zexnocs.teanekocore.command.api.SubCommand;
import org.zexnocs.teanekocore.command.interfaces.ICommandClient;
import org.zexnocs.teanekopapermc.core.command.PaperCommandClient;
import org.zexnocs.teanekopapermc.core.command.PaperCommandContext;
import org.zexnocs.teanekopapermc.core.command.api.TeaNekoMCSubCommand;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 提供 Paper 指令扫描阶段使用的反射检查与子指令提取方法。
 *
 * @author zExNocs
 * @date 2026/09/11
 * @since paperMC-1.0.0alpha
 */
public final class PaperCommandIntrospectionUtils {
    private PaperCommandIntrospectionUtils() {
    }

    /**
     * 判断 Core 指令声明是否允许 Paper 客户端使用。
     *
     * @param supportedClients Core 指令支持的客户端类型
     * @return 支持 Paper 时返回 {@code true}
     */
    public static boolean supportsPaperClient(Class<? extends ICommandClient>[] supportedClients) {
        return Arrays.stream(supportedClients)
                .anyMatch(client -> client.isAssignableFrom(PaperCommandClient.class));
    }

    /**
     * 判断指令类中是否至少存在一个能接收 Paper 上下文的指令方法。
     * <p>
     * 该检查避免把只接受其他客户端原始消息类型的既有 Core 指令注册到 Minecraft。
     *
     * @param commandClass 指令 Bean 的实际类型
     * @return 存在兼容方法时返回 {@code true}
     */
    public static boolean hasPaperCompatibleMethod(Class<?> commandClass) {
        return Arrays.stream(commandClass.getDeclaredMethods())
                .filter(PaperCommandIntrospectionUtils::isCommandMethod)
                .anyMatch(PaperCommandIntrospectionUtils::isPaperCompatibleMethod);
    }

    /**
     * 提取指令类声明的全部子指令名称，用于默认参数补全。
     *
     * @param commandClass 指令 Bean 的实际类型
     * @return 去重后的子指令名称
     */
    public static Set<String> getSubCommandNames(Class<?> commandClass) {
        Set<String> names = new LinkedHashSet<>();
        for (Method method : commandClass.getDeclaredMethods()) {
            SubCommand subCommand = method.getAnnotation(SubCommand.class);
            if (subCommand != null) {
                names.addAll(Arrays.asList(subCommand.value()));
            }
        }
        return Set.copyOf(names);
    }

    /**
     * 提取子指令名称对应的 Minecraft 权限声明。
     *
     * @param commandClass 指令 Bean 的实际类型
     * @return 小写子指令名称到权限注解的只读映射
     * @throws IllegalStateException 注解未与 Core 子指令注解同时使用，或别名权限冲突时抛出
     */
    public static Map<String, TeaNekoMCSubCommand> getMinecraftSubCommandMetadata(
            Class<?> commandClass) {
        Map<String, TeaNekoMCSubCommand> metadataByName = new LinkedHashMap<>();
        for (Method method : commandClass.getDeclaredMethods()) {
            TeaNekoMCSubCommand minecraftMetadata = method.getAnnotation(TeaNekoMCSubCommand.class);
            if (minecraftMetadata == null) {
                continue;
            }
            SubCommand coreMetadata = method.getAnnotation(SubCommand.class);
            if (coreMetadata == null) {
                throw new IllegalStateException("@TeaNekoMCSubCommand 必须与 @SubCommand 同时使用："
                        + commandClass.getName() + "#" + method.getName());
            }
            for (String name : coreMetadata.value()) {
                String normalizedName = name.toLowerCase(Locale.ROOT);
                TeaNekoMCSubCommand existing = metadataByName.putIfAbsent(
                        normalizedName,
                        minecraftMetadata
                );
                if (existing != null && !existing.equals(minecraftMetadata)) {
                    throw new IllegalStateException("Minecraft 子指令权限声明冲突："
                            + commandClass.getName() + "#" + name);
                }
            }
        }
        return Map.copyOf(metadataByName);
    }

    /**
     * 判断方法是否属于默认指令或子指令。
     *
     * @param method 待检查方法
     * @return 是指令方法时返回 {@code true}
     */
    private static boolean isCommandMethod(Method method) {
        return method.isAnnotationPresent(DefaultCommand.class)
                || method.isAnnotationPresent(SubCommand.class);
    }

    /**
     * 判断一个指令方法的 CommandData 泛型是否接受 Paper 上下文。
     *
     * @param method 指令方法
     * @return 方法兼容时返回 {@code true}
     */
    private static boolean isPaperCompatibleMethod(Method method) {
        for (Parameter parameter : method.getParameters()) {
            if (!CommandData.class.isAssignableFrom(parameter.getType())) {
                continue;
            }
            Type parameterType = parameter.getParameterizedType();
            if (!(parameterType instanceof ParameterizedType typed)) {
                continue;
            }
            Type rawDataType = typed.getActualTypeArguments()[0];
            if (rawDataType instanceof Class<?> expectedClass
                    && !expectedClass.isAssignableFrom(PaperCommandContext.class)) {
                return false;
            }
            if (!(rawDataType instanceof Class<?>) && !(rawDataType instanceof WildcardType)) {
                return false;
            }
        }
        return true;
    }
}
