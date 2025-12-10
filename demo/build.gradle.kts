plugins {
    kotlin("jvm")
    id("com.google.devtools.ksp") version "2.2.0-2.0.2"
    id("com.squareup.wire")
    kotlin("plugin.serialization")
}

group = "com.behnawwm"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("com.squareup.wire:wire-grpc-client:5.4.0")
    implementation("com.squareup.wire:wire-schema:5.4.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    ksp(project(":tapsi-grpc-processor"))
    ksp(project(":tapsi-featuretoggle-processor"))
    implementation(project(":tapsi-grpc-annotations"))
    implementation(project(":tapsi-featuretoggle-annotations"))

}
wire {
    kotlin {}
}
tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}