import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
            // Gradle's `api(project(":shared-core"))` below only propagates on the
            // JVM/Android compile classpath - it does NOT put :shared-core's
            // declarations into this framework's generated Objective-C header. Without
            // this line `Shared.h` exposes no DdayEntry/ThemeCatalog/DdayCalculator/
            // IosDdayStorage at all, so Swift in the iosApp main target can't reach
            // them through `import Shared`. Orthogonal to the :shared/:shared-core
            // split itself (that's about which Firebase-touching binary code links
            // into which target); this is only about which headers are exposed.
            export(project(":shared-core"))
        }
    }
    
    android {
       namespace = "com.example.widgetly.shared"
       compileSdk = libs.versions.android.compileSdk.get().toInt()
       minSdk = libs.versions.android.minSdk.get().toInt()
    
       compilerOptions {
           // JVM_17: dev.gitlive Firebase artifacts ship inline functions compiled
           // with JVM target 17; JVM_11 fails with INLINE_FROM_HIGHER_PLATFORM.
           jvmTarget = JvmTarget.JVM_17
       }
       androidResources {
           enable = true
       }
       withHostTest {
           isIncludeAndroidResources = true
       }
       withDeviceTestBuilder {
           sourceSetTreeName = "test"
       }.configure {
           instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
       }
    }
    
    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.compose.uiTooling)
            implementation(libs.androidx.datastore.preferences)
        }
        commonMain.dependencies {
            // api, not implementation: Dday/theme types moved to :shared-core but are
            // still part of :shared's public API surface (AppRoot, MainViewController)
            // and are consumed directly by :androidApp, so they must stay on
            // consumers' compile classpath. This covers the JVM/Android side only -
            // Swift visibility needs the `export(...)` in the framework block above.
            api(project(":shared-core"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
            // api, not implementation: these GitLive types appear directly in shared's
            // public API (FirebaseWidgetlyFunctions, FirestoreConnectionRepository,
            // ensureSignedIn), so consumers like androidApp need them on their own
            // compile classpath to construct/call into that API.
            api(libs.firebase.auth)
            api(libs.firebase.firestore)
            api(libs.firebase.functions)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.firebase.common.internal)
        }
        getByName("androidHostTest") {
            dependencies {
                implementation(libs.androidx.testExt.junit)
                implementation(libs.robolectric)
            }
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}