package org.zexnocs.teanekopapermc.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zexnocs.teanekopapermc.command.api.TeaNekoMCCommand;
import org.zexnocs.teanekopapermc.command.api.TeaNekoMCSubCommand;
import org.zexnocs.teanekopapermc.command.interfaces.IPaperCommandService;
import org.zexnocs.teanekopapermc.command.interfaces.IPaperCommandTabCompleter;
import org.zexnocs.teanekopapermc.utils.PaperCommandUtils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 将 Bukkit 的指令执行与补全回调适配到通用 Paper 指令服务。
 *
 * @author zExNocs
 * @date 2026/09/11
 * @since paperMC-1.0.0alpha
 */
public final class PaperBukkitCommandAdapter implements CommandExecutor, TabCompleter {
    private final JavaPlugin plugin;
    private final IPaperCommandService commandService;
    private final org.zexnocs.teanekocore.command.api.Command coreMetadata;
    private final TeaNekoMCCommand minecraftMetadata;
    private final Map<String, String> coreNamesByBukkitName;
    private final Set<String> subCommandNames;
    private final Map<String, TeaNekoMCSubCommand> minecraftSubCommands;
    private final @Nullable IPaperCommandTabCompleter customTabCompleter;

    /**
     * 创建一个 Bukkit 到 Core 的指令适配器。
     *
     * @param plugin Paper 插件实例
     * @param commandService 通用 Paper 指令服务
     * @param coreMetadata Core 指令元数据
     * @param minecraftMetadata Minecraft 指令元数据
     * @param subCommandNames 子指令名称
     * @param minecraftSubCommands Minecraft 子指令元数据
     * @param customTabCompleter 可选的自定义补全器
     */
    public PaperBukkitCommandAdapter(JavaPlugin plugin,
                                     IPaperCommandService commandService,
                                     org.zexnocs.teanekocore.command.api.Command coreMetadata,
                                     TeaNekoMCCommand minecraftMetadata,
                                     Set<String> subCommandNames,
                                     Map<String, TeaNekoMCSubCommand> minecraftSubCommands,
                                     @Nullable IPaperCommandTabCompleter customTabCompleter) {
        this.plugin = plugin;
        this.commandService = commandService;
        this.coreMetadata = coreMetadata;
        this.minecraftMetadata = minecraftMetadata;
        this.subCommandNames = subCommandNames;
        this.minecraftSubCommands = minecraftSubCommands;
        this.customTabCompleter = customTabCompleter;
        this.coreNamesByBukkitName = buildCommandNameMap(coreMetadata.value());
    }

    /**
     * 将 Bukkit 调用封装为上下文并交给 Core 分发。
     *
     * @param sender 指令发送者
     * @param command Bukkit 指令
     * @param label 实际使用的指令标签
     * @param args 指令参数
     * @return 始终返回 {@code true}
     */
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String @NotNull [] args) {
        PaperCommandContext context = createContext(sender, label, args);
        if (!hasSubCommandPermission(sender, args)) {
            PaperCommandUtils.sendOnMainThread(context, "你没有执行该子指令的权限。");
            return true;
        }
        commandService.dispatch(context);
        return true;
    }

    /**
     * 优先调用指令 Bean 的自定义补全器，否则补全 Core 子指令名称。
     *
     * @param sender 指令发送者
     * @param command Bukkit 指令
     * @param alias 实际使用的指令别名
     * @param args 已输入的参数
     * @return 补全项
     */
    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender,
                                                @NotNull Command command,
                                                @NotNull String alias,
                                                @NotNull String @NotNull [] args) {
        PaperCommandContext context = createContext(sender, alias, args);
        if (customTabCompleter != null) {
            List<String> completions = customTabCompleter.complete(context);
            return completions == null
                    ? Collections.emptyList()
                    : filterUnauthorizedSubCommands(sender, args, completions);
        }
        if (args.length != 1) {
            return Collections.emptyList();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return subCommandNames.stream()
                .filter(name -> hasSubCommandPermission(sender, new String[]{name}))
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    /**
     * 判断当前发送者是否拥有目标子指令覆盖声明的 Bukkit 权限。
     *
     * @param sender 指令发送者
     * @param args 指令参数
     * @return 未覆盖权限或已经获得权限时返回 {@code true}
     */
    private boolean hasSubCommandPermission(CommandSender sender, String[] args) {
        if (args.length == 0) {
            return true;
        }
        TeaNekoMCSubCommand metadata = minecraftSubCommands.get(
                args[0].toLowerCase(Locale.ROOT)
        );
        return metadata == null
                || metadata.permission().isBlank()
                || sender.hasPermission(metadata.permission());
    }

    /**
     * 在补全第一个参数时隐藏发送者无权使用的子指令。
     *
     * @param sender 指令发送者
     * @param args 已输入参数
     * @param completions 原始补全项
     * @return 权限过滤后的补全项
     */
    private List<String> filterUnauthorizedSubCommands(CommandSender sender,
                                                        String[] args,
                                                        List<String> completions) {
        if (args.length != 1) {
            return completions;
        }
        return completions.stream()
                .filter(completion -> hasSubCommandPermission(sender, new String[]{completion}))
                .toList();
    }

    /**
     * 创建一次 Paper 指令上下文。
     *
     * @param sender 指令发送者
     * @param label 实际指令标签
     * @param args 指令参数
     * @return 指令上下文
     */
    private PaperCommandContext createContext(CommandSender sender, String label, String[] args) {
        String normalizedLabel = PaperCommandUtils.normalizeCommandName(label);
        String coreName = coreNamesByBukkitName.getOrDefault(
                normalizedLabel,
                coreMetadata.value()[0]
        );
        return new PaperCommandContext(
                plugin, sender, coreName, normalizedLabel, args, coreMetadata, minecraftMetadata
        );
    }

    /**
     * 建立 Bukkit 小写名称到 Core 原始名称的映射。
     *
     * @param coreNames Core 主名称与别名
     * @return 名称映射
     */
    private Map<String, String> buildCommandNameMap(String[] coreNames) {
        Map<String, String> names = new LinkedHashMap<>();
        for (String coreName : coreNames) {
            names.put(PaperCommandUtils.normalizeCommandName(coreName), coreName);
        }
        return Map.copyOf(names);
    }
}
