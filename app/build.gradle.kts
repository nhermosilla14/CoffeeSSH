plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

val ciKeystorePath = providers.environmentVariable("ANDROID_KEYSTORE_PATH").orNull
val ciKeystorePassword = providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD").orNull
val ciKeyAlias = providers.environmentVariable("ANDROID_KEY_ALIAS").orNull
val ciKeyPassword = providers.environmentVariable("ANDROID_KEY_PASSWORD").orNull
val ciVersionCode = providers.environmentVariable("ANDROID_VERSION_CODE")
    .orNull
    ?.toIntOrNull()
    ?.let { 1000 + it }
    ?: 3

val hasCiSigning = listOf(
    ciKeystorePath,
    ciKeystorePassword,
    ciKeyAlias,
    ciKeyPassword,
).all { it != null }

if (System.getenv("CI") == "true" && !hasCiSigning) {
    error("CI release builds require the Android signing environment variables")
}

android {
    namespace = "cl.segfault.coffeessh"
    compileSdk = 36

    defaultConfig {
        applicationId = "cl.segfault.coffeessh"
        minSdk = 26
        targetSdk = 36
        versionCode = ciVersionCode
        versionName = "0.3.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = if (hasCiSigning) {
                signingConfigs.create("ciRelease").apply {
                    storeFile = file(ciKeystorePath!!)
                    storePassword = ciKeystorePassword
                    keyAlias = ciKeyAlias
                    keyPassword = ciKeyPassword
                }
            } else {
                // Keep local release artifacts installable without exposing the CI key.
                signingConfigs.getByName("debug")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(project(":terminal"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons.extended)
    implementation(libs.navigation.compose)

    implementation(libs.room.runtime)
    ksp(libs.room.compiler)

    implementation(libs.sshj)
    implementation(libs.bcprov)
    implementation(libs.bcpkix)
    runtimeOnly(libs.slf4j.nop)

    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)

    androidTestImplementation(libs.test.ext.junit)
    androidTestImplementation(libs.test.runner)
    androidTestImplementation(libs.test.core)
}
