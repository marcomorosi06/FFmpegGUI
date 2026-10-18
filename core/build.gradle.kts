import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Pure Kotlin module: no Android dependency, so its tests run on a plain JVM
// with `./gradlew :core:test`.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    // Plain JVM coroutines, the same the app uses: lets long work in the engine
    // (packing the parts into a zip) stop when the export is cancelled.
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.core)
}
