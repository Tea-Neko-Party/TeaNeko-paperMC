package org.zexnocs.teaneko.core.command;

import org.junit.jupiter.api.Test;
import org.zexnocs.teaneko.core.command.api.CommandPermission;
import org.zexnocs.teaneko.core.command.api.CommandScope;
import org.zexnocs.teaneko.core.command.api.DefaultValue;
import org.zexnocs.teaneko.core.command.interfaces.ICommandArgumentTypeHandler;
import org.zexnocs.teaneko.core.logger.ILogger;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * 验证可扩展参数类型在普通参数、默认值、列表与补全定位中的行为。
 *
 * @author zExNocs
 * @date 2026/09/12
 * @since paperMC-1.0.0alpha
 */
class CommandArgumentProcessorExtensionTest {
    private final TestValueTypeHandler handler = new TestValueTypeHandler();
    private final CommandArgumentProcessor processor = new CommandArgumentProcessor(
            mock(ILogger.class),
            new CommandArgumentTypeHandlerRegistry(List.of(handler))
    );

    /**
     * 验证扩展类型可以用于普通参数、默认值和列表元素。
     */
    @Test
    void shouldParseCustomTypesInEverySupportedParameterShape() throws Exception {
        CommandData<String> commandData = createCommandData();

        Object[] direct = processor.process(method("direct"), new String[]{"one"}, commandData);
        assertArrayEquals(new Object[]{new TestValue("one")}, direct);

        Object[] defaultValue = processor.process(method("withDefault"), new String[0], commandData);
        assertArrayEquals(new Object[]{new TestValue("fallback")}, defaultValue);

        Object[] list = processor.process(
                method("withList"),
                new String[]{"one", "two"},
                commandData
        );
        assertInstanceOf(List.class, list[0]);
        assertEquals(List.of(new TestValue("one"), new TestValue("two")), list[0]);
    }

    /**
     * 验证可选参数和列表参数会暴露所有合法的光标目标。
     */
    @Test
    void shouldLocateCompletionParametersWithOptionalAndListArguments() throws Exception {
        Method method = method("positioning");
        CommandData<String> commandData = createCommandData();

        assertEquals(
                List.of("optional", "required"),
                processor.findCompletionParameters(method, new String[0], commandData)
                        .stream()
                        .map(java.lang.reflect.Parameter::getName)
                        .toList()
        );
        assertEquals(
                List.of("required"),
                processor.findCompletionParameters(method, new String[]{"text"}, commandData)
                        .stream()
                        .map(java.lang.reflect.Parameter::getName)
                        .toList()
        );
        assertEquals(
                List.of("remaining"),
                processor.findCompletionParameters(method, new String[]{"text", "1"}, commandData)
                        .stream()
                        .map(java.lang.reflect.Parameter::getName)
                        .toList()
        );
        assertEquals(
                List.of("values"),
                processor.findCompletionParameters(
                                method("withList"),
                                new String[]{"one"},
                                commandData
                        )
                        .stream()
                        .map(java.lang.reflect.Parameter::getName)
                        .toList()
        );
    }

    /**
     * 验证同一精确类型不允许注册多个处理器。
     */
    @Test
    void shouldRejectDuplicateTypeHandlers() {
        assertThrows(
                IllegalStateException.class,
                () -> new CommandArgumentTypeHandlerRegistry(List.of(
                        handler,
                        new TestValueTypeHandler()
                ))
        );
    }

    private void direct(TestValue value) {
    }

    private void withDefault(@DefaultValue("fallback") TestValue value) {
    }

    private void withList(List<TestValue> values) {
    }

    private void positioning(CommandData<String> commandData,
                             @DefaultValue("default") String optional,
                             int required,
                             List<String> remaining) {
    }

    /**
     * 按名称获取当前测试类中的参数方法。
     */
    private Method method(String name) {
        return List.of(getClass().getDeclaredMethods()).stream()
                .filter(method -> method.getName().equals(name))
                .findFirst()
                .orElseThrow();
    }

    /**
     * 创建参数处理器需要的最小 Core 指令数据。
     */
    private CommandData<String> createCommandData() {
        return CommandData.<String>builder()
                .body("test")
                .args(new String[0])
                .scope(CommandScope.OTHER)
                .permission(CommandPermission.ALL)
                .senderId("test")
                .scopeId("test")
                .rawData("raw")
                .build();
    }

    /**
     * 表示测试使用的扩展参数值。
     *
     * @param value 原始文本
     * @author zExNocs
     * @date 2026/09/12
     * @since paperMC-1.0.0alpha
     */
    private record TestValue(String value) {
    }

    /**
     * 将任意非空文本转换为测试参数值。
     *
     * @author zExNocs
     * @date 2026/09/12
     * @since paperMC-1.0.0alpha
     */
    private static final class TestValueTypeHandler
            implements ICommandArgumentTypeHandler<TestValue> {
        /**
         * 获取测试值类型。
         *
         * @return 测试值类型
         */
        @Override
        public Class<TestValue> targetType() {
            return TestValue.class;
        }

        /**
         * 转换非空测试文本。
         *
         * @param input 原始参数文本
         * @param commandData 当前指令数据
         * @return 测试值
         */
        @Override
        public Optional<TestValue> parse(String input, CommandData<?> commandData) {
            return input.isBlank() ? Optional.empty() : Optional.of(new TestValue(input));
        }
    }
}
