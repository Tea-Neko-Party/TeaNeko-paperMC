package org.zexnocs.teanekopapermc.core;

import org.bukkit.plugin.java.JavaPlugin;
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
import java.util.Objects;
import java.util.Properties;

/**
 * 管理 TeaNeko Core 的 Spring Boot 应用上下文及其 Paper 生命周期适配。
 * <p>
 * 该类负责释放 Spring 配置、选择活动 Profile、合并配置属性、切换插件类加载器，
 * 并在插件停用时关闭 Spring 管理的 Web 服务、数据库连接池和任务资源。
 *
 * @author zExNocs
 * @date 2026/09/10
 * @since paperMC-1.0.0alpha
 * @see TeaNekoAppApplication
 */
public final class TeaNekoCoreInjection implements AutoCloseable {
    private static final String SPRING_PROFILE_PROPERTY = "spring.profiles.active";
    private static final String DEFAULT_PROFILE = "prod";

    private final JavaPlugin plugin;
    private ConfigurableApplicationContext applicationContext;

    /**
     * 创建绑定到指定 Paper 插件的 Spring Boot 上下文管理器。
     *
     * @param plugin 提供数据目录、日志、版本和类加载器的 Paper 插件
     * @throws NullPointerException 插件实例为空时抛出
     */
    public TeaNekoCoreInjection(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "Paper 插件不能为空。");
    }

    /**
     * 初始化 Spring 配置并启动应用上下文。
     *
     * @throws IllegalStateException 上下文已经启动或所需配置无法加载时抛出
     */
    public void start() {
        if (applicationContext != null) {
            throw new IllegalStateException("Spring Boot 应用上下文已经启动。");
        }

        saveResourceIfAbsent("application.properties");
        saveResourceIfAbsent("application-prod.properties");
        applicationContext = startApplicationContext();
    }

    /**
     * 使用插件类加载器创建 Spring Boot 应用上下文。
     * <p>
     * Paper 的服务器线程上下文类加载器不会暴露插件 JAR。启动期间必须显式指定插件类加载器，
     * 否则 Spring Boot 无法读取 {@code META-INF/spring} 下的自动配置资源。
     *
     * @return 已启动的 Spring Boot 应用上下文
     * @see DefaultResourceLoader
     */
    private ConfigurableApplicationContext startApplicationContext() {
        ClassLoader pluginClassLoader = plugin.getClass().getClassLoader();
        Thread currentThread = Thread.currentThread();
        ClassLoader originalClassLoader = currentThread.getContextClassLoader();
        String activeProfile = getActiveProfile();
        String originalProfile = System.getProperty(SPRING_PROFILE_PROPERTY);
        Properties springProperties = loadSpringProperties(pluginClassLoader, activeProfile);

        currentThread.setContextClassLoader(pluginClassLoader);
        System.setProperty(SPRING_PROFILE_PROPERTY, activeProfile);
        try {
            plugin.getLogger().info("正在以 Spring Profile " + activeProfile + " 启动应用上下文。");
            return new SpringApplicationBuilder(TeaNekoAppApplication.class)
                    .resourceLoader(new DefaultResourceLoader(pluginClassLoader))
                    .properties(springProperties)
                    .web(WebApplicationType.SERVLET)
                    .registerShutdownHook(false)
                    .run(
                            "--spring.main.register-shutdown-hook=false",
                            "--spring.main.banner-mode=off",
                            "--teaneko.paper.data-directory=" + getDataFolderPath(),
                            "--teaneko.plugin.version=" + plugin.getPluginMeta().getVersion()
                    );
        } finally {
            restoreSystemProfile(originalProfile);
            currentThread.setContextClassLoader(originalClassLoader);
        }
    }

    /**
     * 获取本次 Spring Boot 启动应使用的活动 Profile。
     * <p>
     * 独立部署优先采用 JVM 系统属性；本地 Gradle 调试则使用插件数据目录中的 Profile 标记。
     * 未提供有效值时回退到 {@code prod}，确保默认行为面向生产环境。
     *
     * @return {@code dev} 或 {@code prod}
     */
    private String getActiveProfile() {
        String systemProfile = System.getProperty(SPRING_PROFILE_PROPERTY);
        if (isSupportedProfile(systemProfile)) {
            return systemProfile;
        }

        File profileFile = new File(plugin.getDataFolder(), "spring-profile.properties");
        if (!profileFile.isFile()) {
            return DEFAULT_PROFILE;
        }

        Properties properties = new Properties();
        try (InputStream inputStream = new FileInputStream(profileFile)) {
            properties.load(inputStream);
        } catch (IOException exception) {
            plugin.getLogger().warning("无法读取本地 Spring Profile 标记，将使用 prod："
                    + exception.getMessage());
            return DEFAULT_PROFILE;
        }

        String profile = properties.getProperty(SPRING_PROFILE_PROPERTY);
        if (isSupportedProfile(profile)) {
            return profile;
        }

        plugin.getLogger().warning("本地 Spring Profile 标记无效，将使用 prod。");
        return DEFAULT_PROFILE;
    }

    /**
     * 判断指定 Profile 是否为插件允许的运行环境。
     *
     * @param profile 待验证的 Profile 名称
     * @return 为 {@code dev} 或 {@code prod} 时返回 {@code true}
     */
    private boolean isSupportedProfile(String profile) {
        return "dev".equals(profile) || DEFAULT_PROFILE.equals(profile);
    }

    /**
     * 仅在目标文件不存在时释放插件内置资源。
     *
     * @param resourcePath 插件资源路径
     */
    private void saveResourceIfAbsent(String resourcePath) {
        File targetFile = new File(plugin.getDataFolder(), resourcePath);
        if (!targetFile.isFile()) {
            plugin.saveResource(resourcePath, false);
        }
    }

    /**
     * 按基础配置优先、Profile 配置覆盖，以及外部文件覆盖插件资源的顺序合并 Spring 属性。
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
        loadFileProperties(properties, new File(plugin.getDataFolder(), "application.properties"));

        String profileFileName = "application-" + activeProfile + ".properties";
        boolean classpathProfileLoaded = loadClasspathProperties(properties, pluginClassLoader, profileFileName);
        boolean externalProfileLoaded = loadFileProperties(
                properties,
                new File(plugin.getDataFolder(), profileFileName)
        );
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
     * 获取可安全写入 Spring 参数的插件数据目录路径。
     *
     * @return 使用正斜杠分隔的数据目录绝对路径
     */
    private String getDataFolderPath() {
        return plugin.getDataFolder().getAbsolutePath().replace('\\', '/');
    }

    /**
     * 恢复启动前的 Spring Profile 系统属性，避免影响同一 Paper JVM 中的其他插件。
     *
     * @param originalProfile 启动前的属性值
     */
    private void restoreSystemProfile(String originalProfile) {
        if (originalProfile == null) {
            System.clearProperty(SPRING_PROFILE_PROPERTY);
        } else {
            System.setProperty(SPRING_PROFILE_PROPERTY, originalProfile);
        }
    }

    /**
     * 关闭 Spring Boot 应用上下文。
     * <p>
     * 该方法可重复调用；上下文关闭后会释放 Web 服务、数据库连接池与 Spring 管理的任务。
     */
    @Override
    public void close() {
        if (applicationContext != null) {
            applicationContext.close();
            applicationContext = null;
        }
    }
}
