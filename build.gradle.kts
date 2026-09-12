import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import java.util.*

plugins {
    java
    id("com.gradleup.shadow") version "9.6.1"
    id("xyz.jpenilla.run-paper") version "3.1.0"
}

group = "org.zexnocs"
val versionProperties = Properties().apply {
    file("version.properties").inputStream().use { load(it) }
}
version = versionProperties.getProperty("version")
description = "TeaNeko core utilities for Paper"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc"
    }
}

dependencies {
    // Paper 在运行时提供服务器 API，禁止将其打入插件 JAR。
    compileOnly("io.papermc.paper:paper-api:26.2.build.+")
    // Paper API 的 Maven Resolver 传递引入该依赖；升级以修复 CVE-2025-67030。
    compileOnly("org.codehaus.plexus:plexus-utils:3.6.1") {
        because("修复 Expand.extractFile 的目录穿越漏洞")
    }

    // 保留的 core 依赖 Spring 注解，并提供可选的数据库与 HTTP 能力。
    // 以下依赖会打入插件，使其不依赖服务端全局安装的 Spring。
    implementation(platform("org.springframework.boot:spring-boot-dependencies:4.1.0"))
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("tools.jackson.core:jackson-databind")
    implementation("tools.jackson.dataformat:jackson-dataformat-yaml")
    // 当 dev 或 prod 明确指定 H2 驱动时，提供对应的运行时实现。
    runtimeOnly("com.h2database:h2")
    runtimeOnly("com.mysql:mysql-connector-j")

    compileOnly("org.projectlombok:lombok:1.18.42")
    annotationProcessor("org.projectlombok:lombok:1.18.42")

    testImplementation(platform("org.junit:junit-bom:6.0.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")

    testImplementation("io.papermc.paper:paper-api:26.2.build.+")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

sourceSets {
    test {
        // 旧 Spring Boot 集成测试保留至后续 core 迁移时处理。
        // 它们依赖已移除的应用启动类，不属于当前插件骨架。
        java.exclude(
            "org/zexnocs/teanekocore/command/**",
            "org/zexnocs/teanekocore/database/**",
            "org/zexnocs/teanekocore/event/EventTest.java",
            "org/zexnocs/teanekocore/file_config/**",
            "org/zexnocs/teanekocore/task/**"
        )
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-parameters")
    options.release.set(25)
}

val generatedPluginResourcesDirectory = layout.buildDirectory.dir("generated/resources/pluginYml")
val generatedPluginYmlFile = generatedPluginResourcesDirectory.map { it.file("plugin.yml") }
val generatePluginYml = tasks.register<JavaExec>("generatePluginYml") {
    group = "构建"
    description = "扫描 @TeaNekoMCCommand 并自动生成 Paper plugin.yml。"
    dependsOn(tasks.compileJava)

    val mainClasses = sourceSets.main.get().output.classesDirs
    // 扫描类可能引用 Paper 的 compileOnly 类型，生成器必须同时拥有编译与运行时类路径。
    classpath = files(mainClasses, sourceSets.main.get().compileClasspath, configurations.runtimeClasspath)
    mainClass.set("org.zexnocs.teanekopapermc.build.TeaNekoPluginDescriptorGenerator")
    jvmArgs("-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
    inputs.files(mainClasses)
    inputs.property("version", project.version)
    outputs.file(generatedPluginYmlFile)

    doFirst {
        args = listOf(
            generatedPluginYmlFile.get().asFile.absolutePath,
            project.version.toString()
        ) + mainClasses.files.map { it.absolutePath }
    }
}

tasks.processResources {
    dependsOn(generatePluginYml)
    inputs.property("version", project.version)
    // 本地开发数据库配置可能包含凭据，禁止将其写入可部署的插件包。
    exclude("application-dev.properties")
    from(generatedPluginResourcesDirectory)
}

tasks.withType<ShadowJar>().configureEach {
    archiveClassifier.set("")
    // 构建期描述文件生成器不属于服务器运行时代码。
    exclude("org/zexnocs/teanekopapermc/build/**")
    // Spring Boot 依赖 META-INF 中的自动配置资源，不能重定位其包名。
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    mergeServiceFiles()
    append("META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports")
    filesNotMatching(listOf(
        "META-INF/services/**",
        "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports",
        // 保留 Kotlin 模块元数据给 Shadow 内置转换器处理，避免构建时静默丢弃。
        "META-INF/*.kotlin_module"
    )) {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }
}

tasks.jar {
    archiveClassifier.set("dev")
    exclude("org/zexnocs/teanekopapermc/build/**")
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}

val debugServerDirectory = layout.projectDirectory.dir("run")
val debugPluginDataDirectory = debugServerDirectory.dir("plugins/TeaNekoPaper")
val debugSpringProfileFile = debugPluginDataDirectory.file("spring-profile.properties")
val localSpringProfile = providers.gradleProperty("springProfile")
    .orElse("prod")
    .get()
check(localSpringProfile in setOf("dev", "prod")) {
    "springProfile 只能为 dev 或 prod；数据库类型请在对应 Profile 中通过驱动类配置。"
}
val acceptMinecraftEula = providers.gradleProperty("acceptMinecraftEula")
    .map(String::toBoolean)
    .orElse(false)

val prepareDebugServer = tasks.register("prepareDebugServer") {
    group = "Paper 本地服务器"
    description = "准备使用 25565 端口的本地 Paper 调试服务器。"

    // 每次启动前检查 EULA 状态，同时保留开发者手动修改的服务器配置。
    outputs.upToDateWhen { false }

    doLast {
        val runDirectory = debugServerDirectory.asFile
        val eulaFile = runDirectory.resolve("eula.txt")
        val serverPropertiesFile = runDirectory.resolve("server.properties")
        val eulaAccepted = eulaFile.isFile && eulaFile.readLines()
            .any { it.trim().equals("eula=true", ignoreCase = true) }

        check(eulaAccepted || acceptMinecraftEula.get()) {
            "首次启动本地 Minecraft 服务器前，请确认并接受 EULA：" +
                    ".\\gradlew.bat runServer -PacceptMinecraftEula=true"
        }

        runDirectory.mkdirs()
        if (!eulaAccepted) {
            eulaFile.writeText("eula=true\n")
        }
        if (!serverPropertiesFile.isFile) {
            serverPropertiesFile.writeText(
                """
                # TeaNeko Paper 本地调试服务器配置
                server-port=25565
                enable-rcon=false
                motd=TeaNeko Paper 本地调试服务器
                """.trimIndent() + "\n"
            )
        }
    }
}

val prepareLocalSpringConfiguration = tasks.register<Copy>("prepareLocalSpringConfiguration") {
    group = "Paper 本地服务器"
    description = "仅在手动启用 dev Profile 时复制未提交的本地数据库配置。"

    val configurationFile = file("src/main/resources/application-dev.properties")
    from(configurationFile)
    into(debugPluginDataDirectory)

    onlyIf { localSpringProfile == "dev" }

    doFirst {
        check(configurationFile.isFile) {
            "缺少本地 Profile 配置文件：${configurationFile.path}"
        }
    }
}

val prepareLocalSpringProfile = tasks.register("prepareLocalSpringProfile") {
    group = "Paper 本地服务器"
    description = "将本次本地启动选择的 Spring Profile 写入插件数据目录。"
    inputs.property("springProfile", localSpringProfile)
    outputs.file(debugSpringProfileFile)

    doLast {
        val profileFile = debugSpringProfileFile.asFile
        profileFile.parentFile.mkdirs()
        profileFile.writeText("spring.profiles.active=$localSpringProfile\n")
    }
}

tasks {
    runServer {
        minecraftVersion("26.2")
        runDirectory(debugServerDirectory.asFile)
        dependsOn(prepareDebugServer, prepareLocalSpringConfiguration, prepareLocalSpringProfile)
        // 将本次构建复制到 run/plugins 后再加载，避免服务器运行期间重新构建胖包破坏延迟类加载。
        legacyPluginLoading()
        jvmArgs(
            "-Xms4G",
            "-Xmx8G",
            "-Dfile.encoding=UTF-8",
            "-Dsun.stdout.encoding=UTF-8",
            "-Dsun.stderr.encoding=UTF-8")
    }
}

tasks.register<Copy>("deployPlugin") {
    group = "发布"
    description = "将插件 JAR 复制到 -PserverDirectory 指定服务器的 plugins 目录。"
    dependsOn(tasks.shadowJar)
    from(tasks.shadowJar)

    doFirst {
        val serverDirectory = providers.gradleProperty("serverDirectory").orNull
            ?: throw GradleException("请通过 -PserverDirectory 指定目标 Paper 服务器目录。")
        into(file(serverDirectory).resolve("plugins"))
    }
}

tasks.test {
    useJUnitPlatform()
}
