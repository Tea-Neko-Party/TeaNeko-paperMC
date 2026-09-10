package org.zexnocs.teanekopapermc.command;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekocore.command.CommandScanner;
import org.zexnocs.teanekocore.command.api.Command;
import org.zexnocs.teanekocore.command.interfaces.ICommandDispatcher;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IBeanScanner;
import org.zexnocs.teanekopapermc.command.api.TeaNekoMCCommand;
import org.zexnocs.teanekopapermc.utils.PaperCommandIntrospectionUtils;
import org.zexnocs.teanekopapermc.utils.PaperCommandUtils;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 扫描 Spring 指令 Bean，将 Minecraft 指令绑定到 Core 的完整分发链。
 * <p>
 * Core 继续负责注解扫描、参数转换、作用域、权限、事件和异步执行；
 * 本服务只负责 Paper 生命周期接入与 Bukkit 回调适配。
 *
 * @author zExNocs
 * @date 2026/09/10
 * @since paperMC-1.0.0alpha
 * @see CommandScanner
 * @see TeaNekoMCCommand
 */
@Service
public final class PaperCommandService implements IPaperCommandService {
    private final IBeanScanner beanScanner;
    private final CommandScanner commandScanner;
    private final ICommandDispatcher commandDispatcher;
    private final PaperCommandConverter commandConverter;
    private final AtomicBoolean registered = new AtomicBoolean(false);

    /**
     * 创建通用 Paper 指令服务。
     *
     * @param beanScanner Spring Bean 扫描器
     * @param commandScanner Core 指令扫描器
     * @param commandDispatcher Core 指令分发器
     * @param commandConverter Paper 上下文转换器
     */
    public PaperCommandService(IBeanScanner beanScanner,
                               CommandScanner commandScanner,
                               ICommandDispatcher commandDispatcher,
                               PaperCommandConverter commandConverter) {
        this.beanScanner = beanScanner;
        this.commandScanner = commandScanner;
        this.commandDispatcher = commandDispatcher;
        this.commandConverter = commandConverter;
    }

    /**
     * 遍历 Spring 中同时带有 Core 与 Minecraft 指令注解的 Bean，并绑定生成的 plugin.yml 指令。
     *
     * @param plugin Paper 插件实例
     * @throws IllegalStateException 重复注册、注解无效或 plugin.yml 尚未重新生成时抛出
     */
    @Override
    public void registerAll(JavaPlugin plugin) {
        if (!registered.compareAndSet(false, true)) {
            throw new IllegalStateException("Paper 指令已经完成注册，不能重复执行。");
        }

        int commandCount = 0;
        try {
            var commandBeans = beanScanner.getBeansWithAnnotation(Command.class);
            for (var commandPair : commandBeans.values()) {
                Command coreMetadata = commandPair.first();
                Object commandBean = commandPair.second();
                Class<?> commandClass = beanScanner.getBeanClass(commandBean);
                TeaNekoMCCommand minecraftMetadata = commandClass
                        .getAnnotation(TeaNekoMCCommand.class);
                if (minecraftMetadata == null) {
                    continue;
                }
                validateCommand(commandClass, coreMetadata);

                String primaryName = PaperCommandUtils.normalizeCommandName(coreMetadata.value()[0]);
                PluginCommand pluginCommand = plugin.getCommand(primaryName);
                if (pluginCommand == null) {
                    throw new IllegalStateException("plugin.yml 缺少自动扫描的指令 /" + primaryName
                            + "，请重新执行 processResources 或构建插件。");
                }

                IPaperCommandTabCompleter tabCompleter = commandBean instanceof IPaperCommandTabCompleter completer
                        ? completer
                        : null;
                PaperBukkitCommandAdapter adapter = new PaperBukkitCommandAdapter(
                        plugin,
                        this,
                        coreMetadata,
                        minecraftMetadata,
                        PaperCommandIntrospectionUtils.getSubCommandNames(commandClass),
                        tabCompleter
                );
                pluginCommand.setExecutor(adapter);
                pluginCommand.setTabCompleter(adapter);
                commandCount++;
            }
        } catch (RuntimeException exception) {
            registered.set(false);
            throw exception;
        }

        plugin.getLogger().info("已从 Spring Boot 自动注册 " + commandCount + " 个 Minecraft 指令。");
    }

    /**
     * 将 Paper 上下文转换后交给 Core 的错误、帮助、事件与执行器链。
     *
     * @param context Paper 指令上下文
     */
    @Override
    public void dispatch(PaperCommandContext context) {
        commandDispatcher.dispatch(
                commandConverter.__parse(context),
                new PaperCommandErrorHandler(context),
                new PaperHelpSubCommandHandler(context)
        );
    }

    /**
     * 验证 Minecraft 指令注解对应的 Core 声明可被 Paper 安全注册。
     *
     * @param commandClass 指令 Bean 类型
     * @param coreMetadata Core 指令元数据
     */
    private void validateCommand(Class<?> commandClass, Command coreMetadata) {
        if (coreMetadata.mode() != Command.CommandMode.PREFIX) {
            throw new IllegalStateException("Minecraft 指令不支持 Core 正则模式："
                    + commandClass.getName());
        }
        if (coreMetadata.value().length == 0
                || PaperCommandUtils.normalizeCommandName(coreMetadata.value()[0]).isBlank()) {
            throw new IllegalStateException("Minecraft 指令缺少有效的主名称："
                    + commandClass.getName());
        }
        if (!PaperCommandIntrospectionUtils.supportsPaperClient(coreMetadata.supportedClients())) {
            throw new IllegalStateException("Minecraft 指令未声明支持 Paper 客户端："
                    + commandClass.getName());
        }
        if (!PaperCommandIntrospectionUtils.hasPaperCompatibleMethod(commandClass)) {
            throw new IllegalStateException("Minecraft 指令没有兼容 PaperCommandContext 的执行方法："
                    + commandClass.getName());
        }
        for (String coreName : coreMetadata.value()) {
            if (commandScanner.getPrefixCommand(coreName) == null) {
                throw new IllegalStateException("Core 指令扫描器中缺少指令：" + coreName);
            }
        }
    }
}
