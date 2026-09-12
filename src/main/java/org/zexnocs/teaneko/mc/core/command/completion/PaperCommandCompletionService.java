package org.zexnocs.teaneko.mc.core.command.completion;

import org.springframework.stereotype.Service;
import org.zexnocs.teaneko.core.command.CommandArgumentTypeHandlerRegistry;
import org.zexnocs.teaneko.core.command.CommandData;
import org.zexnocs.teaneko.core.command.CommandMapData;
import org.zexnocs.teaneko.core.command.exception.CommandDataTypeMismatchException;
import org.zexnocs.teaneko.core.command.interfaces.ICommandArgumentProcessor;
import org.zexnocs.teaneko.core.logger.ILogger;
import org.zexnocs.teaneko.core.utils.scanner.inerfaces.IBeanScanner;
import org.zexnocs.teaneko.mc.core.command.PaperCommandContext;
import org.zexnocs.teaneko.mc.core.command.PaperCommandConverter;
import org.zexnocs.teaneko.mc.core.command.api.TeaNekoMCSubCommand;
import org.zexnocs.teaneko.mc.core.command.completion.api.CommandComplete;
import org.zexnocs.teaneko.mc.core.command.completion.interfaces.IPaperCommandArgumentTypeHandler;
import org.zexnocs.teaneko.mc.core.command.completion.interfaces.IPaperCommandCompletionProvider;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 根据 Core 指令映射和参数注解生成 Paper 自动补全候选。
 * <p>
 * 本服务在指令注册阶段编译并校验补全元数据，运行时只执行参数定位、权限检查和候选合并。
 *
 * @author zExNocs
 * @date 2026/09/12
 * @since paperMC-1.0.0alpha
 * @see CommandComplete
 */
@Service
public final class PaperCommandCompletionService {
    private final IBeanScanner beanScanner;
    private final ICommandArgumentProcessor argumentProcessor;
    private final CommandArgumentTypeHandlerRegistry typeHandlerRegistry;
    private final PaperCommandConverter commandConverter;
    private final ILogger logger;
    private final Map<CommandMapData, CommandCompletionDefinition> definitions =
            new ConcurrentHashMap<>();
    private volatile Map<Class<?>, IPaperCommandCompletionProvider> providersByClass = Map.of();
    private volatile boolean providersLoaded;

    /**
     * 创建 Paper 指令自动补全服务。
     *
     * @param beanScanner Spring Bean 扫描器
     * @param argumentProcessor Core 参数处理器
     * @param typeHandlerRegistry 扩展参数类型注册表
     * @param commandConverter Paper 指令数据转换器
     * @param logger 日志接口
     */
    public PaperCommandCompletionService(IBeanScanner beanScanner,
                                         ICommandArgumentProcessor argumentProcessor,
                                         CommandArgumentTypeHandlerRegistry typeHandlerRegistry,
                                         PaperCommandConverter commandConverter,
                                         ILogger logger) {
        this.beanScanner = beanScanner;
        this.argumentProcessor = argumentProcessor;
        this.typeHandlerRegistry = typeHandlerRegistry;
        this.commandConverter = commandConverter;
        this.logger = logger;
    }

    /**
     * 编译并校验一个 Core 指令的补全定义。
     *
     * @param commandMapData Core 指令映射
     * @param minecraftSubCommands 子指令的 Bukkit 权限声明
     * @throws IllegalStateException 注解引用的补全提供器未注册时抛出
     */
    public void prepareCommand(CommandMapData commandMapData,
                               Map<String, TeaNekoMCSubCommand> minecraftSubCommands) {
        ensureProvidersLoaded();
        definitions.put(
                commandMapData,
                buildDefinition(commandMapData, minecraftSubCommands)
        );
    }

    /**
     * 为当前 Paper Tab 回调生成补全项。
     *
     * @param context Paper 指令上下文
     * @param commandMapData 当前 Core 指令映射
     * @return 已过滤并排序的补全项
     */
    public List<String> complete(PaperCommandContext context, CommandMapData commandMapData) {
        CommandCompletionDefinition definition = definitions.get(commandMapData);
        if (definition == null || !hasRootPermission(context)) {
            return List.of();
        }

        String[] args = context.args();
        String currentInput = args.length == 0 ? "" : args[args.length - 1];
        String[] committed = args.length == 0
                ? new String[0]
                : Arrays.copyOf(args, args.length - 1);
        Collection<String> candidates = new ArrayList<>();

        if (committed.length == 0) {
            definition.subCommands().values().stream()
                    .filter(subCommand -> hasSubCommandPermission(context, subCommand))
                    .map(SubCommandCompletionDefinition::displayName)
                    .forEach(candidates::add);
            addMethodCandidates(
                    candidates,
                    context,
                    definition,
                    definition.defaultMethod(),
                    "",
                    new String[0],
                    currentInput
            );
            return normalizeCandidates(candidates, currentInput);
        }

        String firstArgument = committed[0].toLowerCase(Locale.ROOT);
        SubCommandCompletionDefinition subCommand = definition.subCommands().get(firstArgument);
        if (subCommand != null) {
            if (!hasSubCommandPermission(context, subCommand)) {
                return List.of();
            }
            addMethodCandidates(
                    candidates,
                    context,
                    definition,
                    subCommand.method(),
                    subCommand.displayName(),
                    Arrays.copyOfRange(committed, 1, committed.length),
                    currentInput
            );
        } else {
            addMethodCandidates(
                    candidates,
                    context,
                    definition,
                    definition.defaultMethod(),
                    "",
                    committed,
                    currentInput
            );
        }
        return normalizeCandidates(candidates, currentInput);
    }

    /**
     * 清理指令定义缓存，供插件关闭或注册回滚时释放引用。
     */
    public void clear() {
        definitions.clear();
        providersByClass = Map.of();
        providersLoaded = false;
    }

    /**
     * 收集一个指令方法当前可达参数的候选值。
     */
    private void addMethodCandidates(Collection<String> candidates,
                                     PaperCommandContext context,
                                     CommandCompletionDefinition definition,
                                     Method method,
                                     String subCommand,
                                     String[] committedArguments,
                                     String currentInput) {
        if (method == null) {
            return;
        }

        CommandData<PaperCommandContext> commandData = commandConverter.__parse(context);
        List<Parameter> parameters;
        try {
            parameters = argumentProcessor.findCompletionParameters(
                    method,
                    committedArguments,
                    commandData
            );
        } catch (CommandDataTypeMismatchException exception) {
            return;
        }

        Parameter[] declaredParameters = method.getParameters();
        for (Parameter parameter : parameters) {
            ParameterCompletionDefinition parameterDefinition =
                    definition.parameterDefinitions().get(parameter);
            if (parameterDefinition == null) {
                continue;
            }
            int parameterIndex = findParameterIndex(declaredParameters, parameter);
            PaperCommandCompletionContext completionContext =
                    new PaperCommandCompletionContext(
                            context,
                            definition.commandBean(),
                            method,
                            parameter,
                            parameterIndex,
                            subCommand,
                            currentInput,
                            Arrays.asList(committedArguments)
                    );
            candidates.addAll(parameterDefinition.staticValues());
            for (IPaperCommandCompletionProvider provider : parameterDefinition.providers()) {
                addProviderCandidates(candidates, provider, completionContext);
            }
            addBuiltInTypeCandidates(candidates, parameterDefinition);
        }
    }

    /**
     * 调用单个提供器，并隔离其异常与非法返回值。
     */
    private void addProviderCandidates(Collection<String> candidates,
                                       IPaperCommandCompletionProvider provider,
                                       PaperCommandCompletionContext context) {
        try {
            Collection<String> provided = provider.complete(context);
            if (provided != null) {
                candidates.addAll(provided);
            }
        } catch (RuntimeException exception) {
            logger.warn(
                    getClass().getName(),
                    "参数补全提供器执行失败：" + provider.getClass().getName()
                            + "，方法：" + context.method().toGenericString(),
                    exception
            );
        }
    }

    /**
     * 添加枚举和布尔类型无需额外 Bean 的内置候选。
     */
    private void addBuiltInTypeCandidates(Collection<String> candidates,
                                          ParameterCompletionDefinition definition) {
        if (!definition.useTypeProvider()) {
            return;
        }
        Class<?> targetType = definition.targetType();
        if (targetType == boolean.class || targetType == Boolean.class) {
            candidates.add("true");
            candidates.add("false");
            return;
        }
        if (targetType.isEnum()) {
            for (Object constant : targetType.getEnumConstants()) {
                candidates.add(((Enum<?>) constant).name());
            }
        }
    }

    /**
     * 构造一个指令的不可变补全定义。
     */
    private CommandCompletionDefinition buildDefinition(
            CommandMapData commandMapData,
            Map<String, TeaNekoMCSubCommand> minecraftSubCommands) {
        Map<String, SubCommandCompletionDefinition> subCommands = new LinkedHashMap<>();
        Set<Method> methods = new LinkedHashSet<>();
        if (commandMapData.getDefaultCommandMethod() != null) {
            methods.add(commandMapData.getDefaultCommandMethod());
        }
        commandMapData.getSubCommands().forEach((name, pair) -> {
            String normalizedName = name.toLowerCase(Locale.ROOT);
            TeaNekoMCSubCommand permission = minecraftSubCommands.get(normalizedName);
            SubCommandCompletionDefinition definition = new SubCommandCompletionDefinition(
                    name,
                    pair.getSecond(),
                    permission
            );
            SubCommandCompletionDefinition previous = subCommands.putIfAbsent(
                    normalizedName,
                    definition
            );
            if (previous != null && !previous.method().equals(definition.method())) {
                throw new IllegalStateException("补全系统发现重复的子指令名称：" + name);
            }
            methods.add(pair.getSecond());
        });

        Map<Parameter, ParameterCompletionDefinition> parameters = new LinkedHashMap<>();
        for (Method method : methods) {
            for (Parameter parameter : method.getParameters()) {
                parameters.put(parameter, buildParameterDefinition(parameter));
            }
        }
        return new CommandCompletionDefinition(
                commandMapData.getCommand(),
                commandMapData.getDefaultCommandMethod(),
                Map.copyOf(subCommands),
                Map.copyOf(parameters)
        );
    }

    /**
     * 合并一个参数声明的固定、显式和类型补全来源。
     */
    private ParameterCompletionDefinition buildParameterDefinition(Parameter parameter) {
        CommandComplete metadata = parameter.getAnnotation(CommandComplete.class);
        List<String> staticValues = metadata == null
                ? List.of()
                : List.of(metadata.value());
        List<IPaperCommandCompletionProvider> providers = new ArrayList<>();
        if (metadata != null) {
            for (Class<? extends IPaperCommandCompletionProvider> providerType
                    : metadata.providers()) {
                providers.add(resolveProvider(providerType));
            }
        }

        Class<?> targetType = getParameterValueType(parameter);
        boolean useTypeProvider = metadata == null || metadata.useTypeProvider();
        if (useTypeProvider) {
            typeHandlerRegistry.findHandler(targetType)
                    .filter(IPaperCommandArgumentTypeHandler.class::isInstance)
                    .map(IPaperCommandArgumentTypeHandler.class::cast)
                    .ifPresent(providers::add);
        }
        return new ParameterCompletionDefinition(
                staticValues,
                List.copyOf(new LinkedHashSet<>(providers)),
                targetType,
                useTypeProvider
        );
    }

    /**
     * 获取 List 元素类型或普通参数类型。
     */
    private Class<?> getParameterValueType(Parameter parameter) {
        if (!List.class.isAssignableFrom(parameter.getType())) {
            return parameter.getType();
        }
        Type genericType = parameter.getParameterizedType();
        if (genericType instanceof ParameterizedType parameterizedType
                && parameterizedType.getActualTypeArguments()[0] instanceof Class<?> elementType) {
            return elementType;
        }
        return String.class;
    }

    /**
     * 解析注解中引用的 Spring 提供器。
     */
    private IPaperCommandCompletionProvider resolveProvider(
            Class<? extends IPaperCommandCompletionProvider> providerType) {
        IPaperCommandCompletionProvider provider = providersByClass.get(providerType);
        if (provider == null) {
            throw new IllegalStateException("@CommandComplete 引用的补全提供器不是 Spring Bean："
                    + providerType.getName());
        }
        return provider;
    }

    /**
     * 首次编译指令时扫描全部补全提供器 Bean。
     */
    private synchronized void ensureProvidersLoaded() {
        if (providersLoaded) {
            return;
        }
        Map<Class<?>, IPaperCommandCompletionProvider> providers = new LinkedHashMap<>();
        for (IPaperCommandCompletionProvider provider
                : beanScanner.getBeansOfType(IPaperCommandCompletionProvider.class).values()) {
            Class<?> providerClass = beanScanner.getBeanClass(provider);
            IPaperCommandCompletionProvider previous = providers.putIfAbsent(
                    providerClass,
                    provider
            );
            if (previous != null && previous != provider) {
                throw new IllegalStateException("补全提供器类型注册了多个 Spring Bean："
                        + providerClass.getName());
            }
        }
        providersByClass = Map.copyOf(providers);
        providersLoaded = true;
    }

    /**
     * 判断发送者是否拥有根指令 Bukkit 权限。
     */
    private boolean hasRootPermission(PaperCommandContext context) {
        String permission = context.minecraftMetadata().permission();
        return permission.isBlank() || context.sender().hasPermission(permission);
    }

    /**
     * 判断发送者是否拥有子指令覆盖权限。
     */
    private boolean hasSubCommandPermission(PaperCommandContext context,
                                            SubCommandCompletionDefinition subCommand) {
        TeaNekoMCSubCommand metadata = subCommand.minecraftMetadata();
        return metadata == null
                || metadata.permission().isBlank()
                || context.sender().hasPermission(metadata.permission());
    }

    /**
     * 根据输入前缀统一清理、去重并排序候选值。
     */
    private List<String> normalizeCandidates(Collection<String> candidates, String input) {
        String prefix = input.toLowerCase(Locale.ROOT);
        Map<String, String> normalized = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (String candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            String value = candidate.trim();
            if (!value.isEmpty() && value.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                normalized.putIfAbsent(value, value);
            }
        }
        return List.copyOf(normalized.values());
    }

    /**
     * 查找反射参数的声明索引。
     */
    private int findParameterIndex(Parameter[] parameters, Parameter target) {
        for (int index = 0; index < parameters.length; index++) {
            if (parameters[index].equals(target)) {
                return index;
            }
        }
        return -1;
    }

    /**
     * 保存一个指令已经编译完成的补全结构。
     *
     * @param commandBean 指令 Spring Bean
     * @param defaultMethod 默认指令方法
     * @param subCommands 小写子指令名称到定义的映射
     * @param parameterDefinitions 参数补全定义
     * @author zExNocs
     * @date 2026/09/12
     * @since paperMC-1.0.0alpha
     */
    private record CommandCompletionDefinition(
            Object commandBean,
            Method defaultMethod,
            Map<String, SubCommandCompletionDefinition> subCommands,
            Map<Parameter, ParameterCompletionDefinition> parameterDefinitions) {
    }

    /**
     * 保存子指令方法、显示名称和权限声明。
     *
     * @param displayName 子指令显示名称
     * @param method 子指令方法
     * @param minecraftMetadata Bukkit 权限声明
     * @author zExNocs
     * @date 2026/09/12
     * @since paperMC-1.0.0alpha
     */
    private record SubCommandCompletionDefinition(
            String displayName,
            Method method,
            TeaNekoMCSubCommand minecraftMetadata) {
    }

    /**
     * 保存单个参数已经解析的补全来源。
     *
     * @param staticValues 固定候选值
     * @param providers 动态候选提供器
     * @param targetType 实际补全值类型
     * @param useTypeProvider 是否启用类型默认候选
     * @author zExNocs
     * @date 2026/09/12
     * @since paperMC-1.0.0alpha
     */
    private record ParameterCompletionDefinition(
            List<String> staticValues,
            List<IPaperCommandCompletionProvider> providers,
            Class<?> targetType,
            boolean useTypeProvider) {
    }
}
