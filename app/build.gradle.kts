import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Llave PÚBLICA de ePayco (la misma que usa checkout.js en cualquier página web; no es secreta).
// Se toma de local.properties si existe y, si no, de gradle.properties.
val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun config(name: String, default: String): String =
    localProps.getProperty(name) ?: (project.findProperty(name) as String?) ?: default

android {
    namespace = "co.nehood.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "co.nehood.app"
        minSdk = 26          // RNF-11: Android 8.0 en adelante
        targetSdk = 34
        versionCode = 3
        versionName = "3.0"

        buildConfigField("String", "EPAYCO_PUBLIC_KEY", "\"${config("EPAYCO_PUBLIC_KEY", "")}\"")
        buildConfigField("boolean", "EPAYCO_TEST", config("EPAYCO_TEST", "true"))
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
        viewBinding = true   // acceso tipado a las vistas de los layouts XML
        buildConfig = true   // expone las llaves de ePayco como BuildConfig.*
    }
}

dependencies {
    // AndroidX y Material
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.fragment:fragment-ktx:1.8.2")
    implementation("androidx.activity:activity-ktx:1.9.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    // Corrutinas: tareas en segundo plano (SQLite) y await() sobre la ubicación de Play Services
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    // Geolocalización (Fused Location Provider de Google Play Services)
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // Carga de las fotos de productos guardadas en el celular
    implementation("io.coil-kt:coil:2.6.0")
}
