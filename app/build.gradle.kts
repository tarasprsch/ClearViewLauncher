import java.util.Properties

plugins {
    base
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

val signing = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
android {
    namespace = "dev.personal.tvlauncher"
    compileSdk = 36
    defaultConfig {
        applicationId = "dev.personal.tvlauncher"
        minSdk = 34
        targetSdk = 36
        versionCode = 17
        versionName = "0.7.8"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    if (signing.isNotEmpty()) {
        signingConfigs.create("personal") {
            storeFile = rootProject.file(signing.getProperty("storeFile"))
            storePassword = signing.getProperty("storePassword")
            keyAlias = signing.getProperty("keyAlias")
            keyPassword = signing.getProperty("keyPassword")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (signing.isNotEmpty()) signingConfig = signingConfigs.getByName("personal")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions { unitTests.isIncludeAndroidResources = true }
}
kotlin { compilerOptions { jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17 } }

base { archivesName.set("ClearViewLauncher-${android.defaultConfig.versionName}") }

dependencies {
    implementation(libs.activity)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.runtime)
    implementation(libs.recyclerview)
    implementation(libs.datastore)
    implementation(libs.coroutines)
    implementation(libs.coil)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.test.core)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.mockwebserver)
    androidTestImplementation(libs.test.core)
    androidTestImplementation(libs.test.runner)
    androidTestImplementation(libs.espresso)
    androidTestImplementation(libs.junit)
}
