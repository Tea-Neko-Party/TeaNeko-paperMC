package org.zexnocs.teaneko.mc.core.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.zexnocs.teaneko.core.command.CommandMapData;
import org.zexnocs.teaneko.mc.core.command.api.TeaNekoMCCommand;
import org.zexnocs.teaneko.mc.core.command.api.TeaNekoMCSubCommand;
import org.zexnocs.teaneko.mc.core.command.completion.PaperCommandCompletionService;
import org.zexnocs.teaneko.mc.core.command.interfaces.IPaperCommandService;
import org.zexnocs.teaneko.mc.utils.PaperCommandUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 将 Bukkit 的指令执行与补全回调适配到通用 Paper 指令服务。
 *
 * @author zExNocs
 * @date 2026/09/12
 * @since paperMC-1.0.0alpha
 */
public final class PaperBukkitCommandAdapter implements CommandExecutor, TabCompleter {
    private final JavaPlugin plugin;
    private final IPaperCommandService commandService;
    private final org.zexnocs.teaneko.core.command.api.Command coreMetadata;
    private final TeaNekoMCCommand minecraftMetadata;
    private final Map<String, String> coreNamesByBukkitName;
    private final Map<String, TeaNekoMCSubCommand> minecraftSubCommands;
    private final PaperCommandCompletionService completionService;
    private final CommandMapData commandMapData;

    /**
     * 创建一个 Bukkit 到 Core 的指令适配器。
     *
     * @param plugin Paper 插件实例
     * @param commandService 通用 Paper 指令服务
     * @param coreMetadata Core 指令元数据
     * @param minecraftMetadata Minecraft 指令元数据
     * @param minecraftSubCommands Minecraft 子指令元数据
     * @param completionService 自动补全服务
     * @param commandMapData Core 指令映射
     */
    public PaperBukkitCommandAdapter(JavaPlugin plugin,
                                     IPaperCommandService commandService,
                                     org.zexnocs.teaneko.core.command.api.Command coreMetadata,
                                     TeaNekoMCCommand minecraftMetadata,
                                     Map<String, TeaNekoMCSubCommand> minecraftSubCommands,
                                     PaperCommandCompletionService completionService,
                                     CommandMapData commandMapData) {
        this.plugin = plugin;
        this.commandService = commandService;
        this.coreMetadata = coreMetadata;
        this.minecraftMetadata = minecraftMetadata;
        this.minecraftSubCommands = minecraftSubCommands;
        this.completionService = completionService;
        this.commandMapData = commandMapData;
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
     * 将 Tab 回调交给统一的注解与类型补全服务。
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
        return completionService.complete(context, commandMapData);
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
