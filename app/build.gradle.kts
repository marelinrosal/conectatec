// Archivo de configuración de build para el módulo de la aplicación (:app).
// Define cómo se compila y empaqueta este módulo específico.

// Bloque 'plugins': Aplica los plugins necesarios para este módulo.
plugins {
    // Aplica el plugin de aplicación Android.
    // 'alias(libs.plugins.android.application)' toma la definición del plugin desde el version catalog (libs.versions.toml).
    alias(libs.plugins.android.application)

    // Aplica el plugin de Kotlin para Android.
    alias(libs.plugins.kotlin.android)

    // Aplica el plugin de Kotlin Serialization.
    // Necesario para serializar/deserializar objetos Kotlin a/desde formatos como JSON.
    // 'kotlin("plugin.serialization")' es el identificador del plugin.
    // 'version "2.1.0"' especifica la versión del plugin de serialización.
    // Nota: La versión del plugin de serialización debe ser compatible con tu versión de Kotlin.
    kotlin("plugin.serialization") version "2.1.0" // Considera gestionar esta versión también en libs.versions.toml si es posible.

    // Aplica el plugin de Google Services.
    // Necesario para integrar servicios de Firebase en este módulo.
    // Este plugin lee el archivo google-services.json.
    id("com.google.gms.google-services")
}

// Bloque 'android': Configuración específica para la compilación de Android.
android {
    // Define el namespace de la aplicación.
    // Debe ser único y se utiliza como el ID de la aplicación en el Manifest y para generar la clase R.
    namespace = "com.example.conectatec"

    // Especifica el nivel de API del SDK contra el cual se compila la aplicación.
    compileSdk = 35 // API Level 35

    // Bloque 'defaultConfig': Configuración por defecto para todas las variantes de compilación.
    defaultConfig {
        // Identificador único de la aplicación en Google Play Store.
        applicationId = "com.example.conectatec"
        // Nivel mínimo de API de Android que la aplicación soporta.
        minSdk = 26 // Android 8.0 (Oreo)
        // Nivel de API objetivo para la aplicación. Se recomienda que sea el mismo que compileSdk.
        targetSdk = 35 // API Level 35

        // Código de versión interno de la aplicación (entero incremental).
        versionCode = 1
        // Nombre de la versión visible para el usuario (ej. "1.0", "1.0.1-beta").
        versionName = "1.0"

        // Especifica el InstrumentationRunner para las pruebas de Android.
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Bloque 'buildTypes': Define diferentes configuraciones para los tipos de compilación (ej. debug, release).
    buildTypes {
        // Configuración para la variante de compilación 'release'.
        release {
            // 'isMinifyEnabled = false': Deshabilita la minificación de código (R8/ProGuard).
            // Para producción, generalmente se establece en 'true' para reducir el tamaño del APK y ofuscar el código.
            isMinifyEnabled = false // TODO: Considerar habilitar (true) para builds de producción.
            // 'proguardFiles': Especifica los archivos de reglas de ProGuard.
            // Se utiliza para la optimización y ofuscación del código cuando isMinifyEnabled es true.
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"), // Reglas por defecto de Android.
                "proguard-rules.pro" // Reglas personalizadas para tu proyecto.
            )
        }
        // Podrías definir otros buildTypes aquí, como 'debug' con configuraciones específicas.
        // debug {
        //     applicationIdSuffix = ".debug"
        //     isDebuggable = true
        // }
    }

    // Bloque 'compileOptions': Opciones para el compilador de Java.
    compileOptions {
        // Especifica la compatibilidad del código fuente con Java 11.
        sourceCompatibility = JavaVersion.VERSION_11
        // Especifica la compatibilidad del código compilado (bytecode) con Java 11.
        targetCompatibility = JavaVersion.VERSION_11
    }

    // Bloque 'kotlinOptions': Opciones para el compilador de Kotlin.
    kotlinOptions {
        // Especifica la versión de la JVM objetivo para el bytecode generado por Kotlin.
        jvmTarget = "11" // Debe coincidir con la compatibilidad de Java.
    }
    // Podrías tener otros bloques aquí como:
    // buildFeatures {
    //    viewBinding = true // Para habilitar ViewBinding
    //    compose = true    // Para habilitar Jetpack Compose
    // }
    // packagingOptions { ... } // Para resolver conflictos de empaquetado
}

// Bloque 'dependencies': Define las dependencias del módulo.
dependencies {
    // Dependencias básicas de AndroidX y Material Design.
    implementation(libs.androidx.core.ktx) // Extensiones Kotlin para AndroidX Core.
    implementation(libs.androidx.appcompat) // Soporte para versiones antiguas de Android y componentes de UI.
    implementation(libs.material) // Componentes de Material Design.
    implementation(libs.androidx.activity) // Utilidades para Activity.
    implementation(libs.androidx.constraintlayout) // Layout para interfaces complejas.

    // Dependencia para Google Maps.
    implementation(libs.play.services.maps)

    // Dependencia para gráficos (posiblemente para alguna UI personalizada o Compose).
    implementation(libs.androidx.ui.graphics.android) // Asegúrate de que esta es la dependencia correcta que necesitas.

    // Dependencia para Firebase Crashlytics Build Tools (generalmente no es una 'implementation').
    // Esta dependencia se usa más comúnmente como 'classpath' en el build.gradle de nivel de proyecto
    // o como un plugin aplicado aquí, no como una dependencia de implementación directa.
    // Revisa su uso; podría ser 'com.google.firebase:firebase-crashlytics-gradle:versión' como plugin.
    implementation(libs.firebase.crashlytics.buildtools) // TODO: Verificar si esta es la forma correcta de usar Crashlytics build tools.

    // Dependencias para Pruebas.
    testImplementation(libs.junit) // JUnit para pruebas unitarias (locales).
    androidTestImplementation(libs.androidx.junit) // AndroidX Test JUnit para pruebas instrumentadas.
    androidTestImplementation(libs.androidx.espresso.core) // Espresso para pruebas de UI.

    // Dependencia adicional de Material Design (v1.9.0, parece ser una versión específica).
    // 'libs.material' generalmente es suficiente, pero si necesitas una versión específica o un artefacto diferente.
    implementation(libs.material.v190) // Si libs.material ya es v1.9.0 o superior, esta podría ser redundante.

    // Dependencias para Navegación (AndroidX Navigation Component).
    implementation(libs.androidx.navigation.fragment.ktx) // Soporte de navegación para fragmentos con Kotlin.
    implementation(libs.androidx.navigation.ui.ktx)      // Integración de UI con Navigation Component.

    // Dependencia para escaneo de códigos de barras/QR (ZXing "Zebra Crossing").
    implementation(libs.zxing) // Asume que 'libs.zxing' apunta a 'com.journeyapps:zxing-android-embedded' o similar.

    // Dependencia para DrawerLayout (menú lateral).
    implementation(libs.androidx.drawerlayout)

    // Dependencias para Supabase (cliente Kotlin).
    // Bill of Materials (BOM) para Supabase: gestiona las versiones de las librerías de Supabase.
    implementation(platform("io.github.jan-tennert.supabase:bom:3.1.3"))
    implementation("io.github.jan-tennert.supabase:postgrest-kt") // Para interactuar con PostgREST API de Supabase.
    implementation("io.github.jan-tennert.supabase:auth-kt")      // Para autenticación con Supabase.
    implementation("io.github.jan-tennert.supabase:realtime-kt")  // Para funcionalidades en tiempo real de Supabase.

    // Dependencia para Ktor Client Engine (Android).
    // Ktor es un framework para construir clientes/servidores asíncronos.
    // Supabase Kotlin client usa Ktor internamente para las solicitudes de red.
    implementation("io.ktor:ktor-client-android:3.1.1") // La versión debe ser compatible con la BOM de Supabase y Ktor.

    // Dependencia para Supabase PostgREST (Kotlin Multiplatform Client).
    // Esta parece ser una duplicación de "io.github.jan-tennert.supabase:postgrest-kt"
    // que ya se incluye a través de la BOM. Generalmente, con la BOM, no necesitas especificar versiones.
    implementation("io.github.jan-tennert.supabase:postgrest-kt:3.1.3") // TODO: Verificar si esta línea es necesaria o redundante debido a la BOM.

    // Dependencia para Kotlinx Serialization (JSON).
    // Necesaria para serializar/deserializar objetos Kotlin a/desde JSON, usada por Supabase.
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0") // La versión debe ser compatible.

    // Dependencia para Coroutines de Kotlin en Android.
    // Para manejar operaciones asíncronas.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Dependencias para Stripe (pagos).
    implementation(libs.stripe.android) // SDK de Stripe para Android.
    implementation(libs.okhttp)         // OkHttp, a menudo una dependencia transitiva de Stripe o usada para networking.

    // Dependencias para Firebase (notificaciones y analytics).
    // Bill of Materials (BOM) para Firebase: gestiona las versiones de las librerías de Firebase.
    implementation(platform("com.google.firebase:firebase-bom:33.11.0"))
    // Al usar la BOM, no es necesario especificar versiones para las dependencias individuales de Firebase.
    implementation("com.google.firebase:firebase-analytics") // Para Firebase Analytics.
    implementation("com.google.firebase:firebase-messaging") // Para Firebase Cloud Messaging (FCM).

    // TODO: Añadir aquí otras dependencias de Firebase que se necesiten.
    // Comentario original de Firebase:
    // Add the dependencies for any other desired Firebase products
    // https://firebase.google.com/docs/android/setup#available-libraries
}