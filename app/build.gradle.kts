plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.hub.app"
    // Target device is a Galaxy S25 Ultra (ships with a recent Android/One UI build);
    // compileSdk/targetSdk track the latest stable SDK at time of writing.
    compileSdk = 35

    defaultConfig {
        applicationId = "com.hub.app"
        minSdk = 29 // Android 10, per spec
        targetSdk = 35
        versionCode = 37
        versionName = "0.1.36"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Release-Signatur aus Umgebungsvariablen (CI) oder gradle.properties (lokal).
    // Ohne diese Werte bleibt der Release-Build unsigniert - fuer die Verteilung ueber
    // GitHub/Obtainium MUSS er signiert sein, und zwar stets mit DEMSELBEN Keystore,
    // sonst verweigert Android das Update ueber die alte Version.
    val keystorePath: String? = System.getenv("KEYSTORE_FILE")
        ?: (project.findProperty("KEYSTORE_FILE") as String?)

    signingConfigs {
        create("release") {
            if (keystorePath != null) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                    ?: project.findProperty("KEYSTORE_PASSWORD") as String?
                keyAlias = System.getenv("KEY_ALIAS")
                    ?: project.findProperty("KEY_ALIAS") as String?
                keyPassword = System.getenv("KEY_PASSWORD")
                    ?: project.findProperty("KEY_PASSWORD") as String?
            }
        }
    }

    buildTypes {
        release {
            // Minify bewusst aus: ohne getestete Proguard-Keep-Regeln koennten R8-
            // Optimierungen Retrofit/Moshi/Room zur Laufzeit brechen. Reines Sideloading,
            // Groesse ist zweitrangig gegenueber Zuverlaessigkeit.
            isMinifyEnabled = false
            if (keystorePath != null) signingConfig = signingConfigs.getByName("release")
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
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
    }

    packaging {
        resources {
            // Mehrere Abhaengigkeiten (u.a. die beiden JavaMail-Artefakte fuer IMAP)
            // liefern gleichnamige Lizenz-/Hinweis-Dateien unter META-INF. Diese sind
            // fuer die Laufzeit irrelevant und wuerden sonst beim Mergen kollidieren.
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/NOTICE.md"
            excludes += "/META-INF/LICENSE.md"
            excludes += "/META-INF/NOTICE"
            excludes += "/META-INF/LICENSE"
            excludes += "/META-INF/DEPENDENCIES"
        }
    }
}

dependencies {
    // Core / Compose
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Room + SQLCipher (encrypted local persistence)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    // Neue SQLCipher-Koordinate (ab 4.5.5); die alte "android-database-sqlcipher"
    // wird nicht mehr gepflegt und existiert nicht in dieser Version.
    implementation("net.zetetic:sqlcipher-android:4.5.6")
    implementation("androidx.sqlite:sqlite:2.4.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Security: Keystore-backed key storage for the SQLCipher passphrase, biometric app-lock
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.biometric:biometric:1.1.0")
    // BiometricPrompt haengt sich an den Fragment-Manager, daher MainActivity als
    // FragmentActivity (siehe MainActivity-KDoc).
    implementation("androidx.fragment:fragment-ktx:1.8.2")

    // Networking for API connectors (Telegram Bot API, IMAP)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.11.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.1")
    ksp("com.squareup.moshi:moshi-kotlin-codegen:1.15.1")
    implementation("com.sun.mail:android-mail:1.6.7") // IMAP (Phase 6 stub connector)
    implementation("com.sun.mail:android-activation:1.6.7")

    // Coil for notification / contact icons
    implementation("io.coil-kt:coil-compose:2.6.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.06.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
