@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// חיפוש בקובץ Properties
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = project.loadProperties(keystorePropertiesFile)

// פונקציית עזר לטעינת קובץ properties
fun Project.loadProperties(propertiesFile: File): Map<String, String> {
    val properties = mutableMapOf<String, String>()
    if (propertiesFile.exists()) {
        propertiesFile.readLines().forEach { line ->
            if (!line.startsWith("#") && line.contains("=")) {
                val split = line.split("=", limit = 2)
                properties[split[0].trim()] = split[1].trim()
            }
        }
    }
    return properties
}

android {
    namespace = "com.yonash.adskipper2"
    compileSdk = 34

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = file(keystoreProperties["storeFile"] ?: "")
                storePassword = keystoreProperties["storePassword"] ?: ""
                keyAlias = keystoreProperties["keyAlias"] ?: ""
                keyPassword = keystoreProperties["keyPassword"] ?: ""
            }
        }
    }

    defaultConfig {
        applicationId = "com.yonash.adskipper2"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        manifestPlaceholders["usesCleartextTraffic"] = "false"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            buildConfigField("Boolean", "DEBUG", "false")
            buildConfigField("String", "API_ENDPOINT", "\"aHR0cHM6Ly9hcGkuZXhhbXBsZS5jb20=\"")
            buildConfigField("Boolean", "ANALYTICS_ENABLED", "true")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            isDebuggable = false
            ndk {
                debugSymbolLevel = "FULL"
            }

            signingConfig = if (keystorePropertiesFile.exists()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug") // Fallback to debug for development
            }
            matchingFallbacks += listOf("release")
        }

        debug {
            buildConfigField("Boolean", "DEBUG", "true")
            buildConfigField("String", "API_ENDPOINT", "\"https://dev-api.example.com\"")
            buildConfigField("Boolean", "ANALYTICS_ENABLED", "false")
            isMinifyEnabled = false

            isDebuggable = true
            ndk {
                debugSymbolLevel = "FULL"
            }
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
        buildConfig = true
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.compose.compiler.get()
    }
}

dependencies {
    // Android Core
    implementation(libs.core.ktx)
    implementation(libs.appcompat)
    implementation(libs.material)

    // Lifecycle
    implementation(libs.lifecycle.runtime)
    implementation(libs.activity.compose)

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)

    // MLKit
    implementation(libs.mlkit)
    implementation(libs.gms.mlkit)

    // Accessibility Services
    implementation(libs.window)

    // Security
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Gson
    implementation("com.google.code.gson:gson:2.10.1")

    // Testing
    testImplementation(libs.junit.core)
    testImplementation("org.mockito:mockito-core:5.2.0")
    testImplementation("org.mockito:mockito-inline:5.2.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.1.0")

    androidTestImplementation(libs.junit.android)
    androidTestImplementation(libs.espresso)
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(platform(libs.compose.bom))

    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
}