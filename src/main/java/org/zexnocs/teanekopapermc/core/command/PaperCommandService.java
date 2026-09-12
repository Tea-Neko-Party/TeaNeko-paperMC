package org.zexnocs.teanekopapermc.core.command;

import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;
import org.zexnocs.teanekocore.command.CommandScanner;
import org.zexnocs.teanekocore.command.api.Command;
import org.zexnocs.teanekocore.command.interfaces.ICommandDispatcher;
import org.zexnocs.teanekocore.logger.ILogger;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IBeanScanner;
import org.zexnocs.teanekopapermc.core.command.api.TeaNekoMCCommand;
import org.zexnocs.teanekopapermc.core.command.interfaces.IPaperCommandService;
import org.zexnocs.teanekopapermc.core.command.interfaces.IPaperCommandTabCompleter;
import org.zexnocs.teanekopapermc.core.initializer.api.ITeaNekoInitializer;
import org.zexnocs.teanekopapermc.core.initializer.api.TeaNekoInitializer;
import org.zexnocs.teanekopapermc.utils.PaperCommandIntrospectionUtils;
import org.zexnocs.teanekopapermc.utils.PaperCommandUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 扫描 Spring 指令 Bean，将 Minecraft 指令绑定到 Core 的完整分发链。
 * <p>
 * Core 继续负责注解扫描、参数转换、作用域、权限、事件和异步执行；
 * 本服务负责 Paper 生命周期接入与 Bukkit 回调适配。注册过程会先完成全部验证，
 * 再统一绑定执行器，并在失败或关闭时恢复原绑定。
 *
 * @author zExNocs
 * @date 2026/09/12
 * @since paperMC-1.0.0alpha
 * @see CommandScanner
 * @see TeaNekoMCCommand
 */
@TeaNekoInitializer(required = true, priority = 100)
public final class PaperCommandService implements IPaperCommandService, ITeaNekoInitializer {
    private final IBeanScanner beanScanner;
    private final CommandScanner commandScanner;
    private final ICommandDispatcher commandDispatcher;
    private final PaperCommandConverter commandConverter;
    private final ILogger iLogger;
    private final List<CommandBinding> activeBindings = new ArrayList<>();
    private boolean registered;

    /**
     * 创建通用 Paper 指令服务。
     *
     * @param beanScanner Spring Bean 扫描器
     * @param commandScanner Core 指令扫描器
     * @param commandDispatcher Core 指令分发器
     * @param commandConverter Paper 上下文转换器
     * @param iLogger TeaNeko 日志接口
     */
    public PaperCommandService(IBeanScanner beanScanner,
                               CommandScanner commandScanner,
                               ICommandDispatcher commandDispatcher,
                               PaperCommandConverter commandConverter, ILogger iLogger) {
        this.beanScanner = beanScanner;
        this.commandScanner = commandScanner;
        this.commandDispatcher = commandDispatcher;
        this.commandConverter = commandConverter;
        this.iLogger = iLogger;
    }

    /**
     * 遍历 Spring 中同时带有 Core 与 Minecraft 指令注解的 Bean，并绑定生成的 plugin.yml 指令。
     *
     * @param plugin Paper 插件实例
     * @throws IllegalStateException 重复注册、注解无效或 plugin.yml 尚未重新生成时抛出
     */
    @Override
    public synchronized void registerAll(JavaPlugin plugin) {
        if (registered) {
            throw new IllegalStateException("Paper 指令已经完成注册，不能重复执行。");
        }

        List<CommandBinding> preparedBindings = prepareBindings(plugin);
        List<CommandBinding> completedBindings = new ArrayList<>();
        try {
            // 所有声明均完成验证后再修改 Bukkit 指令，避免验证失败留下部分注册状态。
            for (CommandBinding binding : preparedBindings) {
                completedBindings.add(binding);
                binding.command().setExecutor(binding.adapter());
                binding.command().setTabCompleter(binding.adapter());
            }
        } catch (RuntimeException exception) {
            rollbackBindings(completedBindings);
            throw exception;
        }
        activeBindings.addAll(completedBindings);
        registered = true;
        iLogger.info(this.getClass().getName(),
                "已从 Spring Boot 自动注册 " + activeBindings.size() + " 个 Minecraft 指令。");
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

    /**
     * 扫描并完整验证待注册指令，生成尚未写入 Bukkit 的绑定计划。
     *
     * @param plugin Paper 插件实例
     * @return 已验证的指令绑定计划
     */
    private List<CommandBinding> prepareBindings(JavaPlugin plugin) {
        // 显式保证 Core 指令映射已经建立，不依赖 ReloadService 的隐式初始化顺序。
        commandScanner.init();
        List<CommandBinding> bindings = new ArrayList<>();
        Set<String> primaryNames = new HashSet<>();
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
            if (!primaryNames.add(primaryName)) {
                throw new IllegalStateException("运行期发现重复的 Minecraft 主指令 /" + primaryName + "。");
            }
            PluginCommand pluginCommand = plugin.getCommand(primaryName);
            if (pluginCommand == null) {
                throw new IllegalStateException("plugin.yml 缺少自动扫描的指令 /" + primaryName
                        + "，请重新执行 processResources 或构建插件。");
            }

            IPaperCommandTabCompleter tabCompleter =
                    commandBean instanceof IPaperCommandTabCompleter completer ? completer : null;
            PaperBukkitCommandAdapter adapter = new PaperBukkitCommandAdapter(
                    plugin,
                    this,
                    coreMetadata,
                    minecraftMetadata,
                    PaperCommandIntrospectionUtils.getSubCommandNames(commandClass),
                    PaperCommandIntrospectionUtils.getMinecraftSubCommandMetadata(commandClass),
                    tabCompleter
            );
            bindings.add(new CommandBinding(
                    pluginCommand,
                    adapter,
                    pluginCommand.getExecutor(),
                    pluginCommand.getTabCompleter()
            ));
        }
        return List.copyOf(bindings);
    }

    /**
     * 按相反顺序恢复一组 Bukkit 指令原有的执行器和补全器。
     *
     * @param bindings 需要回滚的绑定
     */
    private void rollbackBindings(List<CommandBinding> bindings) {
        for (int index = bindings.size() - 1; index >= 0; index--) {
            CommandBinding binding = bindings.get(index);
            try {
                restoreBinding(binding);
            } catch (RuntimeException exception) {
                iLogger.error(
                        this.getClass().getName(),
                        "回滚 Minecraft 指令 /" + binding.command().getName() + " 失败。",
                        exception
                );
            }
        }
    }

    /**
     * 仅在执行器仍属于本服务时恢复一次指令绑定，避免覆盖外部的后续修改。
     *
     * @param binding 指令绑定
     */
    private void restoreBinding(CommandBinding binding) {
        if (binding.command().getTabCompleter() == binding.adapter()) {
            binding.command().setTabCompleter(binding.previousTabCompleter());
        }
        if (binding.command().getExecutor() == binding.adapter()) {
            binding.command().setExecutor(binding.previousExecutor());
        }
    }

    /**
     * 初始化方法，用于在插件启动时进行必要的初始化操作。
     *
     * @param plugin 当前的 JavaPlugin 实例
     */
    @Override
    public void initialize(JavaPlugin plugin) {
        iLogger.info(this.getClass().getName(), "正在初始化 Paper 指令服务...");
        registerAll(plugin);
        iLogger.info(this.getClass().getName(), "已完成 Paper 指令服务的初始化。");
    }

    /**
     * 恢复本服务接管前的 Bukkit 指令绑定。
     */
    @Override
    public synchronized void close() {
        rollbackBindings(activeBindings);
        int commandCount = activeBindings.size();
        activeBindings.clear();
        registered = false;
        iLogger.info(this.getClass().getName(),
                "已释放 " + commandCount + " 个 Minecraft 指令绑定。");
    }

    /**
     * 保存 Bukkit 指令绑定及其被替换前的回调，用于失败回滚和插件关闭。
     *
     * @param command Bukkit 指令
     * @param adapter 当前服务创建的适配器
     * @param previousExecutor 原执行器
     * @param previousTabCompleter 原参数补全器
     * @author zExNocs
     * @date 2026/09/12
     * @since paperMC-1.0.0alpha
     */
    private record CommandBinding(PluginCommand command,
                                  PaperBukkitCommandAdapter adapter,
                                  CommandExecutor previousExecutor,
                                  TabCompleter previousTabCompleter) {
    }
}
