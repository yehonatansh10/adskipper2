@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.adskipper2"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.adskipper2"
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
        debug {
            buildConfigField("Boolean", "DEBUG", "true")
            buildConfigField("String", "API_ENDPOINT", "\"https://dev-api.example.com\"")
            buildConfigField("Boolean", "ANALYTICS_ENABLED", "false")
            isMinifyEnabled = false
        }
        release {
            buildConfigField("Boolean", "DEBUG", "false")
            buildConfigField("String", "API_ENDPOINT", "\"https://api.example.com\"")
            buildConfigField("Boolean", "ANALYTICS_ENABLED", "true")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            matchingFallbacks += listOf("release", "debug")

            aaptOptions {
                noCompress += listOf("html", "json")  // אלה פורמטים שצריך לשמור
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
    packaging {
        resources {
            excludes += "/.idea/**"
            excludes += "/.git/**"
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
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

    // הוספת ספריית אבטחה
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // ספריית Gson לסריאליזציה של נתונים
    implementation("com.google.code.gson:gson:2.10.1")

    // Testing
    testImplementation(libs.junit.core)
    testImplementation(libs.junit.core)
    testImplementation("org.mockito:mockito-core:5.2.0")
    testImplementation("org.mockito:mockito-inline:5.2.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.1.0")
    androidTestImplementation(libs.junit.android)
    androidTestImplementation(libs.espresso)
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.junit.android)
    androidTestImplementation(libs.espresso)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")

    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
}