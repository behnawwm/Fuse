plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}
rootProject.name = "KspPlayground-minimal"
include("tapsi-grpc-processor")
include("tapsi-featuretoggle-processor")
include("tapsi-grpc-annotations")
include("tapsi-featuretoggle-annotations")
include("demo")
