plugins {
    kotlin("jvm") version "2.2.0"
    id("com.squareup.wire") version "5.4.0" apply false
    kotlin("plugin.serialization") version "2.2.0"
}

group = "com.behnawwm"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}