plugins {
    kotlin("jvm")
    id("com.google.devtools.ksp") version "2.2.0-2.0.2"
}

group = "com.behnawwm"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    implementation(project(":tapsi-featuretoggle-annotations"))
    implementation("com.google.devtools.ksp:symbol-processing-api:2.2.0-2.0.2")
    implementation("com.squareup:kotlinpoet:1.14.2")
    implementation("com.squareup:kotlinpoet-ksp:1.14.2")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}