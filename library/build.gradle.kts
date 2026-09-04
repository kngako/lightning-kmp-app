import org.gradle.internal.declarativedsl.defaults.isDefaultsConfiguringCall
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinPluginSerialization)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.vanniktech.mavenPublish)
}

group = "fr.acinq.phoenix"
version = "1.0.0"

kotlin {
//    jvm()
    android {
        namespace = "org.jetbrains.kotlinx.multiplatform.library.template"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        withJava() // enable java compilation support
        withHostTestBuilder {}.configure {
            isIncludeAndroidResources = true
        }
        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }.configure {
            // without an instrumentation runner the device tests cannot be executed at all
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }

    }
    // Declared only on a mac, because only there can they resolve. lightning-kmp gates its own apple
    // targets behind `currentOs.isMacOsX`, and secp256k1-kmp further down the chain declares a
    // libsecp256k1 cinterop, which makes gradle switch off klib cross compilation for apple targets.
    // So on a linux/windows host nothing in the composite offers an ios variant of
    // lightning-kmp-core, and declaring these targets anyway leaves every ios compilation unable to
    // resolve it. That is not just a warning: it fails the IDE's
    // `transformAppleMainCInteropDependenciesMetadataForIde` sync task, so the project will not
    // import at all. Building an ios binary needs a mac regardless -- this only stops a host that
    // cannot do it from pretending it can.
    if (org.gradle.internal.os.OperatingSystem.current().isMacOsX) {
        iosArm64()
        iosSimulatorArm64()
    }
//    linuxX64()

    sourceSets {
        androidMain.dependencies {
            implementation(libs.androidx.work.runtime.ktx)

            implementation(libs.sqldelight.android.driver)
            implementation(libs.androidx.exifinterface)

        }
        // note: the android target of `com.android.kotlin.multiplatform.library` names its host (unit) test
        // source set `androidHostTest`, not `androidUnitTest`. Declaring these on `androidUnitTest` left them
        // off every compilation, so Robolectric was never actually on the test classpath.
        getByName("androidHostTest").dependencies {
            // Robolectric is a real jvm, so these tests need the secp256k1 JNI natives -- but the
            // android host-test classpath drops them. lightning-kmp-core pulls
            // `secp256k1-kmp-jni-jvm`, which the composite build substitutes for secp256k1-kmp's
            // `:jni:jvm:all`; that project is an empty aggregator that re-exports the per-OS native
            // projects via `api`, and those are plain `java-library` variants that AGP does not
            // select for an android consumer. The dependency stays in the graph while contributing
            // no artifact, so `Secp256k1` fails to initialise at the first crypto call.
            //
            // Naming the per-OS project directly gets a variant AGP will take. It also carries the
            // loader (`:jni:jvm`) and `NativeSecp256k1` (`:jni`) transitively, so this one line is
            // the whole native stack. Picked by host OS exactly as bitcoin-kmp picks its own.
            // The version is never resolved -- substitution matches on group:name.
            when {
                org.gradle.internal.os.OperatingSystem.current().isLinux ->
                    implementation("fr.acinq.secp256k1:secp256k1-kmp-jni-jvm-linux:0.24.0")
                org.gradle.internal.os.OperatingSystem.current().isMacOsX ->
                    implementation("fr.acinq.secp256k1:secp256k1-kmp-jni-jvm-darwin:0.24.0")
                org.gradle.internal.os.OperatingSystem.current().isWindows ->
                    implementation("fr.acinq.secp256k1:secp256k1-kmp-jni-jvm-mingw:0.24.0")
            }
            implementation(libs.robolectric)

            implementation(libs.androidx.test.core.ktx)
            implementation(libs.androidx.test.ext.junit)
        }
        // Robolectric itself is host-only -- it substitutes for a device, so it cannot run on one. What is
        // shared is the runner annotation: @RunWith(AndroidJUnit4::class) delegates to RobolectricTestRunner
        // on the jvm and to AndroidJUnit4ClassRunner on a device, so one test class works in both places.
        getByName("androidDeviceTest").dependencies {
            implementation(libs.androidx.test.core.ktx)
            implementation(libs.androidx.test.ext.junit)
            implementation(libs.androidx.test.runner)

            // lightning-kmp-core publishes no android variant, so the android target resolves it to the jvm
            // one, which brings the desktop secp256k1 jni binding -- and agp strips that .so out of the apk.
            // This artifact supplies `NativeSecp256k1AndroidLoader`, which `Secp256k1` tries before the jvm
            // loader, along with the .so for every device abi. Without it every test touching bitcoin crypto
            // dies on "Could not load native Secp256k1 JNI library".
            implementation(libs.secp256k1.kmp.jni.android)
        }
        commonMain.dependencies {
            implementation(libs.androidx.datastore)
            implementation(libs.androidx.datastore.preferences)

            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation (libs.compose.material.icons.core)
            implementation (libs.compose.material.icons.extended)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)



            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.serialization.cbor)

            // Comes from the experimental/lightning-kmp submodule, not from a repository: the
            // includeBuild in settings.gradle.kts substitutes these coordinates for that build's
            // :lightning-kmp-core project. Named as a bare module rather than through the version
            // catalog because no repository serves this: gradle has no syntax for a project
            // dependency that crosses a build boundary, so the coordinates are what the
            // substitution rule matches on. Dropping that includeBuild leaves this unresolvable.
            //
            // The version is never resolved -- substitution matches on group:name alone -- but it
            // is what gets recorded for this `api` dependency in the published pom and module
            // metadata, so it tracks the submodule's own version (experimental/lightning-kmp's
            // gradle.properties). Omitting it emits metadata with no version at all, which no
            // consumer can resolve.
            api("fr.acinq.lightning:lightning-kmp-core:1.13.1-SNAPSHOT")

            implementation(libs.navigation.compose)

            implementation(libs.human.readable)

            implementation(libs.qrose)

            implementation("com.ionspin.kotlin:bignum:0.3.10")

            implementation("no.synth:kmp-zip:0.8.0")
            implementation("no.synth:kmp-zip-okio:0.8.0")
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines.extensions)
            implementation(libs.squareup.okio)
        }

        commonTest.dependencies {
            implementation(kotlin("test-common"))
            implementation(kotlin("test-annotations-common"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
            implementation(libs.kotlin.test)
            implementation("io.ktor:ktor-client-mock:3.1.0")
            implementation(libs.squareup.okio.fakefilesystem)
        }
        // Only exists when the ios targets above were declared; the default hierarchy template
        // creates this source set from them.
        if (org.gradle.internal.os.OperatingSystem.current().isMacOsX) {
            iosMain.dependencies {
                implementation(libs.sqldelight.native.driver)
            }
        }
    }
}

// The compose-resources plugin registers a "copy compose resources into the android assets" task for every
// android component, but only gives it an output directory when the component exposes an assets source set.
// The kmp android device-test component does not, so the task is left with an unset @OutputDirectory and
// fails validation -- which blocks the instrumented tests from even compiling. Nothing consumes its output
// (that wiring is exactly what is missing), so skip it.
tasks.matching { it.name == "copyAndroidDeviceTestComposeResourcesToAndroidAssets" }.configureEach {
    enabled = false
}

sqldelight {
    databases {
        create("ChannelsDatabase") {
            packageName.set("fr.acinq.phoenix.db.sqldelight")
            srcDirs.from("src/commonMain/sqldelight/channelsdb")
        }
        create("PaymentsDatabase") {
            packageName.set("fr.acinq.phoenix.db.sqldelight")
            srcDirs.from("src/commonMain/sqldelight/paymentsdb")
        }
        create("AppDatabase") {
            packageName.set("fr.acinq.phoenix.db.sqldelight")
            srcDirs.from("src/commonMain/sqldelight/appdb")
        }
    }
}

mavenPublishing {
    publishToMavenCentral()

    // Only Central demands signatures, and only the release workflow has a key. JitPack builds with
    // no key at all, so asking to sign there just fails the publication.
    if (providers.gradleProperty("signingInMemoryKey").isPresent) {
        signAllPublications()
    }

    // Maven Central gets fr.acinq.phoenix:library:<version>; jitpack.yml overrides these because
    // JitPack serves a repository's modules under `com.github.<user>.<repo>`, and a multiplatform
    // publication has to be built under the coordinates it will be resolved by -- gradle module
    // metadata records its own group/name/version and gradle rejects a module whose metadata
    // disagrees with the coordinates it was requested under.
    //
    // Only the coordinates move. `project.group` stays put because compose-resources derives the
    // generated `Res` package from it, and every import of it is `fr.acinq.phoenix.library.*`.
    coordinates(
        groupId = providers.gradleProperty("publishGroupId").getOrElse(group.toString()),
        artifactId = "library",
        version = providers.gradleProperty("publishVersion").getOrElse(version.toString()),
    )

    pom {
        name = "Lightning KMP Application"
        description = "A library that extends lightning-kmp-core with application specific logic."
        inceptionYear = "2024"
        url = "https://github.com/kngako/lightning-kmp-app"
        licenses {
            license {
                name = "The Apache License, Version 2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                distribution = "https://www.apache.org/licenses/LICENSE-2.0.txt"
            }
        }
        developers {
            developer {
                id = "kngako"
                name = "Kgothatso Ngako"
                url = "https://github.com/kngako"
            }
        }
        scm {
            url = "https://github.com/kngako/lightning-kmp-app"
            connection = "scm:git:git://github.com/kngako/lightning-kmp-app.git"
            developerConnection = "scm:git:ssh://git@github.com/kngako/lightning-kmp-app.git"
        }
    }
}
