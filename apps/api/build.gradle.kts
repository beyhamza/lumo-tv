import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

plugins {
    java
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.openapi.generator)
}

group = "tv.lumo"
version = "1.0.0"

java {
    // ADR 0005 — Java 25 (LTS). JEP 491 (no carrier pinning on `synchronized`,
    // JDK 24+) and finalised scoped values are the reasons virtual threads are
    // viable here at all.
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

// ---------------------------------------------------------------------------
// Contract-first (ADR 0001).
//
// packages/contracts/openapi.yaml is the source of truth. The server interfaces
// are generated from it into build/ and compiled with the rest of the code;
// controllers implement them. Because config/spring.yaml sets
// interfaceOnly + skipDefaultInterface, the interfaces carry no default methods:
// a controller that does not match the contract does not compile.
//
// The generator options come from the SAME file the npm pipeline uses
// (packages/contracts/config/spring.yaml). One config, one behaviour — a Gradle
// build and `npm run generate` cannot drift apart.
// ---------------------------------------------------------------------------

val contractsDir = rootProject.layout.projectDirectory.dir("../../packages/contracts")
val contractFile = contractsDir.file("openapi.yaml")
val generatedApiDir = layout.buildDirectory.dir("generated/openapi")

tasks.named<GenerateTask>("openApiGenerate") {
    configFile = contractsDir.file("config/spring.yaml").asFile.absolutePath
    inputSpec = contractFile.asFile.absolutePath
    outputDir = generatedApiDir.map { it.asFile.absolutePath }

    // Re-run whenever the contract or the generator options change, and only then.
    inputs.file(contractFile)
    inputs.file(contractsDir.file("config/spring.yaml"))
}

sourceSets {
    main {
        java {
            srcDir(generatedApiDir.map { it.dir("src/main/java") })
        }
    }
}

tasks.named("compileJava") {
    dependsOn(tasks.named("openApiGenerate"))
}

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:${libs.versions.springBoot.get()}"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Spring Security + JOSE. Access tokens are signed and verified with
    // Spring Security's own Nimbus support; no third-party JWT library.
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")

    // Persistence is plain JDBC through JdbcClient, not JPA. Two reasons:
    // ingestion replaces a whole catalogue in batched upserts, which JPA makes
    // painful; and the multi-tenant rule ("no query on a user_id table without a
    // user_id filter", docs/architecture.md §2) is auditable when the SQL is
    // written out rather than generated behind a repository proxy.
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    runtimeOnly("org.postgresql:postgresql")

    // Schema ownership: Liquibase, formatted-SQL changesets (ADR 0006).
    implementation("org.liquibase:liquibase-core")

    // Argon2id password hashing (AGENTS.md §5).
    implementation(libs.bouncycastle.provider)

    // Bounded, self-expiring maps behind the auth rate limiter.
    implementation(libs.caffeine)

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-parameters", "-Xlint:all,-processing,-serial"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    // Testcontainers starts a real PostgreSQL 16; give it room on a cold pull.
    systemProperty("junit.jupiter.execution.timeout.default", "5m")
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = false
    }
}
