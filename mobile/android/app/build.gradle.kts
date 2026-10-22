plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.jarvis.island"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.jarvis.island"
        minSdk = 26
        targetSdk = 35
        versionCode = 25
        versionName = "1.3.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("KEYSTORE_PATH") ?: "../keystore/release.jks"
            val keystoreFile = file(keystorePath)
            val storePass = System.getenv("KEYSTORE_PASSWORD")
            val alias = System.getenv("KEY_ALIAS")
            val keyPass = System.getenv("KEY_PASSWORD")

            val hasCredentials = keystoreFile.exists() &&
                !storePass.isNullOrBlank() &&
                !alias.isNullOrBlank() &&
                !keyPass.isNullOrBlank()

            if (hasCredentials) {
                storeFile = keystoreFile
                storePassword = storePass
                keyAlias = alias
                keyPassword = keyPass
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            val releaseSigning = signingConfigs.getByName("release")
            if (releaseSigning.storeFile != null) {
                signingConfig = releaseSigning
            }
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

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Compose BOM
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Media & Coroutines
    implementation("androidx.media:media:1.7.0")
    implementation("com.alphacephei:vosk-android:0.3.47")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")

    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}

gradle.taskGraph.whenReady {
    val isReleaseBuild = allTasks.any {
        it.name.equals("assembleRelease", ignoreCase = true) ||
        it.name.equals("packageRelease", ignoreCase = true)
    }
    if (isReleaseBuild) {
        val releaseConfig = android.signingConfigs.getByName("release")
        val storeFile = releaseConfig.storeFile
        if (storeFile == null || !storeFile.exists() || releaseConfig.storePassword.isNullOrBlank()) {
            throw GradleException(
                "Release signing failed: Secure signing credentials were not provided. " +
                "Set KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD, and ensure a valid keystore is configured via CI secrets."
            )
        }
    }
}

