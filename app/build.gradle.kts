plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val baseVersionCode = 1

android {
    namespace = "ir.hiwa.grammarinuse"
    compileSdk = 35

    defaultConfig {
        applicationId = "ir.hiwa.grammarinuse"
        minSdk = 24
        targetSdk = 35
        versionCode = baseVersionCode
        versionName = "1.0.0"
    }

    // Myket Multi-APK official ABI split:
    // armeabi-v7a -> versionCode 1001 | arm64-v8a -> versionCode 2001 (always higher!)
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a")
            isUniversalApk = false
        }
    }

    val keystorePath = System.getenv("KEYSTORE_PATH")
    val keystorePassword = System.getenv("KEYSTORE_PASSWORD")
    val keyAliasEnv = System.getenv("KEY_ALIAS")
    val keyPasswordEnv = System.getenv("KEY_PASSWORD")

    signingConfigs {
        if (!keystorePath.isNullOrBlank() && !keystorePassword.isNullOrBlank() &&
            !keyAliasEnv.isNullOrBlank() && !keyPasswordEnv.isNullOrBlank()
        ) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = keystorePassword
                keyAlias = keyAliasEnv
                keyPassword = keyPasswordEnv
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
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

// Assign Myket-compliant per-ABI versionCode (arm64-v8a > armeabi-v7a)
val abiCodes = mapOf("armeabi-v7a" to 1000, "arm64-v8a" to 2000)

androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            val abi = output.filters
                .find { it.filterType == com.android.build.api.variant.FilterConfiguration.FilterType.ABI }
                ?.identifier
            val offset = abiCodes[abi] ?: 0
            output.versionCode.set(offset + baseVersionCode)
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")
}
