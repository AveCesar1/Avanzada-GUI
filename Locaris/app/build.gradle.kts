plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.locaris"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.locaris"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)

    // --- CORRECCIÓN DEL ERROR DE VERSIÓN ---
    // Cambiamos "implementation(libs.activity)" por esta versión específica
    // para evitar el error de que necesitas la API 36.
    implementation("androidx.activity:activity:1.9.3")

    implementation(libs.constraintlayout)
    implementation(libs.play.services.location)

    // --- LIBRERÍAS NUEVAS NECESARIAS (Para que se quiten los errores rojos) ---

    // Mapas (OpenStreetMap)
    implementation("org.osmdroid:osmdroid-android:6.1.18")

    // Conexión a Internet (Retrofit y OkHttp)
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Procesamiento de JSON (Para @SerializedName)
    implementation("com.google.code.gson:gson:2.10.1")

    // Testing (déjalo como estaba)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
