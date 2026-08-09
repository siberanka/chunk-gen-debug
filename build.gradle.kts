import java.security.MessageDigest

plugins {
    java
    jacoco
    id("org.cyclonedx.bom") version "3.4.0"
}

group = "com.siberanka"
version = "1.0.0"
val pluginVersion = version.toString()

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc"
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21-R0.1-SNAPSHOT")
    constraints {
        compileOnly("org.apache.commons:commons-lang3:3.18.0") {
            because("CVE-2025-48924 affects the older Paper API compile-classpath version")
        }
        compileOnly("org.codehaus.plexus:plexus-utils:3.6.1") {
            because("CVE-2025-67030 affects the older Paper API compile-classpath version")
        }
    }

    testImplementation(platform("org.junit:junit-bom:6.1.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

dependencyLocking {
    lockAllConfigurations()
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    withSourcesJar()
    withJavadocJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}

tasks.processResources {
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand("version" to pluginVersion)
    }
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.jar {
    archiveBaseName.set("chunk-gen-debug")
    manifest {
        attributes(
            "Implementation-Title" to "chunk-gen-debug",
            "Implementation-Version" to project.version,
            "Implementation-Vendor" to "siberanka"
        )
    }
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

tasks.check {
    dependsOn(tasks.cyclonedxBom, "verifyPaperApiChecksum")
}

tasks.register("verifyPaperApiChecksum") {
    group = "verification"
    description = "Rejects silent changes to the legacy 1.21 Paper snapshot artifact"
    inputs.files(configurations.compileClasspath)
    doLast {
        val candidates = inputs.files.files.filter {
            it.name == "paper-api-1.21-R0.1-SNAPSHOT.jar"
        }
        check(candidates.size == 1) { "Expected exactly one Paper 1.21 API JAR, found $candidates" }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(candidates.single().readBytes())
            .joinToString("") { "%02x".format(it) }
        check(digest == "f90d31da9af55197847cdb92bd06eed63dc0cdf338c520b0d25c698a07d118a6") {
            "Paper API checksum changed: $digest"
        }
    }
}
