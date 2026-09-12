package org.zexnocs.teaneko.mc.core.command.completion;

import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.util.Pair;
import org.zexnocs.teaneko.core.command.CommandArgumentProcessor;
import org.zexnocs.teaneko.core.command.CommandArgumentTypeHandlerRegistry;
import org.zexnocs.teaneko.core.command.CommandData;
import org.zexnocs.teaneko.core.command.CommandMapData;
import org.zexnocs.teaneko.core.command.api.*;
import org.zexnocs.teaneko.core.logger.ILogger;
import org.zexnocs.teaneko.core.utils.scanner.inerfaces.IBeanScanner;
import org.zexnocs.teaneko.mc.core.command.PaperCommandClient;
import org.zexnocs.teaneko.mc.core.command.PaperCommandContext;
import org.zexnocs.teaneko.mc.core.command.PaperCommandConverter;
import org.zexnocs.teaneko.mc.core.command.api.TeaNekoMCCommand;
import org.zexnocs.teaneko.mc.core.command.api.TeaNekoMCSubCommand;
import org.zexnocs.teaneko.mc.core.command.completion.api.CommandComplete;
import org.zexnocs.teaneko.mc.core.command.completion.interfaces.IPaperCommandArgumentTypeHandler;
import org.zexnocs.teaneko.mc.core.command.completion.interfaces.IPaperCommandCompletionProvider;
import org.zexnocs.teaneko.mc.utils.PaperCommandIntrospectionUtils;

import java.lang.reflect.Method;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 验证 Paper 自动补全服务对注解、类型、权限和前缀的统一处理。
 *
 * @author zExNocs
 * @date 2026/09/12
 * @since paperMC-1.0.0alpha
 */
class PaperCommandCompletionServiceTest {
    private final TestValueTypeHandler typeHandler = new TestValueTypeHandler();
    private final TestCompletionProvider explicitProvider = new TestCompletionProvider();
    private JavaPlugin plugin;
    private CommandSender sender;
    private CommandMapData commandMapData;
    private PaperCommandCompletionService completionService;

    /**
     * 构造每个测试共用的 Paper 与 Core 替身。
     */
    @BeforeEach
    void setUp() throws Exception {
        plugin = mock(JavaPlugin.class);
        Server server = mock(Server.class);
        sender = mock(CommandSender.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getName()).thenReturn("测试服务器");
        when(sender.getName()).thenReturn("测试发送者");

        IBeanScanner beanScanner = mock(IBeanScanner.class);
        when(beanScanner.getBeansOfType(IPaperCommandCompletionProvider.class))
                .thenReturn(Map.of("testCompletionProvider", explicitProvider));
        doReturn(TestCompletionProvider.class)
                .when(beanScanner)
                .getBeanClass(explicitProvider);

        ILogger logger = mock(ILogger.class);
        CommandArgumentTypeHandlerRegistry registry =
                new CommandArgumentTypeHandlerRegistry(List.of(typeHandler));
        CommandArgumentProcessor argumentProcessor = new CommandArgumentProcessor(logger, registry);
        completionService = new PaperCommandCompletionService(
                beanScanner,
                argumentProcessor,
                registry,
                new PaperCommandConverter(),
                logger
        );
        commandMapData = createCommandMapData();
        completionService.prepareCommand(
                commandMapData,
                PaperCommandIntrospectionUtils.getMinecraftSubCommandMetadata(TestCommand.class)
        );
    }

    /**
     * 验证静态、显式、特殊类型、布尔和枚举候选均由中央服务生成。
     */
    @Test
    void shouldCombineEveryCompletionSource() {
        assertEquals(List.of("alpha"), complete("static", "a"));
        assertEquals(List.of("dynamic-one", "dynamic-two"), complete("dynamic", "dynamic-"));
        assertEquals(List.of("typed-one", "typed-two"), complete("typed", "typed-"));
        assertEquals(
                List.of("dynamic-one", "dynamic-two", "static-one", "typed-one", "typed-two"),
                complete("combined", "")
        );
        assertEquals(List.of("static-only"), complete("without-type", ""));
        assertEquals(List.of("false", "true"), complete("boolean", ""));
        assertEquals(List.of("FIRST", "SECOND"), complete("enum", ""));
    }

    /**
     * 验证根补全和深入参数补全都不会泄露无权限子指令。
     */
    @Test
    void shouldHideUnauthorizedSubCommandsAndCandidates() {
        List<String> root = complete("");
        assertFalse(root.contains("admin"));
        assertEquals(List.of(), complete("admin", ""));

        when(sender.hasPermission("test.command.admin")).thenReturn(true);
        assertTrue(complete("").contains("admin"));
        assertEquals(List.of("1", "10", "5"), complete("admin", ""));
    }

    /**
     * 验证显式引用但未注册为 Spring Bean 的提供器会阻止指令注册。
     */
    @Test
    void shouldRejectMissingExplicitProvider() {
        IBeanScanner emptyScanner = mock(IBeanScanner.class);
        when(emptyScanner.getBeansOfType(IPaperCommandCompletionProvider.class))
                .thenReturn(Map.of());
        ILogger logger = mock(ILogger.class);
        CommandArgumentTypeHandlerRegistry registry =
                new CommandArgumentTypeHandlerRegistry(List.of(typeHandler));
        PaperCommandCompletionService service = new PaperCommandCompletionService(
                emptyScanner,
                new CommandArgumentProcessor(logger, registry),
                registry,
                new PaperCommandConverter(),
                logger
        );

        assertThrows(
                IllegalStateException.class,
                () -> service.prepareCommand(commandMapData, Map.of())
        );
    }

    /**
     * 执行一次指定参数序列的补全。
     */
    private List<String> complete(String... args) {
        Command command = TestCommand.class.getAnnotation(Command.class);
        TeaNekoMCCommand minecraftCommand =
                TestCommand.class.getAnnotation(TeaNekoMCCommand.class);
        PaperCommandContext context = new PaperCommandContext(
                plugin,
                sender,
                command.value()[0],
                "test",
                args,
                command,
                minecraftCommand
        );
        return completionService.complete(context, commandMapData);
    }

    /**
     * 从测试指令类构造 Core 扫描器等价的映射数据。
     */
    private CommandMapData createCommandMapData() throws Exception {
        TestCommand commandBean = new TestCommand();
        Method defaultMethod = TestCommand.class.getDeclaredMethod(
                "defaultCommand",
                CommandData.class,
                String.class
        );
        Map<String, Pair<SubCommand, Method>> subCommands = new LinkedHashMap<>();
        for (Method method : TestCommand.class.getDeclaredMethods()) {
            SubCommand annotation = method.getAnnotation(SubCommand.class);
            if (annotation == null) {
                continue;
            }
            for (String name : annotation.value()) {
                subCommands.put(name, Pair.of(annotation, method));
            }
        }
        return CommandMapData.builder()
                .command(commandBean)
                .commandAnnotation(TestCommand.class.getAnnotation(Command.class))
                .defaultCommandMethod(defaultMethod)
                .defaultCommandAnnotation(defaultMethod.getAnnotation(DefaultCommand.class))
                .subCommandMap(subCommands)
                .build();
    }

    /**
     * 提供自动补全测试需要的各种参数声明。
     *
     * @author zExNocs
     * @date 2026/09/12
     * @since paperMC-1.0.0alpha
     */
    @Command(
            value = "/test",
            permission = CommandPermission.ALL,
            scope = CommandScope.ALL,
            supportedClients = PaperCommandClient.class
    )
    @TeaNekoMCCommand
    private static final class TestCommand {
        @DefaultCommand
        private void defaultCommand(CommandData<PaperCommandContext> commandData,
                                    @CommandComplete({"default-one"}) String value) {
        }

        @SubCommand("static")
        private void staticValues(CommandData<PaperCommandContext> commandData,
                                  @CommandComplete({"alpha", "beta"}) String value) {
        }

        @SubCommand("dynamic")
        private void dynamicValues(CommandData<PaperCommandContext> commandData,
                                   @CommandComplete(providers = TestCompletionProvider.class)
                                   String value) {
        }

        @SubCommand("typed")
        private void typedValues(CommandData<PaperCommandContext> commandData,
                                 TestValue value) {
        }

        @SubCommand("combined")
        private void combinedValues(
                CommandData<PaperCommandContext> commandData,
                @CommandComplete(
                        value = "static-one",
                        providers = TestCompletionProvider.class
                ) TestValue value) {
        }

        @SubCommand("without-type")
        private void valuesWithoutTypeProvider(
                CommandData<PaperCommandContext> commandData,
                @CommandComplete(value = "static-only", useTypeProvider = false)
                TestValue value) {
        }

        @SubCommand("boolean")
        private void booleanValues(CommandData<PaperCommandContext> commandData,
                                   boolean value) {
        }

        @SubCommand("enum")
        private void enumValues(CommandData<PaperCommandContext> commandData,
                                TestMode value) {
        }

        @SubCommand(value = "admin", permission = CommandPermission.ADMIN)
        @TeaNekoMCSubCommand(permission = "test.command.admin")
        private void adminValues(CommandData<PaperCommandContext> commandData,
                                 @CommandComplete({"1", "5", "10"}) int value) {
        }
    }

    /**
     * 表示自动类型补全测试值。
     *
     * @param value 原始值
     * @author zExNocs
     * @date 2026/09/12
     * @since paperMC-1.0.0alpha
     */
    private record TestValue(String value) {
    }

    /**
     * 表示枚举自动补全测试值。
     *
     * @author zExNocs
     * @date 2026/09/12
     * @since paperMC-1.0.0alpha
     */
    private enum TestMode {
        FIRST,
        SECOND
    }

    /**
     * 提供显式注解候选值。
     *
     * @author zExNocs
     * @date 2026/09/12
     * @since paperMC-1.0.0alpha
     */
    private static final class TestCompletionProvider
            implements IPaperCommandCompletionProvider {
        /**
         * 返回动态测试候选。
         *
         * @param context 当前参数补全上下文
         * @return 动态候选
         */
        @Override
        public Collection<String> complete(PaperCommandCompletionContext context) {
            return List.of("dynamic-one", "dynamic-two");
        }
    }

    /**
     * 提供可解析且可自动补全的测试类型。
     *
     * @author zExNocs
     * @date 2026/09/12
     * @since paperMC-1.0.0alpha
     */
    private static final class TestValueTypeHandler
            implements IPaperCommandArgumentTypeHandler<TestValue> {
        /**
         * 获取测试目标类型。
         *
         * @return 测试值类型
         */
        @Override
        public Class<TestValue> targetType() {
            return TestValue.class;
        }

        /**
         * 转换测试值。
         *
         * @param input 原始参数文本
         * @param commandData 当前指令数据
         * @return 测试值
         */
        @Override
        public Optional<TestValue> parse(String input, CommandData<?> commandData) {
            return Optional.of(new TestValue(input));
        }

        /**
         * 返回类型默认候选。
         *
         * @param context 当前参数补全上下文
         * @return 类型候选
         */
        @Override
        public Collection<String> complete(PaperCommandCompletionContext context) {
            return List.of("typed-one", "typed-two");
        }
    }
}
