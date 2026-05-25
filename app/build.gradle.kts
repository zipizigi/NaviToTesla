import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
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
    compileSdk = 36

    defaultConfig {
        buildConfigField("String", "BUILD_MODE", "\"playstore\"")
        applicationId = "me.zipi.navitotesla"
        minSdk = 24
        targetSdk = 36

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
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
        resValues = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
    namespace = "me.zipi.navitotesla"
}

androidComponents {
    onVariants { variant ->
        variant.androidTest?.sources?.assets?.addStaticSourceDirectory("$projectDir/schemas")
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}

dependencies {
    implementation(platform(libs.kotlinx.serialization.bom))
    implementation(platform(libs.kotlinx.coroutines.bom))
    implementation(platform(libs.firebase.bom))

    implementation(libs.kotlin.coroutine)
    implementation(libs.kotlinx.coroutines.play.services)

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
    implementation(libs.ted.permission)

    implementation(libs.bundles.room)
    annotationProcessor(libs.room.compiler)
    ksp(libs.room.compiler)

    implementation(libs.bundles.retrofit)

    implementation(libs.bundles.firebase)
    debugImplementation(libs.firebase.appcheck.debug)

    implementation(libs.google.places)

    testImplementation(libs.junit)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.espresso)
    androidTestImplementation(libs.room.testing)
}
ktlint {
    version.set(libs.versions.ktlint.cli.get())
    android.set(true)
    outputColorName.set("RED")
}
