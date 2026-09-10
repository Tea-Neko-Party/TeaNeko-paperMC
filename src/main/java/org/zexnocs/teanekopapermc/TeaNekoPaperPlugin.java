package org.zexnocs.teanekopapermc;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.DefaultResourceLoader;
import org.zexnocs.teanekoapp.TeaNekoAppApplication;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * TeaNeko Paper 插件入口，负责管理插件生命周期与基础状态指令。
 * <p>
 * 该类创建 Spring Boot 应用上下文，使 core 服务与应用交互层在 Paper 生命周期内运行。
 *
 * @author zExNocs
 * @date 2026/09/10
 * @since paperMC-1.0.0alpha
 */
public final class TeaNekoPaperPlugin extends JavaPlugin {
    private static final String SPRING_PROFILE_PROPERTY = "spring.profiles.active";
    private static final String DEFAULT_PROFILE = "prod";

    private ConfigurableApplicationContext applicationContext;

    /**
     * 启用插件、初始化默认配置并启动 Spring Boot 应用上下文。
     * <p>
     * 应用上下文以 Servlet 模式启动，以便加载 {@code teanekocore} 的服务、
     * {@code teanekoapp} 的客户端扫描器及 WebSocket 外部交互端点。
     */
    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResourceIfAbsent("application.properties");
        saveResourceIfAbsent("application-prod.properties");

        try {
            applicationContext = startApplicationContext();
            getLogger().info("TeaNeko Paper 插件与 Spring Boot 应用上下文已启用。");
        } catch (RuntimeException exception) {
            closeApplicationContext();
            throw exception;
        }
    }

    /**
     * 使用插件类加载器创建 Spring Boot 应用上下文。
     * <p>
     * Paper 的服务器线程上下文类加载器不会暴露插件 JAR。启动期间必须显式指定插件类加载器，
     * 否则 Spring Boot 无法读取 {@code META-INF/spring} 下的自动配置资源。
     *
     * @return 已完成组件扫描和自动配置的应用上下文
     * @see DefaultResourceLoader
     */
    private ConfigurableApplicationContext startApplicationContext() {
        ClassLoader pluginClassLoader = getClass().getClassLoader();
        Thread currentThread = Thread.currentThread();
        ClassLoader originalClassLoader = currentThread.getContextClassLoader();
        String activeProfile = getActiveProfile();
        String originalProfile = System.getProperty(SPRING_PROFILE_PROPERTY);
        Properties springProperties = loadSpringProperties(pluginClassLoader, activeProfile);

        currentThread.setContextClassLoader(pluginClassLoader);
        System.setProperty(SPRING_PROFILE_PROPERTY, activeProfile);
        try {
            getLogger().info("正在以 Spring Profile " + activeProfile + " 启动应用上下文。");
            return new SpringApplicationBuilder(TeaNekoAppApplication.class)
                    .resourceLoader(new DefaultResourceLoader(pluginClassLoader))
                    .properties(springProperties)
                    .web(WebApplicationType.SERVLET)
                    .registerShutdownHook(false)
                    .run(
                            "--spring.main.register-shutdown-hook=false",
                            "--spring.main.banner-mode=off",
                            "--teaneko.paper.data-directory=" + getDataFolder().getAbsolutePath().replace('\\', '/'),
                            "--teaneko.plugin.version=" + getPluginMeta().getVersion()
                    );
        } finally {
            if (originalProfile == null) {
                System.clearProperty(SPRING_PROFILE_PROPERTY);
            } else {
                System.setProperty(SPRING_PROFILE_PROPERTY, originalProfile);
            }
            currentThread.setContextClassLoader(originalClassLoader);
        }
    }

    /**
     * 停用插件，先关闭 Spring Boot 应用上下文，再取消 Bukkit 调度任务。
     * <p>
     * Spring 上下文关闭时会依次停止嵌入式 Web 服务、JPA 连接池、定时任务和其余 Bean。
     */
    @Override
    public void onDisable() {
        closeApplicationContext();
        getServer().getScheduler().cancelTasks(this);
        getLogger().info("TeaNeko Paper 插件已停用。");
    }

    /**
     * 获取本次 Spring Boot 启动应使用的活动 Profile。
     * <p>
     * 独立部署优先采用 JVM 系统属性；本地 Gradle 调试则使用数据目录中的 Profile 标记文件。
     * 标记文件由 {@code prepareLocalSpringProfile} 任务生成，避免依赖 run-paper 对 JVM 参数的转发行为。
     * 未提供有效值时始终回退到 {@code prod}，以确保构建产物的默认行为面向生产配置。
     *
     * @return {@code dev} 或 {@code prod}
     */
    private String getActiveProfile() {
        String systemProfile = System.getProperty(SPRING_PROFILE_PROPERTY);
        if (isSupportedProfile(systemProfile)) {
            return systemProfile;
        }

        File profileFile = new File(getDataFolder(), "spring-profile.properties");
        if (!profileFile.isFile()) {
            return DEFAULT_PROFILE;
        }

        Properties properties = new Properties();
        try (InputStream inputStream = new FileInputStream(profileFile)) {
            properties.load(inputStream);
        } catch (IOException exception) {
            getLogger().warning("无法读取本地 Spring Profile 标记，将使用 prod：" + exception.getMessage());
            return DEFAULT_PROFILE;
        }

        String profile = properties.getProperty(SPRING_PROFILE_PROPERTY);
        if (isSupportedProfile(profile)) {
            return profile;
        }

        getLogger().warning("本地 Spring Profile 标记无效，将使用 prod。");
        return DEFAULT_PROFILE;
    }

    /**
     * 判断指定 Profile 是否为插件允许的数据库环境。
     *
     * @param profile 待验证的 Profile 名称
     * @return 为 {@code dev} 或 {@code prod} 时返回 {@code true}
     */
    private boolean isSupportedProfile(String profile) {
        return "dev".equals(profile) || DEFAULT_PROFILE.equals(profile);
    }

    /**
     * 仅在目标文件不存在时释放插件内置资源，避免正常重启产生重复警告。
     *
     * @param resourcePath 插件资源路径
     */
    private void saveResourceIfAbsent(String resourcePath) {
        File targetFile = new File(getDataFolder(), resourcePath);
        if (!targetFile.isFile()) {
            saveResource(resourcePath, false);
        }
    }

    /**
     * 按基础配置优先、Profile 配置覆盖，以及外部文件覆盖插件资源的顺序合并 Spring 属性。
     * <p>
     * Paper 插件类加载环境下，Spring Boot 无法稳定发现插件数据目录中的配置文件，
     * 因此在创建应用上下文前显式合并配置，避免静默回退到随机 H2 内存数据库。
     *
     * @param pluginClassLoader 插件类加载器
     * @param activeProfile 当前活动 Profile
     * @return 已合并的 Spring 属性
     */
    private Properties loadSpringProperties(ClassLoader pluginClassLoader, String activeProfile) {
        Properties properties = new Properties();
        if (!loadClasspathProperties(properties, pluginClassLoader, "application.properties")) {
            throw new IllegalStateException("插件中缺少基础 Spring 配置 application.properties。");
        }
        loadFileProperties(properties, new File(getDataFolder(), "application.properties"));

        String profileFileName = "application-" + activeProfile + ".properties";
        boolean classpathProfileLoaded = loadClasspathProperties(properties, pluginClassLoader, profileFileName);
        File externalProfileFile = new File(getDataFolder(), profileFileName);
        boolean externalProfileLoaded = loadFileProperties(properties, externalProfileFile);
        if (!classpathProfileLoaded && !externalProfileLoaded) {
            throw new IllegalStateException("找不到当前 Spring Profile 配置：" + profileFileName);
        }
        return properties;
    }

    /**
     * 从插件类路径读取属性，并覆盖目标属性中的同名配置。
     *
     * @param target 目标属性
     * @param pluginClassLoader 插件类加载器
     * @param resourceName 资源名称
     * @return 找到并加载资源时返回 {@code true}
     */
    private boolean loadClasspathProperties(Properties target, ClassLoader pluginClassLoader,
                                            String resourceName) {
        InputStream inputStream = pluginClassLoader.getResourceAsStream(resourceName);
        if (inputStream == null) {
            return false;
        }
        loadProperties(target, inputStream, "插件资源 " + resourceName);
        return true;
    }

    /**
     * 从插件数据目录读取属性，并覆盖目标属性中的同名配置。
     *
     * @param target 目标属性
     * @param file 配置文件
     * @return 找到并加载文件时返回 {@code true}
     */
    private boolean loadFileProperties(Properties target, File file) {
        if (!file.isFile()) {
            return false;
        }
        try {
            loadProperties(target, new FileInputStream(file), "外部文件 " + file.getName());
            return true;
        } catch (IOException exception) {
            throw new IllegalStateException("无法打开 Spring 配置文件：" + file.getName(), exception);
        }
    }

    /**
     * 使用 UTF-8 编码加载属性流，并负责关闭底层输入流。
     *
     * @param target 目标属性
     * @param inputStream 属性输入流
     * @param sourceDescription 配置来源描述
     * @throws IllegalStateException 属性无法读取时抛出
     */
    private void loadProperties(Properties target, InputStream inputStream, String sourceDescription) {
        try (InputStream stream = inputStream;
             Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            target.load(reader);
        } catch (IOException exception) {
            throw new IllegalStateException("无法读取 Spring 配置：" + sourceDescription, exception);
        }
    }

    /**
     * 安全关闭已创建的 Spring Boot 应用上下文。
     */
    private void closeApplicationContext() {
        if (applicationContext != null) {
            applicationContext.close();
            applicationContext = null;
        }
    }

    /**
     * 处理 TeaNeko 的基础状态指令。
     *
     * @param sender 指令发送者
     * @param command 被执行的 Bukkit 指令
     * @param label 实际使用的指令别名
     * @param args 指令参数
     * @return 已处理时返回 {@code true}，否则返回 {@code false}
     */
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String @NotNull [] args) {
        if (!command.getName().equalsIgnoreCase("teaneko")) {
            return false;
        }

        sender.sendRichMessage("<green>TeaNeko Paper</green> <gray>v" + getPluginMeta().getVersion()
                + " — core 已就绪。</gray>");
        return true;
    }
}
