# Paper 指令扩展

Paper 指令同时复用 TeaNeko Core 的扫描与执行链，并由构建任务自动生成 `plugin.yml`。

## 声明指令

一个类只有同时具备 `@Command` 与 `@TeaNekoMCCommand` 才会注册到 Minecraft：

```java
@Command(
        value = {"/example", "/示例"},
        permission = CommandPermission.ALL,
        scope = CommandScope.ALL,
        supportedClients = PaperCommandClient.class
)
@TeaNekoMCCommand(
        description = "示例指令。",
        usage = "/example [文本]",
        permission = "teaneko.example.use",
        permissionDescription = "允许使用示例指令。",
        permissionDefault = TeaNekoMCCommand.PermissionDefault.TRUE
)
public final class ExampleCommand {
    @DefaultCommand
    public void execute(CommandData<PaperCommandContext> commandData,
                        @DefaultValue("默认文本") String text) {
        PaperCommandUtils.sendOnMainThread(commandData.getRawData(), text);
    }
}
```

- `@Command` 继续控制 Core 权限、作用域、客户端、任务命名空间、默认方法和子指令。
- `@TeaNekoMCCommand` 控制 `plugin.yml` 中的说明、用法、Bukkit 权限及默认授权。
- `@Command.value()` 的第一个名称是主指令，其余名称自动生成 `aliases`。
- 未标注 `@TeaNekoMCCommand` 的现有 Core 指令不会暴露给 Minecraft。

## 自动生成与运行期注册

`processResources`、`shadowJar`、`build` 和 `runServer` 会自动执行 `generatePluginYml`。生成文件位于：

```text
build/generated/resources/pluginYml/plugin.yml
```

服务器启动后，`PaperCommandService` 会再次扫描 Spring Bean，并将生成描述文件中的 Bukkit 指令绑定到 Core `CommandDispatcher`。参数转换、权限与作用域校验、事件通知和异步执行仍由 `teanekocore.command` 完成。

## Bukkit API 与线程

Core 默认执行器会异步调用指令方法。读取玩家位置、传送、发送消息等 Bukkit 操作应使用 `PaperCommandUtils` 切换到服务器主线程。

如需自定义参数补全，让指令 Bean 实现 `IPaperCommandTabCompleter`。补全方法在服务器主线程执行，不得访问数据库或执行阻塞任务。
