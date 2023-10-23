import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
  kotlin("jvm")
  id("org.jetbrains.compose")
  id("fr.stardustenterprises.rust.importer") version "3.2.5"
}

group = "com.github.salaink"
version = "1.0-SNAPSHOT"

repositories {
  mavenCentral()
  maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
  google()
}

dependencies {
  // Note, if you develop a library, you should use compose.desktop.common.
  // compose.desktop.currentOs should be used in launcher-sourceSet
  // (in a separate module for demo project and in testMain).
  // With compose.desktop.common you will also lose @Preview functionality
  implementation(compose.desktop.currentOs)
  implementation("io.vertx:vertx-web:4.4.6")
  implementation("com.fasterxml.jackson.core:jackson-databind:2.15.3")
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.3")
  implementation("io.vertx:vertx-lang-kotlin-coroutines:4.4.6")
  implementation("org.slf4j:slf4j-api:2.0.9")
  runtimeOnly("ch.qos.logback:logback-classic:1.4.11")

  rust(project(":rust-library"))
  implementation("fr.stardustenterprises", "yanl", "0.7.4")

  testImplementation("org.junit.jupiter:junit-jupiter:5.8.1")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
}

tasks.withType<Test> {
  useJUnitPlatform()
}

rustImport {
  baseDir.set("/META-INF/natives")
  layout.set("hierarchical")
}

compose.desktop {
  application {
    mainClass = "com.github.salaink.tomcontrol.MainKt"

    nativeDistributions {
      targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
      packageName = "tomcontrol"
      packageVersion = "1.0.0"
    }
  }
}
