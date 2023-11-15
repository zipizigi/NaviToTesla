// Top-level build file where you can add configuration options common to all sub-projects/modules.\
plugins {
    id("com.android.application") version "8.1.3" apply false
    id("org.jetbrains.kotlin.android") version "1.9.20" apply false
    id("com.google.devtools.ksp") version "1.9.20-1.0.14" apply false

    id("com.google.gms.google-services") version "4.3.13" apply false
    id("com.google.firebase.crashlytics") version "2.9.1" apply false
    id("com.google.firebase.firebase-perf") version "1.4.2" apply false
    id("org.jlleitschuh.gradle.ktlint") version "11.6.1" apply false
}
