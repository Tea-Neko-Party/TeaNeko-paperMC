package org.zexnocs.teanekopapermc.core.command;

import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.zexnocs.teanekocore.command.api.Command;
import org.zexnocs.teanekopapermc.core.command.api.TeaNekoMCCommand;

import java.util.Objects;

/**
 * 保存一次 Paper 指令调用的原始上下文。
 *
 * @param plugin 指令所属插件
 * @param sender 指令发送者
 * @param coreCommandName Core 扫描器中的原始指令名称
 * @param label 玩家实际使用的指令标签
 * @param args 原始参数
 * @param coreMetadata Core 指令元数据
 * @param minecraftMetadata Minecraft 指令元数据
 * @author zExNocs
 * @date 2026/09/10
 * @since paperMC-1.0.0alpha
 */
public record PaperCommandContext(JavaPlugin plugin,
                                  CommandSender sender,
                                  String coreCommandName,
                                  String label,
                                  String[] args,
                                  Command coreMetadata,
                                  TeaNekoMCCommand minecraftMetadata) {
    /**
     * 验证上下文字段并复制参数，防止异步执行期间被外部修改。
     */
    public PaperCommandContext {
        Objects.requireNonNull(plugin, "指令所属插件不能为空。");
        Objects.requireNonNull(sender, "指令发送者不能为空。");
        Objects.requireNonNull(coreCommandName, "Core 指令名称不能为空。");
        Objects.requireNonNull(label, "指令标签不能为空。");
        args = Objects.requireNonNull(args, "指令参数不能为空。").clone();
        Objects.requireNonNull(coreMetadata, "Core 指令元数据不能为空。");
        Objects.requireNonNull(minecraftMetadata, "Minecraft 指令元数据不能为空。");
    }

    /**
     * 返回参数副本，避免调用方修改内部数组。
     *
     * @return 原始参数副本
     */
    @Override
    public String[] args() {
        return args.clone();
    }
}
