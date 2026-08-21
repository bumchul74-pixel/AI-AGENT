plugins {
    id("java")
    id("jacoco")
    id("org.springframework.boot") version "4.0.6"
    id("io.spring.dependency-management") version "1.1.7"
}


group = "com.hanwha.ai"
version = "1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

val mockitoAgent = configurations.create("mockitoAgent") {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.ai:spring-ai-bom:2.0.0")
    }
}

dependencies {
    implementation("org.apache.poi:poi-ooxml:5.5.1")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-json")
    implementation("org.springframework.boot:spring-boot-starter-data-neo4j")
    implementation("org.springframework.ai:spring-ai-starter-mcp-client")
    implementation("org.mybatis.spring.boot:mybatis-spring-boot-starter:4.0.0")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.github.javaparser:javaparser-core:3.26.4")
    runtimeOnly("org.postgresql:postgresql")
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    mockitoAgent("org.mockito:mockito-core") {
        isTransitive = false
    }
}

tasks.test {
    useJUnitPlatform { excludeTags("integration", "live") }
    jvmArgs("-javaagent:${mockitoAgent.asPath}", "-Xshare:off")
    finalizedBy(tasks.jacocoTestReport)
}

jacoco { toolVersion = "0.8.13" }

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports { xml.required = true; html.required = true }
}

val integrationTest by tasks.registering(Test::class) {
    group = "verification"
    description = "Runs tests tagged integration without live external calls."
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("integration"); excludeTags("live") }
    jvmArgs("-javaagent:${mockitoAgent.asPath}", "-Xshare:off")
    shouldRunAfter(tasks.test)
}

val liveTest by tasks.registering(Test::class) {
    group = "verification"
    description = "Runs explicitly authorized tests tagged live."
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("live") }
    jvmArgs("-javaagent:${mockitoAgent.asPath}", "-Xshare:off")
    shouldRunAfter(integrationTest)
}

val frontendCheck by tasks.registering(Exec::class) {
    group = "verification"
    description = "Builds the React/Vite frontend."
    workingDir(layout.projectDirectory.dir("frontend"))
    commandLine(if (System.getProperty("os.name").lowercase().contains("windows")) "npm.cmd" else "npm", "run", "build")
}

val requiredHarnessDocuments = listOf(
    "AGENTS.md", "ARCHITECTURE.md", "CONTRIBUTING.md", "docs/DESIGN.md", "docs/FRONTEND.md",
    "docs/PLANS.md", "docs/PRODUCT_SENSE.md", "docs/QUALITY_SCORE.md", "docs/RELIABILITY.md",
    "docs/SECURITY.md", "docs/design-docs/core-beliefs.md", "docs/design-docs/index.md",
    "docs/design-docs/decisions/index.md", "docs/product-specs/index.md", "docs/references/index.md",
    "docs/exec-plans/active/README.md", "docs/exec-plans/completed/README.md", "docs/exec-plans/tech-debt-tracker.md"
)

val validateHarnessDocs by tasks.registering {
    group = "verification"
    description = "Checks required harness documents and local Markdown links."
    inputs.files(requiredHarnessDocuments.map(layout.projectDirectory::file))
    inputs.files(fileTree(layout.projectDirectory) { include("**/*.md"); exclude("build/**", "frontend/node_modules/**") })
    doLast {
        val missing = requiredHarnessDocuments.filterNot { layout.projectDirectory.file(it).asFile.isFile }
        if (missing.isNotEmpty()) throw GradleException("Missing required harness documents: ${missing.joinToString()}")
        val linkPattern = Regex("""\[[^\]]+]\(([^)]+)\)""")
        val broken = mutableListOf<String>()
        fileTree(layout.projectDirectory) {
            include("**/*.md")
            exclude("build/**", "frontend/node_modules/**")
        }.forEach { markdown ->
            markdown.readLines().forEachIndexed { index, line ->
                linkPattern.findAll(line).forEach { match ->
                    val rawTarget = match.groupValues[1].trim().removePrefix("<").removeSuffix(">")
                    val target = rawTarget.substringBefore('#').substringBefore(' ')
                    if (target.isNotBlank()
                        && !target.contains("://")
                        && !target.startsWith("mailto:")
                        && !target.startsWith("#")
                        && !markdown.parentFile.resolve(target).normalize().exists()
                    ) {
                        broken += "${markdown.relativeTo(projectDir)}:${index + 1} -> $rawTarget"
                    }
                }
            }
        }
        if (broken.isNotEmpty()) throw GradleException("Broken local Markdown links:\n${broken.joinToString("\n")}")
    }
}

val doctor by tasks.registering {
    group = "verification"
    description = "Checks Java and repository entry points."
    doLast {
        val failures = mutableListOf<String>()
        if (JavaVersion.current() < JavaVersion.VERSION_21) failures += "Java 21 or newer is required; current version is ${JavaVersion.current()}."
        listOf("gradlew", "gradlew.bat", "gradle/wrapper/gradle-wrapper.properties", "frontend/package-lock.json")
            .filterNot { layout.projectDirectory.file(it).asFile.exists() }
            .forEach { failures += "Required file is missing: $it" }
        if (failures.isNotEmpty()) throw GradleException("Environment doctor found ${failures.size} problem(s):\n${failures.joinToString("\n")}")
        logger.lifecycle("Environment doctor passed (Java ${JavaVersion.current()}).")
    }
}

tasks.check { dependsOn(validateHarnessDocs) }
integrationTest { mustRunAfter(tasks.check) }
frontendCheck { mustRunAfter(integrationTest) }

tasks.register("verifyAll") {
    group = "verification"
    description = "Runs the deterministic backend, integration, frontend, and documentation gate."
    dependsOn(doctor, tasks.check, integrationTest, frontendCheck)
}
