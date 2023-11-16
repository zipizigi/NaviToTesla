plugins {
    id("com.android.application")
    kotlin("android")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
    id("com.google.firebase.firebase-perf")
    id("com.google.devtools.ksp")
    id("org.jlleitschuh.gradle.ktlint")
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

        versionCode = Integer.parseInt(System.getenv("GITHUB_RUN_NUMBER") ?: "1") ?: 1
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
                "proguard-rules.pro"
            )
        }
        getByName("debug") {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
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

    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.10.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    implementation("com.github.Piashsarker:AndroidAppUpdateLibrary:1.0.4")
    implementation("net.yslibrary.keyboardvisibilityevent:keyboardvisibilityevent:3.0.0-RC3")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("androidx.sqlite:sqlite-ktx:2.4.0")
    implementation("androidx.work:work-runtime-ktx:2.8.1")

    var lifecycle_version = "2.6.2"
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:$lifecycle_version")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:$lifecycle_version")
    implementation("androidx.lifecycle:lifecycle-common-java8:$lifecycle_version")
    var navigation_version = "2.7.5"
    implementation("androidx.navigation:navigation-fragment-ktx:$navigation_version")
    implementation("androidx.navigation:navigation-ui-ktx:$navigation_version")
    implementation("androidx.legacy:legacy-support-v4:1.0.0")
    implementation("androidx.concurrent:concurrent-futures-ktx:1.1.0")

    implementation("org.apache.commons:commons-lang3:3.13.0")

    implementation("androidx.room:room-runtime:2.6.0")
    implementation("androidx.room:room-ktx:2.6.0")
    annotationProcessor("androidx.room:room-compiler:2.6.0")
    ksp("androidx.room:room-compiler:2.6.0")

    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    implementation("android.arch.lifecycle:extensions:1.1.1")

    implementation(platform("com.google.firebase:firebase-bom:32.6.0"))
    implementation("com.google.firebase:firebase-config")
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-crashlytics")
    implementation("com.google.firebase:firebase-perf")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
ktlint {
    android.set(true)
    outputColorName.set("RED")
}
