import com.android.build.api.dsl.ApplicationExtension

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val appVersionCode = providers.gradleProperty("appVersionCode")
    .map(String::toInt)
    .orElse(1)

val appVersionName = providers.gradleProperty("appVersionName")
    .orElse("0.1.0")

val releaseKeystoreFile = providers.environmentVariable("APPMAPPER_KEYSTORE_FILE").orNull
val releaseKeystorePassword = providers.environmentVariable("APPMAPPER_KEYSTORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("APPMAPPER_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("APPMAPPER_KEY_PASSWORD").orNull
val hasReleaseSigning = listOf(
    releaseKeystoreFile,
    releaseKeystorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { !it.isNullOrBlank() }

val licenseAssets = layout.buildDirectory.dir("generated/licenseAssets")
val syncLicenseAssets = tasks.register<Sync>("syncLicenseAssets") {
    from(rootProject.file("../../LICENSE"), rootProject.file("../../THIRD_PARTY_LICENSES.md"))
    into(licenseAssets)
}

extensions.configure<ApplicationExtension> {
    namespace = "dev.youximi.appmapper"
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }
    buildToolsVersion = "37.0.0"
    sourceSets.getByName("main").assets.directories.add(licenseAssets.get().asFile.path)

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseKeystoreFile!!)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    defaultConfig {
        applicationId = "dev.youximi.appmapper"
        minSdk = 26
        targetSdk = 37
        versionCode = appVersionCode.get()
        versionName = appVersionName.get()
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        release {
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
}

kotlin {
    jvmToolchain(27)
}

tasks.named("preBuild") {
    dependsOn(syncLicenseAssets)
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom-beta:2026.10.00")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.core:core:1.19.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("com.google.zxing:core:3.5.4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20260814")
}
