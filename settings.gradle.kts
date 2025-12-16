plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}
rootProject.name = "fuse"
include("fuse-processor")
include("fuse-annotations")
include("demo")
