[![official project](http://jb.gg/badges/official.svg)](https://github.com/JetBrains#jetbrains-on-github)

# Lightning KMP Application

Extending the `acinq/lightning-kmp-core` library to add application specific shared logic.

## What is it?

This repository contains a [Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform.html) library, targeting Android and iOS, that is deployable to [Maven Central](https://central.sonatype.com/) and to [JitPack](https://jitpack.io/).

Note that no other actions or tools usually required for the library development are set up, such as [tracking of backwards compatibility](https://kotlinlang.org/docs/jvm-api-guidelines-backward-compatibility.html#tools-designed-to-enforce-backward-compatibility), explicit API mode, licensing, contribution guideline, code of conduct and others. You can find a guide for best practices for designing Kotlin libraries [here](https://kotlinlang.org/docs/api-guidelines-introduction.html).

## Using the library

The library is available from [JitPack](https://jitpack.io/#kngako/lightning-kmp-app). Add the repository to the consuming project's `settings.gradle.kts` — alongside the other repositories, and *not* under `buildscript`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}
```

then depend on it:

```kotlin
kotlin {
    sourceSets.commonMain.dependencies {
        implementation("com.github.kngako.lightning-kmp-app:library:1.0.0")
    }
}
```

The version is any git tag, a commit hash, or `<branch>-SNAPSHOT` (for example `master-SNAPSHOT`) to track the head of a branch. JitPack builds a version the first time somebody asks for it, so the first resolution of a new tag takes a few minutes and everything after that is served from its cache. Progress and logs are at `https://jitpack.io/com/github/kngako/lightning-kmp-app/<tag>/build.log`.

`com.github.kngako.lightning-kmp-app` is the group id JitPack serves a multi-module repository under; `library` is this repository's one published module.

## Publishing

### JitPack

Tag a commit and push the tag — that is the whole release step:

```bash
git tag 1.0.1 && git push origin 1.0.1
```

The build itself is driven by [`jitpack.yml`](jitpack.yml), which pins the JDK and publishes under the coordinates JitPack will serve, `com.github.kngako.lightning-kmp-app:library:<tag>`. To reproduce that build locally before tagging:

```bash
./gradlew publishToMavenLocal --no-configuration-cache -PpublishGroupId=com.github.kngako.lightning-kmp-app -PpublishVersion=1.0.1
```

JitPack builds on Linux only. The iOS `.klib`s are therefore cross-compiled, which `kotlin.native.enableKlibsCrossCompilation` in [`gradle.properties`](gradle.properties) enables. That is enough to publish and to consume from an iOS project — the final iOS binary is still linked on the consumer's Mac. It would stop working if this library ever declared a cinterop of its own; those cannot be processed off a Mac, and publishing the iOS targets would have to move back to a macOS runner.

### Maven Central

Releasing on GitHub runs [`.github/workflows/publish.yml`](.github/workflows/publish.yml), which publishes `fr.acinq.phoenix:library` from a macOS runner. Signing is wired up only when a signing key is present, so the JitPack build (which has none) is unaffected.

## Guide

Please find the detailed guide [here](https://www.jetbrains.com/help/kotlin-multiplatform-dev/multiplatform-publish-libraries.html).

# Other resources
* [Publishing via the Central Portal](https://central.sonatype.org/publish-ea/publish-ea-guide/)
* [Gradle Maven Publish Plugin \- Publishing to Maven Central](https://vanniktech.github.io/gradle-maven-publish-plugin/central/)
