# TeaNeko Paper

TeaNeko 的 Paper 26.2 插件工程。`org.zexnocs.teanekocore` 与 `org.zexnocs.teanekoapp` 会一起打入插件，并由 Paper 生命周期管理的 Spring Boot 应用上下文加载。

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

插件启用时，`TeaNekoPaperPlugin` 会委托 `TeaNekoCoreInjection` 创建 Servlet 模式的 Spring Boot 应用上下文，扫描 `org.zexnocs` 下的组件，并启动 `teanekoapp` 的 WebSocket 外部交互层。插件停用时会通过该管理器关闭上下文，进而停止 Web 服务、数据库连接池和 Spring 管理的任务。

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

插件入口是 `org.zexnocs.teanekopapermc.TeaNekoPaperPlugin`，负责 Paper 与 Spring Boot 的统一生命周期、默认 `config.yml` 和示例状态命令。Spring Bean 中若调用 Bukkit/Paper API，必须自行切回服务器主线程。

## 测试

```powershell
.\gradlew.bat test
```

现有依赖旧 Spring Boot 应用入口的集成测试已从默认测试源集中排除，源码仍保留，待数据库和 Spring 容器迁移策略确定后再恢复。
