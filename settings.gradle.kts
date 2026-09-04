pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "lightning-kmp-app"
include(":library")

// Build lightning-kmp from the `experimental/lightning-kmp` submodule (its `threshold` branch, which
// carries the FROST/prefractal signers) rather than resolving it from Maven Central. The submodule
// publishes under the same coordinates the `api` dependency in library/build.gradle.kts names, so the
// substitution below redirects it to the included build's project; the version there is never resolved.
//
// That build includes its own bitcoin-kmp submodule, which in turn includes secp256k1-kmp, which
// compiles the C library from a secp256k1-zkp fork. So `fr.acinq.secp256k1:*` and `fr.acinq.bitcoin:*`
// are substituted too -- by rules those builds declare, which apply across the whole composite -- and
// no repository serves any of them. Nothing here needs to name mavenLocal(): that was only required
// while the submodule tracked `master`, whose jvm source set resolved hand-installed
// `0.23.0-iceberg` secp256k1 artifacts out of the local maven repository.
includeBuild("experimental/lightning-kmp") {
    dependencySubstitution {
        substitute(module("fr.acinq.lightning:lightning-kmp-core"))
            .using(project(":lightning-kmp-core"))
    }
}
