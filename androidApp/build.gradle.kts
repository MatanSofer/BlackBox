import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

val localProps = Properties().also { props ->
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { props.load(it) }
}

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.googleServices)
    alias(libs.plugins.firebaseCrashlytics)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.activity.compose)

            // DI
            implementation(libs.koin.android)
            implementation(libs.koin.androidx.compose)

            // AndroidX
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.work.runtime)
            implementation(libs.androidx.biometric)
            implementation(libs.androidx.splashscreen)

            // Google Play Services (includes Activity Recognition API)
            implementation(libs.play.services.location)

            // SQLCipher (encrypted database)
            implementation(libs.sqlcipher)

            // OSMDroid (offline maps)
            implementation(libs.osmdroid)

            // Shared module (exports Compose deps via api)
            implementation(project(":shared"))
        }
    }
}

android {
    namespace = "com.blackbox.android"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.blackbox.android"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
        buildConfigField(
            "String",
            "OPENAI_API_KEY",
            "\"${localProps.getProperty("openai.api.key", "")}\"",
        )
    }

    buildFeatures {
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    // Firebase (BoM manages all Firebase library versions)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics.ktx)

    debugImplementation(libs.compose.uiTooling)
}
