# TeaNeko Paper

TeaNeko 的 Paper 26.2 插件工程。`org.zexnocs.teaneko.core` 与 `org.zexnocs.teaneko.app` 会一起打入插件，并由 Paper 生命周期管理的 Spring Boot 应用上下文加载。

## 环境

- Paper `26.2`
- Java 25
- Gradle Wrapper（已随项目提供）

## 构建与部署

```powershell
.\gradlew.bat shadowJar
```

将 `build/libs/TeaNeko-Paper-<version>.jar` 放入 Paper 服务器的 `plugins/` 目录后重启或启动服务器。插件启用后可执行 `/teaneko` 验证加载状态。

`shadowJar` 会将 Spring Boot、Hibernate、Reactor、Jackson 和数据库驱动打入插件；Paper API 保持由服务器提供，因而不会被重复打包。为保留 Spring Boot 自动配置资源，Spring 相关包不进行重定位。

## Spring Boot 应用上下文

插件启用时，`TeaNekoPaperPlugin` 只负责衔接 Paper 生命周期，并将启动工作委托给 `TeaNekoCoreHandler`。处理器通过 `TeaNekoCoreInjection` 创建 Servlet 模式的 Spring Boot 应用上下文，扫描 `org.zexnocs` 下的组件，并启动 `org.zexnocs.teaneko.app` 的 WebSocket 外部交互层。插件停用时，处理器会先逆序关闭 Paper 初始化器，再关闭 Spring 上下文，进而停止 Web 服务、数据库连接池和 Spring 管理的任务。

首次启用会将默认 `application.properties` 与 `application-prod.properties` 复制到插件数据目录。插件未收到有效的活动 Profile 时默认启用 `prod`；生产服务器请在 `plugins/TeaNekoPaper/application-prod.properties` 填写 MySQL 连接信息，或向 Paper 进程设置 `TEANEKO_DATABASE_URL`、`TEANEKO_DATABASE_USERNAME` 与 `TEANEKO_DATABASE_PASSWORD` 环境变量。外部配置文件会覆盖插件 JAR 内的模板，因此凭据不会被写入构建产物。

生产配置示例：

```properties
spring.datasource.url=jdbc:mysql://127.0.0.1:3306/teaneko?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
spring.datasource.username=你的用户名
spring.datasource.password=你的密码
```

`dev` 与 `prod` 均可独立指定 `spring.datasource.driver-class-name`。例如，需要在某个 Profile 使用 H2 文件数据库时，在对应的 `application-dev.properties` 或 `application-prod.properties` 中替换数据库配置：

```properties
spring.datasource.driver-class-name=org.h2.Driver
spring.datasource.url=jdbc:h2:file:${teaneko.paper.data-directory}/database/teaneko;MODE=MySQL;AUTO_SERVER=TRUE
spring.datasource.username=sa
spring.datasource.password=
```

## 核心初始化架构

启动链路如下：

```text
Paper onEnable
  → TeaNekoCoreHandler
  → TeaNekoCoreInjection 启动 Spring Boot
  → TeaNekoInitializerScanner 扫描初始化器
  → 按 priority 从高到低调用 initialize
  → PaperCommandService 验证并绑定 Minecraft 指令
```

`TeaNekoPaperPlugin` 不直接初始化具体功能。需要访问 Paper API 或在 Spring 上下文完成后执行启动动作的服务，应实现 `ITeaNekoInitializer` 并标注 `@TeaNekoInitializer`：

```java
/**
 * 演示一个具有明确启动和关闭生命周期的 Paper 功能。
 *
 * @author zExNocs
 * @date 2026/09/12
 * @since paperMC-1.0.0alpha
 */
@TeaNekoInitializer(required = true, priority = 200)
public final class ExampleInitializer implements ITeaNekoInitializer {
    private BukkitTask task;

    /**
     * 创建当前功能需要的 Paper 侧资源。
     *
     * @param plugin 当前 Paper 插件
     */
    @Override
    public void initialize(JavaPlugin plugin) {
        task = plugin.getServer().getScheduler().runTaskTimer(
                plugin,
                () -> {
                    // 执行需要位于 Paper 主线程的周期工作。
                },
                20L,
                20L
        );
    }

    /**
     * 释放初始化阶段创建的资源。
     */
    @Override
    public void close() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }
}
```

初始化器遵循以下约定：

- `@TeaNekoInitializer` 已经包含 Spring `@Component` 语义，不要重复添加组件注解。
- `priority` 数值越大越早初始化；相同优先级按实现类名排序，保证启动顺序稳定。默认值是 `0`。
- `required` 默认是 `false`。可选初始化器失败时会记录错误、立即尝试清理并继续启动；必须初始化器失败时会回滚已初始化资源、关闭 Spring 上下文并使插件启动失败。
- 只有成功初始化的实例会在插件停用时按照相反顺序关闭。`close()` 也可能在 `initialize()` 中途失败后被调用，因此必须允许部分初始化状态，并应设计为可重复安全执行。
- Bean 之间的结构依赖优先使用 Spring 构造器注入；`priority` 只用于约束必须按顺序执行的 Paper 侧启动动作。
- 初始化器在 Paper 插件启动线程中同步执行，不要执行无界等待或长期阻塞任务。需要异步工作的功能应在初始化时提交受控任务，并在 `close()` 中取消。

### 指令服务初始化

`PaperCommandService` 是优先级为 `100` 的必须初始化器。它会先完成所有 Spring 指令 Bean、Core 声明、Paper 客户端兼容性和 `plugin.yml` 声明检查，全部通过后才统一绑定 Bukkit Executor 与 TabCompleter。绑定阶段发生异常时会恢复原绑定；插件关闭时也会释放这些绑定。

构建期和 Spring 运行期共同使用 `TeaNekoAppApplication.ROOT_SCAN_PACKAGE`，当前值为 `org.zexnocs`。因此放在任意 `org.zexnocs.*` 新功能包中的顶级指令类都能进入同一套扫描流程，不再局限于 `org.zexnocs.teaneko.mc` 包。若未来修改项目根包，必须修改该常量后重新构建插件。

添加 Minecraft 指令时无需修改 `TeaNekoPaperPlugin`：

1. 在 `org.zexnocs.*` 下创建由 Spring 管理的顶级类，并同时标注 Core 的 `@Command` 与 Paper 的 `@TeaNekoMCCommand`。
2. 使用 `@DefaultCommand`、`@SubCommand` 和可选的 `@TeaNekoMCSubCommand` 声明执行方法与子指令权限。
3. 执行 `shadowJar`、`build` 或 `runServer`。构建任务会自动生成 `plugin.yml`，服务器启动时指令服务会自动完成运行期绑定。

更完整的参数转换、权限和自动补全示例见 `src/main/java/org/zexnocs/teaneko/mc/core/command/README.md`。

### 参数解析与自动补全

指令参数可以使用 `@CommandComplete` 声明固定候选或独立的 Spring 补全提供器。
`Player` 会自动解析并补全在线玩家，`OfflinePlayer` 会自动解析并补全服务器存在过的玩家；
布尔和枚举参数也具有默认候选。业务相关候选应实现 `IPaperCommandCompletionProvider`，
新的可解析 Java 类型应实现 `IPaperCommandArgumentTypeHandler<T>`。全部扩展 Bean 都会随
Spring 根扫描包自动发现，无需修改 `TeaNekoPaperPlugin` 或中央补全函数。

补全回调运行在 Paper 主线程，只允许读取内存快照。需要数据库数据的提供器应通过初始化器
或事件提前异步加载，并在 `close()` 中注销监听器、取消任务和清理缓存。

## 本地运行与调试

项目提供 `runServer` Gradle 任务。首次执行会下载 Paper 26.2、将当前构建的插件自动加载到本地服务器，并使用项目根目录的 `run/` 作为服务器目录；该目录已被 Git 忽略。

首次启动需由开发者自行确认 Minecraft EULA：

```powershell
.\gradlew.bat runServer -PacceptMinecraftEula=true
```

本地服务器监听 `25565` 端口。停止服务器可在 Gradle 控制台输入 `stop`。后续启动无需再次提供 EULA 参数：

```powershell
.\gradlew.bat runServer
```

`runServer` 默认以 `prod` Profile 启动，不会读取或复制开发配置。只有手动传入 `-PspringProfile=dev` 时，才会将本机未提交的 `src/main/resources/application-dev.properties` 复制到 `run/plugins/TeaNekoPaper/` 并启用开发数据库；因此该文件中的 MySQL 凭据不会被打入插件 JAR：

```powershell
.\gradlew.bat runServer -PspringProfile=dev
```

在 IntelliJ IDEA 中，建议创建或运行 Gradle 的 `runServer` 任务，并在本地开发运行配置的脚本参数中添加 `-PspringProfile=dev`。若通过 IntelliJ 直接启动 Paper 的 Java 进程，请在“运行/调试配置 → VM 选项”添加 `-Dspring.profiles.active=dev`，不要放在程序参数中。

需要断点调试时，使用 `runServer` 内置的 JDWP 配置。服务器会在 `5005` 端口暂停并等待调试器连接：

```powershell
.\gradlew.bat runServer --debug-jvm
```

随后在 IntelliJ IDEA 中新建“远程 JVM 调试”配置，主机填写 `127.0.0.1`、端口填写 `5005`，再附加到运行中的服务器进程。

## 部署到独立服务器

构建完成后可手动将 `build/libs/TeaNeko-Paper-<version>.jar` 复制到目标服务器的 `plugins/` 目录，并重启服务器。也可通过下列任务复制；`serverDirectory` 必须指向目标 Paper 服务端根目录：

```powershell
.\gradlew.bat deployPlugin -PserverDirectory="D:\Minecraft\Paper"
```

部署后请重启服务器。不要在生产服务器上使用 `/reload`，因为它可能遗留插件的类加载器、线程和任务状态。

## 当前边界

插件入口是 `org.zexnocs.teaneko.mc.TeaNekoPaperPlugin`，只负责 Paper 与核心处理器的生命周期以及默认 `config.yml`。功能初始化通过 `@TeaNekoInitializer` 扩展，Minecraft 指令通过 `@TeaNekoMCCommand` 扩展。Core 指令默认异步执行；Spring Bean 中若调用 Bukkit/Paper API，必须通过 `PaperCommandUtils` 切回服务器主线程。

## 测试

```powershell
.\gradlew.bat test
```

现有依赖旧 Spring Boot 应用入口的部分集成测试仍从默认测试源集中排除，源码继续保留。新增初始化器或指令基础设施时，应至少验证初始化顺序、必须组件失败传播、关闭逆序和指令注册回滚。
