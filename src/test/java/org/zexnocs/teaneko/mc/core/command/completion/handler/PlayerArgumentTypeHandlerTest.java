package org.zexnocs.teaneko.mc.core.command.completion.handler;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.zexnocs.teaneko.core.command.CommandData;
import org.zexnocs.teaneko.core.command.api.Command;
import org.zexnocs.teaneko.core.command.api.CommandPermission;
import org.zexnocs.teaneko.core.command.api.CommandScope;
import org.zexnocs.teaneko.mc.core.command.PaperCommandClient;
import org.zexnocs.teaneko.mc.core.command.PaperCommandContext;
import org.zexnocs.teaneko.mc.core.command.api.TeaNekoMCCommand;
import org.zexnocs.teaneko.mc.core.command.completion.PaperCommandCompletionContext;
import org.zexnocs.teaneko.mc.core.command.completion.PaperKnownPlayerDirectory;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.*;

/**
 * 验证在线玩家与历史玩家特殊参数类型的解析和可见性补全。
 *
 * @author zExNocs
 * @date 2026/09/12
 * @since paperMC-1.0.0alpha
 */
class PlayerArgumentTypeHandlerTest {
    private JavaPlugin plugin;
    private Server server;
    private Player sender;
    private Player onlinePlayer;
    private Player hiddenPlayer;
    private OfflinePlayer knownPlayer;
    private PaperKnownPlayerDirectory directory;
    private PaperCommandContext context;

    /**
     * 创建服务器、发送者和玩家替身。
     */
    @BeforeEach
    void setUp() {
        plugin = mock(JavaPlugin.class);
        server = mock(Server.class);
        sender = mock(Player.class);
        onlinePlayer = mock(Player.class);
        hiddenPlayer = mock(Player.class);
        knownPlayer = mock(OfflinePlayer.class);
        directory = new PaperKnownPlayerDirectory();

        UUID onlineUuid = UUID.randomUUID();
        UUID hiddenUuid = UUID.randomUUID();
        UUID knownUuid = UUID.randomUUID();
        when(plugin.isEnabled()).thenReturn(true);
        when(plugin.getServer()).thenReturn(server);
        when(onlinePlayer.getName()).thenReturn("Online");
        when(onlinePlayer.getUniqueId()).thenReturn(onlineUuid);
        when(hiddenPlayer.getName()).thenReturn("Hidden");
        when(hiddenPlayer.getUniqueId()).thenReturn(hiddenUuid);
        when(knownPlayer.getName()).thenReturn("Known");
        when(knownPlayer.getUniqueId()).thenReturn(knownUuid);
        when(knownPlayer.hasPlayedBefore()).thenReturn(true);
        doReturn(List.of(onlinePlayer, hiddenPlayer)).when(server).getOnlinePlayers();
        when(server.getPlayerExact("Online")).thenReturn(onlinePlayer);
        when(server.getPlayerExact("Hidden")).thenReturn(hiddenPlayer);
        when(server.getOfflinePlayer(knownUuid)).thenReturn(knownPlayer);
        when(sender.canSee(onlinePlayer)).thenReturn(true);
        when(sender.canSee(hiddenPlayer)).thenReturn(false);

        directory.remember(onlineUuid, "Online");
        directory.remember(hiddenUuid, "Hidden");
        directory.remember(knownUuid, "Known");
        Command command = PlayerTestCommand.class.getAnnotation(Command.class);
        context = new PaperCommandContext(
                plugin,
                sender,
                command.value()[0],
                "players",
                new String[0],
                command,
                PlayerTestCommand.class.getAnnotation(TeaNekoMCCommand.class)
        );
    }

    /**
     * 验证在线玩家类型按精确名称解析并隐藏不可见玩家候选。
     */
    @Test
    void shouldParseAndCompleteVisibleOnlinePlayers() throws Exception {
        OnlinePlayerArgumentTypeHandler handler = new OnlinePlayerArgumentTypeHandler();
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            assertSame(onlinePlayer, handler.parse("Online", commandData()).orElseThrow());
        }

        PaperCommandCompletionContext completionContext = completionContext(0);
        assertEquals(List.of("Online"), handler.complete(completionContext));
    }

    /**
     * 验证历史玩家类型可按缓存名称解析，并隐藏当前不可见的在线玩家。
     */
    @Test
    void shouldParseAndCompleteKnownPlayers() throws Exception {
        KnownPlayerArgumentTypeHandler handler =
                new KnownPlayerArgumentTypeHandler(directory);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            assertSame(knownPlayer, handler.parse("Known", commandData()).orElseThrow());
        }

        assertEquals(
                List.of("Known", "Online"),
                handler.complete(completionContext(1)).stream().sorted().toList()
        );
    }

    /**
     * 创建具有 Paper 原始上下文的 Core 指令数据。
     */
    private CommandData<PaperCommandContext> commandData() {
        return CommandData.<PaperCommandContext>builder()
                .body("/players")
                .args(new String[0])
                .scope(CommandScope.OTHER)
                .permission(CommandPermission.ALL)
                .senderId("test")
                .scopeId("test")
                .rawData(context)
                .build();
    }

    /**
     * 创建指定参数的补全上下文。
     */
    private PaperCommandCompletionContext completionContext(int parameterIndex) throws Exception {
        Method method = PlayerTestCommand.class.getDeclaredMethod(
                "targets",
                Player.class,
                OfflinePlayer.class
        );
        return new PaperCommandCompletionContext(
                context,
                new PlayerTestCommand(),
                method,
                method.getParameters()[parameterIndex],
                parameterIndex,
                "",
                "",
                List.of()
        );
    }

    /**
     * 为玩家参数测试提供最小指令元数据。
     *
     * @author zExNocs
     * @date 2026/09/12
     * @since paperMC-1.0.0alpha
     */
    @Command(
            value = "/players",
            permission = CommandPermission.ALL,
            scope = CommandScope.ALL,
            supportedClients = PaperCommandClient.class
    )
    @TeaNekoMCCommand
    private static final class PlayerTestCommand {
        private void targets(Player online, OfflinePlayer known) {
        }
    }
}
