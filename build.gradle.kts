plugins {
    id("java")
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
    useJUnitPlatform()
    jvmArgs("-javaagent:${mockitoAgent.asPath}", "-Xshare:off")
}
