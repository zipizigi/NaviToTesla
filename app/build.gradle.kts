plugins {
    kotlin("android")
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.gms.service)
    alias(libs.plugins.firebase.crashlytics)
    alias(libs.plugins.firebase.perf)
    alias(libs.plugins.ktlint)
}
ksp {
    arg { listOf("room.schemaLocation=$projectDir/schemas") }
}
android {
    compileSdk = 34

    defaultConfig {
        buildConfigField("String", "BUILD_MODE", "\"playstore\"")
        applicationId = "me.zipi.navitotesla"
        minSdk = 23
        targetSdk = 34

        versionCode = Integer.parseInt(System.getenv("GITHUB_RUN_NUMBER") ?: "1")
        versionName = System.getenv("RELEASE") ?: "1.0"
    }

    flavorDimensions += "store"
    productFlavors {
        create("nostore") {
            dimension = "store"
            applicationIdSuffix = ".ns"
            versionNameSuffix = "-nostore"
            buildConfigField("String", "BUILD_MODE", "\"nostore\"")
            resValue("string", "app_name", "\"Navi to Tesla (nostore)\"")
        }
        create("playstore") {
            dimension = "store"
            buildConfigField("String", "BUILD_MODE", "\"playstore\"")
            resValue("string", "app_name", "\"Navi to Tesla\"")
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        getByName("debug") {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isDebuggable = true
            extra.apply {
                set("enableCrashlytics", false)
            }
//            ext.enableCrashlytics = false
        }
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    buildFeatures {
        viewBinding = true
    }
    namespace = "me.zipi.navitotesla"
}

dependencies {
    implementation(libs.kotlin.coroutine)

    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.security.crypto)

    implementation(libs.androidAppUpdateLibrary)
    implementation(libs.keyboardvisibilityevent)
    implementation(libs.gson)
    implementation(libs.androidx.work.runtime.ktx)

    implementation(libs.bundles.androidx.lifecycle)
    implementation(libs.bundles.androidx.navigation)
    implementation(libs.androidx.legacy.support.v4)
    implementation(libs.androidx.concurrent.futures.ktx)

    implementation(libs.commons.lang3)

    implementation(libs.bundles.room)
    annotationProcessor(libs.room.compiler)
    ksp(libs.room.compiler)

    implementation(libs.bundles.retrofit)

    implementation(platform(libs.firebase.bom))
    implementation(libs.bundles.firebase)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.espresso)
}
ktlint {
    android.set(true)
    outputColorName.set("RED")
}
