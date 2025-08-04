import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.jvm.tasks.Jar
import java.time.Duration

plugins {
    java
    application
    id("com.gradleup.shadow") version "9.0.0-rc3"
    id("project-report")
}

group = "net.kyver"
version = "1.0-SNAPSHOT"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    withSourcesJar()
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("net.dv8tion:JDA:5.6.1")
    implementation("org.slf4j:slf4j-simple:2.0.17")
    implementation("org.yaml:snakeyaml:2.4")

    implementation("com.zaxxer:HikariCP:5.1.0")

    implementation("com.stripe:stripe-java:29.4.0")
    implementation("com.paypal.sdk:rest-api-sdk:1.14.0")

    implementation("com.google.zxing:core:3.5.3")
    implementation("com.google.zxing:javase:3.5.3")
}

application {
    mainClass.set("net.kyver.invoices.KyverInvoices")
}

tasks.jar {
    manifest {
        attributes(
            "Main-Class" to "net.kyver.invoices.KyverInvoices"
        )
    }
}

tasks.withType<ShadowJar> {
    archiveBaseName.set("server")
    archiveClassifier.set("")
    archiveVersion.set("")

    manifest {
        attributes(
            "Main-Class" to "net.kyver.invoices.KyverInvoices"
        )
    }


    relocate("org.slf4j", "net.kyver.shadow.org.slf4j")

    mergeServiceFiles()
}

tasks.named<Jar>("jar") {
    enabled = false
}

tasks.withType<JavaExec>().configureEach {
    timeout.set(Duration.ofMinutes(10))
}
