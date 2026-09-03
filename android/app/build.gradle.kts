import com.android.build.gradle.internal.api.BaseVariantOutputImpl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

fun String.asBuildConfigString(): String =
    "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

fun envDefault(name: String, fallback: String): String =
    providers.environmentVariable(name).orElse(fallback).get()

fun envFlag(name: String): Boolean =
    providers.environmentVariable(name).orElse("false").get().toBooleanStrictOrNull() ?: false

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "io.github.ichwars.fitorb.relay"
    compileSdk = 35

    defaultConfig {
        val allowCleartextHttp = envFlag("FITORB_ALLOW_CLEARTEXT_HTTP")
        applicationId = "io.github.ichwars.fitorb.relay"
        minSdk = 26
        targetSdk = 35
        versionCode = 15
        versionName = "0.1.14"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField(
            "String",
            "DEFAULT_HOME_ASSISTANT_URL",
            envDefault("FITORB_DEFAULT_HOME_ASSISTANT_URL", "")
                .asBuildConfigString(),
        )
        buildConfigField(
            "String",
            "DEFAULT_RELAY_TOKEN",
            envDefault("FITORB_DEFAULT_RELAY_TOKEN", "").asBuildConfigString(),
        )
        buildConfigField(
            "String",
            "DEFAULT_RING_ID",
            envDefault("FITORB_DEFAULT_RING_ID", "")
                .asBuildConfigString(),
        )
        buildConfigField("boolean", "ALLOW_CLEARTEXT_HTTP", allowCleartextHttp.toString())
        manifestPlaceholders["usesCleartextTraffic"] = allowCleartextHttp.toString()
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    buildTypes {
        debug {
            // Debug/installable builds are commonly used with a trusted local HA server.
            buildConfigField("boolean", "ALLOW_CLEARTEXT_HTTP", "true")
            manifestPlaceholders["usesCleartextTraffic"] = "true"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.all {
            it.maxHeapSize = "128m"
            it.maxParallelForks = 1
        }
    }

    applicationVariants.all {
        val variantName = name
        val appVersion = versionName ?: "dev"
        outputs.all {
            (this as BaseVariantOutputImpl).outputFileName =
                "Fitorb-Mobile-Relay-$appVersion-$variantName.apk"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.health.connect)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    testImplementation(kotlin("test"))
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.robolectric)
}
