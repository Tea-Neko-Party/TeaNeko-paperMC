# TeaNeko Paper

TeaNeko 的 Paper 26.2 插件基础工程。原有的 `org.zexnocs.teanekocore` 源码被保留为可复用的核心库；原 Spring Boot 应用端（`org.zexnocs.teanekoapp`）不会被编译或打入插件。

## 环境

- Paper `26.2`
- Java 25
- Gradle Wrapper（已随项目提供）

## 构建与部署

```powershell
.\gradlew.bat shadowJar
```

将 `build/libs/TeaNeko-Paper-<version>.jar` 放入 Paper 服务器的 `plugins/` 目录后重启或启动服务器。插件启用后可执行 `/teaneko` 验证加载状态。

`shadowJar` 会将 core 所需的 Spring、Hibernate、Reactor 与 Jackson 依赖打入并重定位到 TeaNeko 自己的命名空间；Paper API 保持由服务器提供，因而不会被重复打包。

## 当前边界

插件入口是 `org.zexnocs.teanekopapermc.TeaNekoPaperPlugin`，负责 Paper 生命周期、默认 `config.yml` 和示例状态命令。它**不会**启动 Spring Boot：这样不会在 Minecraft 服务端意外开启 Web 服务、数据库自动配置或独立应用生命周期。

core 中原有的 Spring 注解、数据库和 HTTP 功能仍可继续迁移。接入某项服务时，应由插件的 `onEnable` 显式创建和关闭所需对象，并将涉及 Bukkit/Paper API 的操作切回服务器主线程。

## 测试

```powershell
.\gradlew.bat test
```

现有依赖旧 Spring Boot 应用入口的集成测试已从默认测试源集中排除，源码仍保留，待数据库和 Spring 容器迁移策略确定后再恢复。
