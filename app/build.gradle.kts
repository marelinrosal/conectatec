plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)

    // se agrega el plugin de serialization
    kotlin("plugin.serialization") version "2.1.0"

    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.conectatec"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.conectatec"
        minSdk = 24
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
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.play.services.maps)
    implementation(libs.androidx.ui.graphics.android)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation(libs.material.v190)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.zxing)

    // se agrega la dependencia de instalación de la libreria cliente kotlin para supabase
    implementation(platform("io.github.jan-tennert.supabase:bom:3.1.1"))
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.github.jan-tennert.supabase:auth-kt")
    implementation("io.github.jan-tennert.supabase:realtime-kt")
    implementation("io.github.jan-tennert.supabase:postgrest-kt:3.1.1")

    // se agrega el Ktor Client Engine para cada Kotlin targets
    implementation("io.ktor:ktor-client-android:3.1.1")

    //Se agrega el Kotlin Multiplatform Client para Supabase
    implementation("io.github.jan-tennert.supabase:postgrest-kt:3.1.3")

    //se agrega kotlinx serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")

    //se agrega la dependencia de coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")



    //Stripe para pagos.

    implementation(libs.stripe.android)
    implementation(libs.okhttp)

    //Firebase para notificaciones
    // Import the Firebase BoM
    implementation(platform("com.google.firebase:firebase-bom:33.11.0"))
    // TODO: Add the dependencies for Firebase products you want to use
    // When using the BoM, don't specify versions in Firebase dependencies
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-messaging")
    // Add the dependencies for any other desired Firebase products
    // https://firebase.google.com/docs/android/setup#available-libraries

}