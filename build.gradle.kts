plugins {
    java
    application
    id("org.openjfx.javafxplugin") version "0.1.0"
}

group = "com.laurosantos"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

javafx {
    version = "26.0.2"
    modules = listOf("javafx.controls", "javafx.fxml", "javafx.media")
}

application {
    mainModule.set("com.laurosantos.clippr")
    mainClass.set("com.laurosantos.clippr.App")
}

dependencies {
    // UI Theme
    implementation("io.github.mkpaz:atlantafx-base:2.0.1")

    // Database & Persistence (Versões mais recentes e estáveis)
    implementation("org.postgresql:postgresql:42.7.7")
    implementation("org.hibernate.orm:hibernate-core:6.6.14.Final")
    implementation("org.jboss.logging:jboss-logging:3.5.3.Final")
    implementation("jakarta.enterprise:jakarta.enterprise.cdi-api:4.1.0")
    implementation("com.fasterxml:classmate:1.7.0")
    implementation("net.bytebuddy:byte-buddy:1.15.11")
    implementation("org.hibernate.common:hibernate-commons-annotations:7.0.3.Final")
    implementation("jakarta.xml.bind:jakarta.xml.bind-api:4.0.0")
    implementation("org.glassfish.jaxb:jaxb-runtime:4.0.2")
    implementation("jakarta.inject:jakarta.inject-api:2.0.1")
    implementation("com.zaxxer:HikariCP:6.3.0")
    implementation("jakarta.persistence:jakarta.persistence-api:3.1.0")

    // JSON & Serialization
    implementation("com.fasterxml.jackson.core:jackson-databind:2.19.0")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.19.0")

    // Logging & Observabilidade
    implementation("org.slf4j:slf4j-api:2.0.17")
    implementation("ch.qos.logback:logback-classic:1.5.18")

    // Testes Unitários & Integração
    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // Testcontainers (PostgreSQL para testes determinísticos)
    testImplementation(platform("org.testcontainers:testcontainers-bom:1.20.6"))
    testImplementation("org.testcontainers:testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf(
        "-Xlint:all",
        "-Xlint:-processing",
        "-Xlint:-path",
        "-Xlint:-requires-automatic"
    ))
}

tasks.test {
    useJUnitPlatform()
    maxParallelForks = 1
    val podmanSock = File("/run/user/1000/podman/podman.sock")
    if (podmanSock.exists() && System.getenv("DOCKER_HOST") == null) {
        environment("DOCKER_HOST", "unix://${podmanSock.absolutePath}")
    }
    environment("TESTCONTAINERS_RYUK_DISABLED", "true")
}