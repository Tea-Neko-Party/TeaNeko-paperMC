package org.zexnocs.teaneko.core.command;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.zexnocs.teaneko.core.command.api.DefaultValue;
import org.zexnocs.teaneko.core.command.exception.CommandDataTypeMismatchException;
import org.zexnocs.teaneko.core.command.interfaces.ICommandArgumentProcessor;
import org.zexnocs.teaneko.core.logger.ILogger;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;

/**
 * 负责匹配指令方法参数、转换基础或扩展类型，并为补全系统定位当前参数。
 *
 * @author zExNocs
 * @date 2026/09/12
 * @since paperMC-1.0.0alpha
 */
@Service("commandArgumentProcessor")
public class CommandArgumentProcessor implements ICommandArgumentProcessor {
    /// 日志
    private final ILogger logger;
    /** 扩展参数类型处理器注册表。 */
    private final CommandArgumentTypeHandlerRegistry typeHandlerRegistry;

    @Autowired
    public CommandArgumentProcessor(ILogger logger,
                                    CommandArgumentTypeHandlerRegistry typeHandlerRegistry) {
        this.logger = logger;
        this.typeHandlerRegistry = typeHandlerRegistry;
    }

    /**
     * 处理指令参数。
     * @param method 指令方法
     * @param args 指令参数
     * @param commandData 指令数据。如果有参数是 CommandData 的话，可以直接使用
     * @return 处理后的参数
     */
    @Override
    public Object[] process(Method method, String[] args, CommandData<?> commandData) throws CommandDataTypeMismatchException {
        Parameter[] parameters = method.getParameters();
        if (parameters.length == 0) {
            // 无参数，直接返回
            return new Object[0];
        }
        // 检查 method 是否符合 commandData 参数的要求，如果不符合，直接抛出异常
        for (Parameter parameter : parameters) {
            if (isCommandData(parameter.getType())) {
                validateCommandDataType(parameter, commandData);
            }
        }
        // 构建结果数组
        Object[] result = new Object[parameters.length];
        List<String> argList = args == null ? Collections.emptyList() : Arrays.asList(args);
        // 根据参数构建非 List 参数的剩余数量数组
        int[] nonListCount = buildNonListCount(parameters);
        boolean success = matchParameter(
                parameters,
                0,
                argList,
                0,
                result,
                commandData,
                method,
                nonListCount
        );
        return success ? result : null;
    }

    /**
     * 根据光标前已经提交的参数查找所有可接收当前输入的 Java 参数。
     * <p>
     * 可选参数与列表参数可能产生多条合法路径，因此返回值可能包含多个参数。
     *
     * @param method 指令方法
     * @param committedArgs 光标前已经提交的参数
     * @param commandData 当前指令数据
     * @return 按方法声明顺序排列的候选参数
     * @throws CommandDataTypeMismatchException CommandData 泛型不兼容时抛出
     */
    @Override
    public List<Parameter> findCompletionParameters(Method method,
                                                    String[] committedArgs,
                                                    CommandData<?> commandData)
            throws CommandDataTypeMismatchException {
        Parameter[] parameters = method.getParameters();
        for (Parameter parameter : parameters) {
            if (isCommandData(parameter.getType())) {
                validateCommandDataType(parameter, commandData);
            }
        }

        List<String> args = committedArgs == null
                ? List.of()
                : Arrays.asList(committedArgs);
        Set<Parameter> result = new LinkedHashSet<>();
        collectCompletionParameters(
                parameters,
                0,
                args,
                0,
                commandData,
                result,
                new HashSet<>()
        );
        return List.copyOf(result);
    }

    /**
     * 遍历参数匹配状态并收集当前光标可以到达的参数。
     */
    private void collectCompletionParameters(Parameter[] parameters,
                                             int parameterIndex,
                                             List<String> args,
                                             int argumentIndex,
                                             CommandData<?> commandData,
                                             Set<Parameter> result,
                                             Set<ParameterCursor> visited) {
        ParameterCursor cursor = new ParameterCursor(parameterIndex, argumentIndex);
        if (!visited.add(cursor) || parameterIndex >= parameters.length) {
            return;
        }

        Parameter parameter = parameters[parameterIndex];
        Class<?> parameterType = parameter.getType();
        if (isCommandData(parameterType)) {
            collectCompletionParameters(
                    parameters,
                    parameterIndex + 1,
                    args,
                    argumentIndex,
                    commandData,
                    result,
                    visited
            );
            return;
        }

        if (argumentIndex >= args.size()) {
            result.add(parameter);
            if (isList(parameterType) || parameter.isAnnotationPresent(DefaultValue.class)) {
                collectCompletionParameters(
                        parameters,
                        parameterIndex + 1,
                        args,
                        argumentIndex,
                        commandData,
                        result,
                        visited
                );
            }
            return;
        }

        if (isList(parameterType)) {
            Class<?> elementType = getListElementType(parameter);
            int convertedCount = 0;
            while (argumentIndex + convertedCount < args.size()
                    && convertValue(
                            args.get(argumentIndex + convertedCount),
                            elementType,
                            commandData
                    ) != null) {
                convertedCount++;
            }
            if (convertedCount == args.size() - argumentIndex) {
                // 已提交的剩余参数全部属于当前列表时，光标可以继续补全下一个列表元素。
                result.add(parameter);
            }
            for (int size = 1; size <= convertedCount; size++) {
                collectCompletionParameters(
                        parameters,
                        parameterIndex + 1,
                        args,
                        argumentIndex + size,
                        commandData,
                        result,
                        visited
                );
            }
            // List 参数允许为空，因此当前已提交参数也可能属于其后的参数。
            collectCompletionParameters(
                    parameters,
                    parameterIndex + 1,
                    args,
                    argumentIndex,
                    commandData,
                    result,
                    visited
            );
            return;
        }

        if (convertValue(args.get(argumentIndex), parameterType, commandData) != null) {
            collectCompletionParameters(
                    parameters,
                    parameterIndex + 1,
                    args,
                    argumentIndex + 1,
                    commandData,
                    result,
                    visited
            );
        }
        if (parameter.isAnnotationPresent(DefaultValue.class)) {
            collectCompletionParameters(
                    parameters,
                    parameterIndex + 1,
                    args,
                    argumentIndex,
                    commandData,
                    result,
                    visited
            );
        }
    }


    /**
     * 构建非 List 参数的剩余数量数组。
     *
     * @param parameters 方法参数数组
     * @return {@link int[] }
     */
    private int[] buildNonListCount(Parameter[] parameters) {
        int[] result = new int[parameters.length];
        for (int i = parameters.length - 2; i >= 0; i--) {
            Class<?> nextType = parameters[i + 1].getType();
            result[i] = result[i + 1] +
                    (isList(nextType) || isCommandData(nextType) ? 0 : 1);
        }
        return result;
    }


    /**
     * 匹配参数。
     *
     * @param parameters  方法参数数组
     * @param paramIndex  当前参数索引
     * @param args        指令参数列表
     * @param argIndex    当前指令参数索引
     * @param output      输出数组
     * @param commandData 指令数据
     * @param method      指令方法
     * @param nonListCount 非 List 参数在每个位置之后剩余数量的数组 非 List 参数的剩余数量数组
     * @return boolean
     */
    private boolean matchParameter(
            Parameter[] parameters,
            int paramIndex,
            List<String> args,
            int argIndex,
            Object[] output,
            CommandData<?> commandData,
            Method method,
            int[] nonListCount
    ) {
        // 全部参数匹配完成，后续参数可以丢弃
        if (paramIndex >= parameters.length) {
            return true;
        }

        // 处理当前参数
        Parameter param = parameters[paramIndex];
        Class<?> type = param.getType();
        if (isCommandData(type)) {
            // 如果是 CommandData 参数，直接使用传入的 commandData
            output[paramIndex] = commandData;
            return matchParameter(
                    parameters,
                    paramIndex + 1,
                    args,
                    argIndex,
                    output,
                    commandData,
                    method,
                    nonListCount
            );
        }
        if (isList(type)) {
            // 如果是 List 参数，尝试匹配多个参数
            return matchListParameter(
                    param,
                    parameters,
                    paramIndex,
                    args,
                    argIndex,
                    output,
                    commandData,
                    method,
                    nonListCount
            );
        }

        // 普通参数，尝试匹配一个参数
        return matchNormalParameter(
                param,
                parameters,
                paramIndex,
                args,
                argIndex,
                output,
                commandData,
                method,
                nonListCount
        );
    }


    /**
     * 匹配普通参数。
     *
     * @param param         当前参数
     * @param parameters    参数数组
     * @param paramIndex    当前参数索引
     * @param args          当前指令参数列表
     * @param argIndex      当前指令参数索引
     * @param output        输出数组
     * @param commandData   指令源数据
     * @param method        方法
     * @param nonListCount  非 List 参数在每个位置之后剩余数量的数组
     * @return boolean
     */
    private boolean matchNormalParameter(
            Parameter param,
            Parameter[] parameters,
            int paramIndex,
            List<String> args,
            int argIndex,
            Object[] output,
            CommandData<?> commandData,
            Method method,
            int[] nonListCount
    ) {
        // 尝试匹配一个参数，如果没有参数了，且有默认值，则使用默认值
        DefaultValue defaultValue = param.getAnnotation(DefaultValue.class);
        int remainingArgs = args.size() - argIndex;
        if (remainingArgs <= nonListCount[paramIndex] && defaultValue != null) {
            // 如果剩余参数不足以匹配后续非 List 参数，并且当前参数有默认值，则尝试使用默认值
            Object dv = resolveDefaultValue(param, defaultValue, commandData);
            if (dv != null) {
                output[paramIndex] = dv;
                if (matchParameter(
                        parameters,
                        paramIndex + 1,
                        args,
                        argIndex,
                        output,
                        commandData,
                        method,
                        nonListCount)) {
                    return true;
                }
            }
        }

        // 没有 DefaultValue，且没有参数来匹配，直接失败
        if (remainingArgs == 0) {
            return false;
        }
        // 尝试匹配一个参数
        Object converted = convertValue(args.get(argIndex), param.getType(), commandData);
        if (converted == null) {
            // 如果转化失败，则尝试使用默认值（如果有）
            if (defaultValue == null) {
                return false;
            }
            Object dv = resolveDefaultValue(param, defaultValue, commandData);
            if (dv == null) {
                return false;
            }
            output[paramIndex] = dv;
            return matchParameter(
                    parameters,
                    paramIndex + 1,
                    args,
                    argIndex,
                    output,
                    commandData,
                    method,
                    nonListCount
            );
        }
        // 转化成功，继续匹配下一个参数
        output[paramIndex] = converted;
        return matchParameter(
                parameters,
                paramIndex + 1,
                args,
                argIndex + 1,
                output,
                commandData,
                method,
                nonListCount
        );
    }


    /**
     * 匹配 List 参数。
     *
     * @param param        当前参数
     * @param parameters   参数数组
     * @param paramIndex   当前参数索引
     * @param args         当前指令参数列表
     * @param argIndex     当前指令参数索引
     * @param output       输出数组
     * @param commandData  指令源数据
     * @param method       方法
     * @param nonListCount 非 List 参数在每个位置之后剩余数量的数组
     * @return boolean
     */
    private boolean matchListParameter(
            Parameter param,
            Parameter[] parameters,
            int paramIndex,
            List<String> args,
            int argIndex,
            Object[] output,
            CommandData<?> commandData,
            Method method,
            int[] nonListCount
    ) {
        DefaultValue defaultValue = param.getAnnotation(DefaultValue.class);
        // 可以匹配的最大参数数量 = 总参数数量 - 当前参数索引 - 后续非 List 参数数量
        int max = args.size() - argIndex - nonListCount[paramIndex];
        if (max < 0) {
            // 如果 max < 0，说明剩余参数不足以匹配后续非 List 参数，直接失败
            return false;
        }
        // 获取 List 元素类型
        Class<?> elementType = getListElementType(param);
        List<Object> list = new ArrayList<>(max);
        // 尝试匹配多个参数，直到转化失败或达到 max
        for (int i = 0; i < max; i++) {
            // 转化参数，如果转化失败，则停止匹配 List 参数
            Object value = convertValue(args.get(argIndex + i), elementType, commandData);
            if (value == null) {
                break;
            }
            list.add(value);
        }
        // 尝试匹配剩余参数，如果 List 参数匹配了 size 个参数，则继续匹配下一个参数
        for (int size = list.size(); size > 0; size--) {
            output[paramIndex] = new ArrayList<>(list.subList(0, size));
            // 如果匹配成功，直接返回 true
            if (matchParameter(
                    parameters,
                    paramIndex + 1,
                    args,
                    argIndex + size,
                    output,
                    commandData,
                    method,
                    nonListCount))
                return true;
        }
        // 如果没有参数匹配成功，但 List 参数有默认值，则使用默认值（作为 List 的唯一元素）
        if (defaultValue != null) {
            Object dv = convertValue(defaultValue.value(), elementType, commandData);
            if (dv == null) {
                logger.errorWithReport(this.getClass().getSimpleName(), """
                                列表参数的默认值转化失败，无法使用默认值
                                 - 参数: %s
                                 - 默认值: %s
                                 - 元素类型: %s
                                """.formatted(param.getName(), defaultValue.value(), elementType.getName()));
                return false;
            }
            output[paramIndex] = Collections.singletonList(dv);
        } else {
            output[paramIndex] = Collections.emptyList();
        }
        // 继续匹配下一个参数
        return matchParameter(
                parameters,
                paramIndex + 1,
                args,
                argIndex,
                output,
                commandData,
                method,
                nonListCount
        );
    }

    /**
     * 解析默认值。
     *
     * @param param        当前参数
     * @param defaultValue 默认值注解
     * @param commandData 当前指令数据
     * @return 转换后的默认值；转换失败时返回 {@code null}
     */
    private Object resolveDefaultValue(
            Parameter param,
            DefaultValue defaultValue,
            CommandData<?> commandData
    ) {
        // 转化默认值，如果转化失败，记录错误日志并返回 null
        Object value = convertValue(defaultValue.value(), param.getType(), commandData);
        if (value == null) {
            logger.errorWithReport(this.getClass().getSimpleName(), """
                            转化默认值失败，无法使用默认值
                             - 参数: %s
                             - 默认值: %s
                             - 目标类型: %s
                            """.formatted(param.getName(), defaultValue.value(), param.getType().getName()));
        }
        return value;
    }

    /**
     * 获取 List 元素类型。
     * @param param List 参数
     * @return      List 元素类型，如果无法获取则默认为 String.class
     */
    private static Class<?> getListElementType(Parameter param) {
        Type type = param.getParameterizedType();
        if (type instanceof ParameterizedType pt) {
            Type arg = pt.getActualTypeArguments()[0];
            if (arg instanceof Class<?> clazz) {
                return clazz;
            }
        }
        return String.class;
    }


    /**
     * 转化参数值。
     *
     * @param input 输入字符串
     * @param type  类型
     * @param commandData 当前指令数据
     * @return 转化后的值，如果转化失败则返回 null
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object convertValue(
            String input,
            Class<?> type,
            CommandData<?> commandData
    ) {
        try {
            if (type == String.class)
                return input;
            if (type == int.class || type == Integer.class)
                return Integer.parseInt(input);
            if (type == long.class || type == Long.class)
                return Long.parseLong(input);
            if (type == float.class || type == Float.class)
                return Float.parseFloat(input);
            if (type == double.class || type == Double.class)
                return Double.parseDouble(input);
            if (type == boolean.class || type == Boolean.class) {
                if (input.equalsIgnoreCase("true"))
                    return true;
                if (input.equalsIgnoreCase("false"))
                    return false;
                return null;
            }
            if (type.isEnum())
                return Enum.valueOf((Class) type, input);
            return typeHandlerRegistry.findHandler(type)
                    .flatMap(handler -> handler.parse(input, commandData))
                    .orElse(null);
        }
        catch (RuntimeException exception) {
            logger.warn(
                    getClass().getSimpleName(),
                    "指令参数无法转换为类型 " + type.getName() + "：" + input,
                    exception
            );
        }
        return null;
    }

    /**
     * 保存参数遍历位置，避免可选参数和列表参数造成重复搜索。
     *
     * @param parameterIndex 方法参数索引
     * @param argumentIndex 指令文本参数索引
     * @author zExNocs
     * @date 2026/09/12
     * @since paperMC-1.0.0alpha
     */
    private record ParameterCursor(int parameterIndex, int argumentIndex) {
    }


    /**
     * 判断是否为 List 类型。
     *
     * @param type 类型
     * @return boolean
     */
    private static boolean isList(Class<?> type) {
        return List.class.isAssignableFrom(type);
    }


    /**
     * 判断是否为 CommandData 类型。
     *
     * @param type 类型
     * @return boolean
     */
    private static boolean isCommandData(Class<?> type) {
        return CommandData.class.isAssignableFrom(type);
    }


    /**
     * 验证 CommandData 类型是否匹配 Method 参数的要求。
     *
     * @param parameter   Method 参数
     * @param commandData 指令数据
     * @throws CommandDataTypeMismatchException 如果CommandData不符合Method参数的要求，则直接抛出该异常，表示无法匹配。
     */
    public static void validateCommandDataType(
            Parameter parameter,
            CommandData<?> commandData
    ) throws CommandDataTypeMismatchException {
        // 获取参数的泛型类型，如果不是 ParameterizedType，则抛出异常
        Type methodType = parameter.getParameterizedType();
        if (!(methodType instanceof ParameterizedType pt)) {
            throw new CommandDataTypeMismatchException();
        }
        Class<?> expected = (Class<?>) pt.getActualTypeArguments()[0];
        // 获取 CommandData 的泛型类型参数，如果无法获取或不合法，则抛出异常
        Class<?> actualClass = commandData.getRawDataType();
        // 判断实际类型是否与预期类型兼容，如果不兼容，则抛出异常
        if (!expected.isAssignableFrom(actualClass)) throw new CommandDataTypeMismatchException();
    }
}
