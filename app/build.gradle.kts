import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.yingwang.watchchess"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.yingwang.watchchess"
        minSdk = 30
        // Play 不再接受 targetSdk 34 的新应用，提交时会被退回，报 "Target SDK of
        // artifact is too low"。跟到 36。
        targetSdk = 36
        versionCode = 11
        versionName = "1.0.4"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    // 上传用的签名。钥匙与口令放在仓库之外（~/.config/watchchess/），绝不进版本库。
    // 这是上传密钥不是应用签名密钥：应用签名密钥由 Play 自己保管（Play App Signing），
    // 所以万一这把丢了还能向 Google 申请重置，不至于这个包名就此发不了更新。
    val keystoreProps = Properties()
    val keystorePropsFile = File(System.getProperty("user.home"), ".config/watchchess/keystore.properties")
    if (keystorePropsFile.exists()) {
        FileInputStream(keystorePropsFile).use { keystoreProps.load(it) }
    }

    signingConfigs {
        create("release") {
            val path: String? = keystoreProps.getProperty("storeFile")
            if (path != null && File(path).exists()) {
                storeFile = File(path)
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            // 引擎是要被 exec 的可执行文件，不是给 System.loadLibrary 加载的库，
            // 必须让它在安装时落到磁盘上。
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation("androidx.wear.compose:compose-material:1.3.0")
    implementation("androidx.wear.compose:compose-foundation:1.3.0")
    implementation("androidx.compose.ui:ui:1.6.1")
    implementation("androidx.compose.runtime:runtime:1.6.1")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    testImplementation("junit:junit:4.13.2")
}
