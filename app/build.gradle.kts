import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.kapt)
}

val publicRepoUrl = providers.gradleProperty("publicRepoUrl")
    .orElse("https://github.com/d3v4shish/NotificationSaver")
val publicPrivacyUrl = providers.gradleProperty("publicPrivacyUrl")
    .orElse("${publicRepoUrl.get()}/blob/main/docs/privacy.md")
val publicLatestReleaseUrl = providers.gradleProperty("publicLatestReleaseUrl")
    .orElse("${publicRepoUrl.get()}/releases/latest")

val releaseVersionName = "1.0.0"
val releaseVersionCode = releaseVersionName.toVersionCode()
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use(::load)
    }
}
val envKeystorePath = providers.environmentVariable("ANDROID_KEYSTORE_PATH").orNull
val envKeystorePassword = providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD").orNull
val envKeyAlias = providers.environmentVariable("ANDROID_KEY_ALIAS").orNull
val envKeyPassword = providers.environmentVariable("ANDROID_KEY_PASSWORD").orNull
val keystorePath = envKeystorePath ?: keystoreProperties.getProperty("storeFile")
val keystorePassword = envKeystorePassword ?: keystoreProperties.getProperty("storePassword")
val signingKeyAlias = envKeyAlias ?: keystoreProperties.getProperty("keyAlias")
val signingKeyPassword = envKeyPassword ?: keystoreProperties.getProperty("keyPassword")
val hasReleaseSigning = listOf(keystorePath, keystorePassword, signingKeyAlias, signingKeyPassword).all { !it.isNullOrBlank() }

android {
    namespace = "dev.d3v.notificationsaver"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.d3v.notificationsaver"
        minSdk = 26
        targetSdk = 36
        versionCode = releaseVersionCode
        versionName = releaseVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "PUBLIC_REPO_URL", "\"${publicRepoUrl.get()}\"")
        buildConfigField("String", "PUBLIC_PRIVACY_URL", "\"${publicPrivacyUrl.get()}\"")
        buildConfigField("String", "PUBLIC_LATEST_RELEASE_URL", "\"${publicLatestReleaseUrl.get()}\"")
        buildConfigField("String", "OSS_LICENSE_NAME", "\"Apache-2.0\"")
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(requireNotNull(keystorePath))
                storePassword = requireNotNull(keystorePassword)
                keyAlias = requireNotNull(signingKeyAlias)
                keyPassword = requireNotNull(signingKeyPassword)
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = if (hasReleaseSigning) signingConfigs.getByName("release") else null
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kapt {
    correctErrorTypes = true
    arguments {
        arg("room.schemaLocation", "$projectDir/schemas")
        arg("room.incremental", "true")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.google.material)
    implementation(libs.kotlinx.coroutines.android)
    kapt(libs.androidx.room.compiler)
    testImplementation(libs.junit4)
    testImplementation(libs.org.json)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.compose.ui.test.junit4)

    debugImplementation(libs.compose.ui.test.manifest)
    debugImplementation(libs.compose.ui.tooling)
}

tasks.register("printVersionName") {
    doLast {
        println(releaseVersionName)
    }
}

tasks.register("printVersionCode") {
    doLast {
        println(releaseVersionCode)
    }
}

private fun String.toVersionCode(): Int {
    val parts = split('.')
        .map { it.toIntOrNull() ?: error("Version '$this' must use numeric semver segments") }
    require(parts.size == 3) { "Version '$this' must use major.minor.patch" }
    val (major, minor, patch) = parts
    return major * 10_000 + minor * 100 + patch
}
