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
    androidLibrary {
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
    iosArm64()
    iosSimulatorArm64()
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

            api(libs.lightning.kmp.core)

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
        iosMain.dependencies {
            implementation(libs.sqldelight.native.driver)
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

    signAllPublications()

    coordinates(group.toString(), "library", version.toString())

    pom {
        name = "Lightning KMP Application"
        description = "A library that extends lightning-kmp-core with application specific logic."
        inceptionYear = "2024"
        url = "https://github.com/kotlin/multiplatform-library-template/"
        licenses {
            license {
                name = "XXX"
                url = "YYY"
                distribution = "ZZZ"
            }
        }
        developers {
            developer {
                id = "XXX"
                name = "YYY"
                url = "ZZZ"
            }
        }
        scm {
            url = "XXX"
            connection = "YYY"
            developerConnection = "ZZZ"
        }
    }
}
