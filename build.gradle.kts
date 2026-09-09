import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import java.util.Properties

plugins {
    java
    id("com.gradleup.shadow") version "9.6.1"
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
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    implementation("org.springframework:spring-context")
    implementation("org.springframework:spring-orm")
    implementation("org.springframework:spring-webflux")
    implementation("org.springframework.data:spring-data-jpa")
    implementation("org.hibernate.orm:hibernate-core")
    implementation("tools.jackson.core:jackson-databind")
    implementation("tools.jackson.dataformat:jackson-dataformat-yaml")

    compileOnly("org.projectlombok:lombok:1.18.42")
    annotationProcessor("org.projectlombok:lombok:1.18.42")

    testImplementation(platform("org.junit:junit-bom:6.0.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

sourceSets {
    main {
        // 原 Spring Boot 应用端不属于插件产物。
        java.exclude("org/zexnocs/teanekoapp/**")
    }
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

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
    exclude("application*.properties", "templates/**")
}

tasks.withType<ShadowJar>().configureEach {
    archiveClassifier.set("")
    // 隔离 core 依赖，避免与其他插件携带的不兼容版本冲突。
    relocate("org.springframework", "org.zexnocs.teanekopapermc.libs.springframework")
    relocate("org.hibernate", "org.zexnocs.teanekopapermc.libs.hibernate")
    relocate("reactor", "org.zexnocs.teanekopapermc.libs.reactor")
    relocate("tools.jackson", "org.zexnocs.teanekopapermc.libs.jackson")
}

tasks.jar {
    archiveClassifier.set("dev")
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}

tasks.test {
    useJUnitPlatform()
}
