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

    signingConfigs {
        create("myconfig") {
            storeFile = rootProject.file("release.jks")
            storePassword = "adminforge"
            keyAlias = "adminforge"
            keyPassword = "adminforge"
            enableV1Signing = true
            enableV2Signing = true
        }
    }

    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("myconfig")
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
    
    // For HTML parsing
    implementation("org.jsoup:jsoup:1.17.2")
    
    // For Json Parsing
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("androidx.localbroadcastmanager:localbroadcastmanager:1.1.0")
    implementation("androidx.concurrent:concurrent-futures-ktx:1.1.0")

    // Markdown rendering
    implementation("io.noties.markwon:core:4.6.2")
}

tasks.whenTaskAdded {
    if (name == "assembleRelease") {
        doLast {
            copy {
                from(layout.buildDirectory.dir("outputs/apk/release"))
                into(project.rootDir)
                include("app-release.apk")
                rename { "adminforge-latest.apk" }
            }
        }
    }
}
