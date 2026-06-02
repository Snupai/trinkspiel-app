import java.io.File
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

val releaseSigningPropertiesFile = rootProject.file("signing/release-signing.properties")
val releaseSigningProperties = Properties().apply {
    if (releaseSigningPropertiesFile.exists()) {
        releaseSigningPropertiesFile.inputStream().use(::load)
    }
}

fun releaseSigningProperty(name: String): String? =
    providers.gradleProperty(name).orNull
        ?: providers.environmentVariable(name).orNull
        ?: releaseSigningProperties.getProperty(name)

val releaseStoreFile = releaseSigningProperty("SEEMOPS_RELEASE_STORE_FILE")
    ?: "signing/seemops-release.keystore"
val releaseStorePassword = releaseSigningProperty("SEEMOPS_RELEASE_STORE_PASSWORD")
val releaseKeyAlias = releaseSigningProperty("SEEMOPS_RELEASE_KEY_ALIAS")
val releaseKeyPassword = releaseSigningProperty("SEEMOPS_RELEASE_KEY_PASSWORD")
val releaseStorePath = if (File(releaseStoreFile).isAbsolute) {
    file(releaseStoreFile)
} else {
    rootProject.file(releaseStoreFile)
}
val releaseSigningConfigured = releaseStorePassword != null &&
    releaseKeyAlias != null &&
    releaseKeyPassword != null &&
    releaseStorePath.exists()

android {
    namespace = "com.snupai.trinkspiel"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.snupai.trinkspiel"
        minSdk = 24
        targetSdk = 36
        versionCode = 3
        versionName = "3.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseSigningConfigured) {
            create("release") {
                storeFile = releaseStorePath
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    val roomVersion = "2.8.4"
    val lifecycleVersion = "2.10.0"
    val composeBom = platform("androidx.compose:compose-bom:2026.05.01")

    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.14.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:$lifecycleVersion")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:$lifecycleVersion")
    implementation("androidx.navigation:navigation-compose:2.9.8")
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test:rules:1.7.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20260522")
}
