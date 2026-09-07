import java.security.KeyStore
import java.io.FileInputStream

plugins {
    id("com.android.application")
}

android {
    namespace = "com.deivid22srk.dkc2recomp"
    compileSdk = 34
    ndkVersion = "27.2.12479018"

    defaultConfig {
        applicationId = "com.deivid22srk.dkc2recomp"
        minSdk = 26
        targetSdk = 34
        versionCode = 3
        versionName = "0.0.7-android"
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
        externalNativeBuild {
            cmake {
                arguments += listOf(
                    "-DCMAKE_BUILD_TYPE=Release",
                    "-DANDROID_STL=c++_static",
                )
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("../CMakeLists.txt")
            version = "3.31.6"
        }
    }

    signingConfigs {
        create("release") {
            val storePath = System.getenv("DKC2_RELEASE_STORE_FILE")
            if (storePath != null) {
                storeFile = file(storePath)
                storePassword = System.getenv("DKC2_RELEASE_STORE_PASSWORD")
                keyAlias = System.getenv("DKC2_RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("DKC2_RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            val storePath = System.getenv("DKC2_RELEASE_STORE_FILE")
            signingConfig = if (storePath != null) signingConfigs.getByName("release")
                            else signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    lint {
        abortOnError = false
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
}
