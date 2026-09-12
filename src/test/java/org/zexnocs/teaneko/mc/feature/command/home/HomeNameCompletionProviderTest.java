package org.zexnocs.teaneko.mc.feature.command.home;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.zexnocs.teaneko.core.command.CommandData;
import org.zexnocs.teaneko.core.command.api.Command;
import org.zexnocs.teaneko.core.command.api.CommandPermission;
import org.zexnocs.teaneko.core.command.api.CommandScope;
import org.zexnocs.teaneko.core.logger.ILogger;
import org.zexnocs.teaneko.mc.core.command.PaperCommandClient;
import org.zexnocs.teaneko.mc.core.command.PaperCommandContext;
import org.zexnocs.teaneko.mc.core.command.api.TeaNekoMCCommand;
import org.zexnocs.teaneko.mc.core.command.completion.PaperCommandCompletionContext;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

/**
 * 验证 Home 名称补全只读取内存快照，并在关闭时清理缓存。
 *
 * @author zExNocs
 * @date 2026/09/12
 * @since paperMC-1.0.0alpha
 */
class HomeNameCompletionProviderTest {
    /**
     * 验证补全回调不触发数据库预加载。
     */
    @Test
    void shouldOnlyReadCachedHomeNamesDuringCompletion() throws Exception {
        HomeService homeService = mock(HomeService.class);
        HomeNameCompletionProvider provider = new HomeNameCompletionProvider(
                homeService,
                mock(ILogger.class)
        );
        Player sender = mock(Player.class);
        UUID playerUuid = UUID.randomUUID();
        when(sender.getUniqueId()).thenReturn(playerUuid);
        when(homeService.findCachedHomeNames(playerUuid))
                .thenReturn(List.of("mine", "village"));

        assertEquals(
                List.of("home", "mine", "village"),
                provider.complete(createContext(sender))
        );
        verify(homeService).findCachedHomeNames(playerUuid);

        provider.close();
        verify(homeService).clearCachedHomeNames();
    }

    /**
     * 创建 Home 提供器需要的最小补全上下文。
     */
    private PaperCommandCompletionContext createContext(Player sender) throws Exception {
        JavaPlugin plugin = mock(JavaPlugin.class);
        Command command = TestCommand.class.getAnnotation(Command.class);
        PaperCommandContext commandContext = new PaperCommandContext(
                plugin,
                sender,
                command.value()[0],
                "test-home",
                new String[]{""},
                command,
                TestCommand.class.getAnnotation(TeaNekoMCCommand.class)
        );
        Method method = TestCommand.class.getDeclaredMethod(
                "execute",
                CommandData.class,
                String.class
        );
        return new PaperCommandCompletionContext(
                commandContext,
                new TestCommand(),
                method,
                method.getParameters()[1],
                1,
                "",
                "",
                List.of()
        );
    }

    /**
     * 为 Home 提供器测试提供最小指令元数据。
     *
     * @author zExNocs
     * @date 2026/09/12
     * @since paperMC-1.0.0alpha
     */
    @Command(
            value = "/test-home",
            permission = CommandPermission.ALL,
            scope = CommandScope.ALL,
            supportedClients = PaperCommandClient.class
    )
    @TeaNekoMCCommand
    private static final class TestCommand {
        private void execute(CommandData<PaperCommandContext> commandData, String homeName) {
        }
    }
}
