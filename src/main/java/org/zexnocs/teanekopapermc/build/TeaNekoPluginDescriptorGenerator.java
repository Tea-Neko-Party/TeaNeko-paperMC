package org.zexnocs.teanekopapermc.build;

import org.zexnocs.teanekoapp.TeaNekoAppApplication;
import org.zexnocs.teanekocore.command.api.Command;
import org.zexnocs.teanekocore.command.api.SubCommand;
import org.zexnocs.teanekopapermc.core.command.api.TeaNekoMCCommand;
import org.zexnocs.teanekopapermc.core.command.api.TeaNekoMCSubCommand;
import org.zexnocs.teanekopapermc.utils.PaperCommandUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * 在构建期扫描已编译的 Minecraft 指令类并生成 Paper plugin.yml。
 *
 * @author zExNocs
 * @date 2026/09/12
 * @since paperMC-1.0.0alpha
 * @see TeaNekoMCCommand
 */
public final class TeaNekoPluginDescriptorGenerator {
    private static final String PLUGIN_NAME = "TeaNekoPaper";
    private static final String PLUGIN_MAIN_CLASS = "org.zexnocs.teanekopapermc.TeaNekoPaperPlugin";
    private static final String PAPER_API_VERSION = "26.2";

    private TeaNekoPluginDescriptorGenerator() {
    }

    /**
     * 扫描参数指定的类目录并写入 plugin.yml。
     *
     * @param args 第一个参数为输出文件，第二个参数为插件版本，其余参数为类目录
     * @throws IOException 扫描类目录或写入文件失败时抛出
     * @throws ClassNotFoundException 扫描到的类无法加载时抛出
     */
    public static void main(String[] args) throws IOException, ClassNotFoundException {
        if (args.length < 3) {
            throw new IllegalArgumentException("生成 plugin.yml 至少需要输出路径、插件版本和一个类目录。");
        }

        Path outputFile = Path.of(args[0]);
        String version = args[1];
        List<Class<?>> classes = new ArrayList<>();
        for (int index = 2; index < args.length; index++) {
            classes.addAll(scanClasses(Path.of(args[index])));
        }

        List<CommandDefinition> commands = classes.stream()
                .map(TeaNekoPluginDescriptorGenerator::toCommandDefinition)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(CommandDefinition::primaryName))
                .toList();
        validateUniqueNames(commands);

        Files.createDirectories(outputFile.getParent());
        Files.writeString(outputFile, buildYaml(version, commands), StandardCharsets.UTF_8);
        System.out.println("已自动生成 plugin.yml，共包含 " + commands.size() + " 个 Minecraft 指令。");
    }

    /**
     * 扫描一个编译输出目录中的 TeaNeko Paper 类。
     *
     * @param classesDirectory 编译类目录
     * @return 成功加载的类
     * @throws IOException 遍历目录失败时抛出
     * @throws ClassNotFoundException 类无法加载时抛出
     */
    private static List<Class<?>> scanClasses(Path classesDirectory)
            throws IOException, ClassNotFoundException {
        if (!Files.isDirectory(classesDirectory)) {
            return List.of();
        }

        List<String> classNames;
        try (var paths = Files.walk(classesDirectory)) {
            classNames = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".class"))
                    .map(path -> toClassName(classesDirectory, path))
                    // 与 Spring 使用同一根包，避免构建期遗漏其他功能包中的 Minecraft 指令。
                    .filter(name -> name.startsWith(TeaNekoAppApplication.ROOT_SCAN_PACKAGE + "."))
                    .filter(name -> !name.contains("$"))
                    .sorted()
                    .toList();
        }

        List<Class<?>> classes = new ArrayList<>(classNames.size());
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        for (String className : classNames) {
            classes.add(Class.forName(className, false, classLoader));
        }
        return classes;
    }

    /**
     * 将类文件路径转换为完整类名。
     *
     * @param classesDirectory 编译类根目录
     * @param classFile 类文件
     * @return 完整类名
     */
    private static String toClassName(Path classesDirectory, Path classFile) {
        String relativeName = classesDirectory.relativize(classFile).toString();
        return relativeName.substring(0, relativeName.length() - ".class".length())
                .replace('/', '.')
                .replace('\\', '.');
    }

    /**
     * 将同时带有两个指令注解的类转换为描述文件定义。
     *
     * @param commandClass 待检查类
     * @return 指令定义；不是 Minecraft 指令时返回 {@code null}
     */
    private static CommandDefinition toCommandDefinition(Class<?> commandClass) {
        Command coreMetadata = commandClass.getAnnotation(Command.class);
        TeaNekoMCCommand minecraftMetadata = commandClass.getAnnotation(TeaNekoMCCommand.class);
        if (coreMetadata == null || minecraftMetadata == null) {
            return null;
        }
        if (coreMetadata.mode() != Command.CommandMode.PREFIX) {
            throw new IllegalStateException("@TeaNekoMCCommand 不支持正则指令：" + commandClass.getName());
        }
        if (coreMetadata.value().length == 0) {
            throw new IllegalStateException("Minecraft 指令缺少主名称：" + commandClass.getName());
        }

        String primaryName = normalizeAndValidateName(coreMetadata.value()[0], commandClass);
        List<String> aliases = new ArrayList<>();
        for (int index = 1; index < coreMetadata.value().length; index++) {
            aliases.add(normalizeAndValidateName(coreMetadata.value()[index], commandClass));
        }
        validateMinecraftSubCommands(commandClass);
        return new CommandDefinition(
                primaryName,
                List.copyOf(aliases),
                minecraftMetadata,
                commandClass
        );
    }

    /**
     * 验证 Minecraft 子指令注解只声明在 Core 子指令方法上。
     *
     * @param commandClass 顶级指令类
     */
    private static void validateMinecraftSubCommands(Class<?> commandClass) {
        for (var method : commandClass.getDeclaredMethods()) {
            if (method.isAnnotationPresent(TeaNekoMCSubCommand.class)
                    && !method.isAnnotationPresent(SubCommand.class)) {
                throw new IllegalStateException("@TeaNekoMCSubCommand 必须与 @SubCommand 同时使用："
                        + commandClass.getName() + "#" + method.getName());
            }
        }
    }

    /**
     * 规范化并验证 Bukkit 指令名称。
     *
     * @param originalName Core 原始名称
     * @param commandClass 指令类
     * @return Bukkit 指令名称
     */
    private static String normalizeAndValidateName(String originalName, Class<?> commandClass) {
        String name = PaperCommandUtils.normalizeCommandName(originalName);
        if (name.isBlank() || name.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalStateException("Minecraft 指令名称无效：" + commandClass.getName());
        }
        return name;
    }

    /**
     * 验证主名称与别名在全部指令中没有冲突。
     *
     * @param commands 指令定义
     */
    private static void validateUniqueNames(List<CommandDefinition> commands) {
        Map<String, String> ownerByName = new LinkedHashMap<>();
        for (CommandDefinition command : commands) {
            validateUniqueName(ownerByName, command.primaryName(), command.primaryName());
            for (String alias : command.aliases()) {
                validateUniqueName(ownerByName, alias, command.primaryName());
            }
        }
    }

    /**
     * 验证一个名称尚未被其他主指令占用。
     *
     * @param ownerByName 名称所有者映射
     * @param name 待验证名称
     * @param owner 当前主指令
     */
    private static void validateUniqueName(Map<String, String> ownerByName, String name, String owner) {
        String existingOwner = ownerByName.putIfAbsent(name, owner);
        if (existingOwner != null && !existingOwner.equals(owner)) {
            throw new IllegalStateException("Minecraft 指令名称冲突：" + name
                    + " 同时属于 " + existingOwner + " 和 " + owner);
        }
    }

    /**
     * 构造完整的 Paper 插件描述文件内容。
     *
     * @param version 插件版本
     * @param commands 指令定义
     * @return YAML 文本
     */
    private static String buildYaml(String version, List<CommandDefinition> commands) {
        StringBuilder yaml = new StringBuilder();
        yaml.append("name: ").append(quote(PLUGIN_NAME)).append('\n');
        yaml.append("version: ").append(quote(version)).append('\n');
        yaml.append("main: ").append(quote(PLUGIN_MAIN_CLASS)).append('\n');
        yaml.append("description: ").append(quote("面向 Paper 服务端的 TeaNeko Core 工具库。"))
                .append('\n');
        yaml.append("api-version: ").append(quote(PAPER_API_VERSION)).append('\n');
        yaml.append("author: ").append(quote("zExNocs")).append('\n');

        if (!commands.isEmpty()) {
            yaml.append("commands:\n");
            for (CommandDefinition command : commands) {
                appendCommand(yaml, command);
            }
        }
        appendPermissions(yaml, commands);
        return yaml.toString();
    }

    /**
     * 向 YAML 中追加一个指令声明。
     *
     * @param yaml YAML 构造器
     * @param command 指令定义
     */
    private static void appendCommand(StringBuilder yaml, CommandDefinition command) {
        TeaNekoMCCommand metadata = command.metadata();
        yaml.append("  ").append(command.primaryName()).append(":\n");
        if (!metadata.description().isBlank()) {
            yaml.append("    description: ").append(quote(metadata.description())).append('\n');
        }
        String usage = metadata.usage().isBlank()
                ? "/" + command.primaryName()
                : metadata.usage();
        yaml.append("    usage: ").append(quote(usage)).append('\n');
        if (!command.aliases().isEmpty()) {
            yaml.append("    aliases:\n");
            for (String alias : command.aliases()) {
                yaml.append("      - ").append(quote(alias)).append('\n');
            }
        }
        if (!metadata.permission().isBlank()) {
            yaml.append("    permission: ").append(quote(metadata.permission())).append('\n');
        }
    }

    /**
     * 汇总并追加指令使用的 Bukkit 权限节点。
     *
     * @param yaml YAML 构造器
     * @param commands 指令定义
     */
    private static void appendPermissions(StringBuilder yaml, List<CommandDefinition> commands) {
        Map<String, PermissionDefinition> permissions = new LinkedHashMap<>();
        for (CommandDefinition command : commands) {
            TeaNekoMCCommand metadata = command.metadata();
            if (!metadata.permission().isBlank()) {
                collectPermission(
                        permissions,
                        metadata.permission(),
                        metadata.permissionDescription(),
                        metadata.permissionDefault()
                );
            }
            for (var method : command.commandClass().getDeclaredMethods()) {
                TeaNekoMCSubCommand subCommand = method.getAnnotation(TeaNekoMCSubCommand.class);
                if (subCommand != null && !subCommand.permission().isBlank()) {
                    collectPermission(
                            permissions,
                            subCommand.permission(),
                            subCommand.permissionDescription(),
                            subCommand.permissionDefault()
                    );
                }
            }
        }
        if (permissions.isEmpty()) {
            return;
        }

        yaml.append("permissions:\n");
        for (var entry : permissions.entrySet()) {
            PermissionDefinition metadata = entry.getValue();
            yaml.append("  ").append(entry.getKey()).append(":\n");
            if (!metadata.description().isBlank()) {
                yaml.append("    description: ")
                        .append(quote(metadata.description()))
                        .append('\n');
            }
            yaml.append("    default: ")
                    .append(quote(metadata.permissionDefault().getYamlValue()))
                    .append('\n');
        }
    }

    /**
     * 收集一个 Bukkit 权限节点，并校验重复声明的默认策略一致。
     *
     * @param permissions 权限定义映射
     * @param permission 权限节点
     * @param description 权限说明
     * @param permissionDefault 默认授权策略
     */
    private static void collectPermission(Map<String, PermissionDefinition> permissions,
                                          String permission,
                                          String description,
                                          TeaNekoMCCommand.PermissionDefault permissionDefault) {
        PermissionDefinition definition = new PermissionDefinition(description, permissionDefault);
        PermissionDefinition existing = permissions.putIfAbsent(permission, definition);
        if (existing != null && existing.permissionDefault() != permissionDefault) {
            throw new IllegalStateException("权限节点默认策略冲突：" + permission);
        }
    }

    /**
     * 使用单引号安全编码 YAML 标量。
     *
     * @param value 原始文本
     * @return YAML 标量
     */
    private static String quote(String value) {
        return "'" + value.replace("'", "''") + "'";
    }

    /**
     * 保存一个待写入 plugin.yml 的指令定义。
     *
     * @param primaryName Bukkit 主指令名称
     * @param aliases Bukkit 指令别名
     * @param metadata Minecraft 指令元数据
     * @param commandClass 顶级指令类
     * @author zExNocs
     * @date 2026/09/11
     * @since paperMC-1.0.0alpha
     */
    private record CommandDefinition(String primaryName,
                                     List<String> aliases,
                                     TeaNekoMCCommand metadata,
                                     Class<?> commandClass) {
    }

    /**
     * 保存一个待写入 plugin.yml 的 Bukkit 权限定义。
     *
     * @param description 权限说明
     * @param permissionDefault 默认授权策略
     * @author zExNocs
     * @date 2026/09/11
     * @since paperMC-1.0.0alpha
     */
    private record PermissionDefinition(
            String description,
            TeaNekoMCCommand.PermissionDefault permissionDefault) {
    }
}
