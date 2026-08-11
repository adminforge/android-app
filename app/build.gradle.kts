plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

import java.io.FileInputStream
import java.util.Properties

val versionPropsFile = project.file("version.properties")
val versionProps = Properties()
if (versionPropsFile.exists()) {
    versionProps.load(FileInputStream(versionPropsFile))
}

val vCode = (versionProps["VERSION_CODE"] ?: "100").toString().toInt()
val vName = (versionProps["VERSION_NAME"] ?: "1.0.0").toString()

// Signing credentials live in local.properties, which is untracked.
// Never hardcode keystore passwords in this file.
val localPropsFile = rootProject.file("local.properties")
val localProps = Properties()
if (localPropsFile.exists()) {
    localProps.load(FileInputStream(localPropsFile))
}
val releaseStoreFile = (localProps["RELEASE_STORE_FILE"] ?: "release-v2.jks").toString()
val releaseStorePassword = localProps["RELEASE_STORE_PASSWORD"]?.toString()
val releaseKeyAlias = (localProps["RELEASE_KEY_ALIAS"] ?: "adminforge").toString()
val releaseKeyPassword = localProps["RELEASE_KEY_PASSWORD"]?.toString()
val hasReleaseSigning = releaseStorePassword != null && releaseKeyPassword != null &&
        rootProject.file(releaseStoreFile).exists()

android {
    namespace = "de.adminforge.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "de.adminforge.app"
        minSdk = 24
        targetSdk = 36
        versionCode = vCode
        versionName = vName
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    // WARNING: the APK this produces is NOT ready to publish. It carries the current key only.
    // A release must be re-signed with BOTH keys plus signing-lineage.bin, so that API 24-32
    // still verifies against the previous key and API 33+ against the current one; otherwise
    // devices on Android 7-12 cannot install the update. Use sign_release.sh, then confirm with
    //   apksigner verify --print-certs adminforge-v<version>.apk
    // which must list exactly two signers (minSdkVersion 24-32 and 33+).
    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        getByName("release") {
            signingConfig = if (hasReleaseSigning) signingConfigs.getByName("release") else null
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        jvmToolchain(17)
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("com.github.bumptech.glide:glide:4.16.0")
    // kapt("com.github.bumptech.glide:compiler:4.16.0") // This line requires the 'kotlin-kapt' plugin
    
    // For background RSS fetching and notifications
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    
    // UnifiedPush
    implementation("org.unifiedpush.android:connector:3.1.2")

    // For Json Parsing
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("androidx.localbroadcastmanager:localbroadcastmanager:1.1.0")
    implementation("androidx.concurrent:concurrent-futures-ktx:1.1.0")

    // Markdown rendering
    implementation("io.noties.markwon:core:4.6.2")
}

// The assembled APK is deliberately NOT copied to adminforge-latest.apk anymore: that artifact
// still lacks the rotation lineage and would break updates on Android 7-12 if published.
// sign_release.sh produces the publishable APK.
