// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "8.5.2" apply false
    // Kotlin 2.x: Der Compose-Compiler wird ab hier als eigenes Gradle-Plugin
    // ausgeliefert (loest composeOptions/kotlinCompilerExtensionVersion ab).
    // Die drei Versionen muessen zueinander passen - KSP haengt fest an der
    // Kotlin-Version.
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("com.google.devtools.ksp") version "2.0.21-1.0.25" apply false
}
