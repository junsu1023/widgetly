import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/**
 * Firebase-free half of the shared code (dday + theme).
 *
 * Why this module exists: Kotlin/Native links a module's whole multiplatform
 * dependency graph into one static framework binary (`isStatic = true`), with no
 * dead-code elimination of unused cinterop bridging code. So as soon as `:shared`
 * gained GitLive Firebase code, `Shared.framework` carried unresolved references to
 * Firebase's Objective-C classes (FIRAuth/FIRUser/FIRFirestore/...), which only the
 * iosApp main target links the real Firebase iOS SDK to satisfy. The
 * DdayWidgetExtensionExtension target does NOT link that SDK, so `import Shared`
 * there broke its link step. It imports `SharedCore` instead - this module has no
 * Firebase dependency at all, so its framework binary has no Firebase ObjC references.
 *
 * Keep it that way: nothing in here may depend on `dev.gitlive:firebase-*`.
 */
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
            baseName = "SharedCore"
            isStatic = true
        }
    }

    android {
        namespace = "com.example.widgetly.sharedcore"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            // Matches :shared - keeps both modules on one JVM target so the
            // Android classpath stays consistent.
            jvmTarget = JvmTarget.JVM_17
        }
        androidResources {
            enable = true
        }
        withHostTest {
            isIncludeAndroidResources = true
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.compose.uiTooling)
            implementation(libs.androidx.datastore.preferences)
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.material.icons.extended)
            implementation(libs.compose.ui)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            api(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
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
