// Top-level build file where you can add configuration options common to all sub-projects/modules.

// El bloque 'plugins' define los plugins de Gradle que se utilizarán en el proyecto.
// Estos plugins se declaran aquí pero se aplican (o no) en los archivos build.gradle.kts de cada módulo.
plugins {
    // Declara el plugin de aplicación Android.
    // 'alias(libs.plugins.android.application)' se refiere a una versión definida en el archivo `libs.versions.toml` (version catalog).
    // 'apply false' significa que este plugin no se aplica automáticamente a este script de build de nivel de proyecto,
    // sino que está disponible para ser aplicado en los módulos (ej. el módulo 'app').
    alias(libs.plugins.android.application) apply false

    // Declara el plugin de Kotlin para Android.
    // Similar al plugin de aplicación, se define aquí para su uso en módulos.
    alias(libs.plugins.kotlin.android) apply false

    // Declara el plugin de Google Services.
    // Este plugin es necesario para integrar servicios de Firebase (como FCM, Analytics, etc.) en tu aplicación Android.
    // 'id("com.google.gms.google-services")' es el identificador del plugin.
    // 'version "4.4.2"' especifica la versión del plugin de Google Services a utilizar.
    // 'apply false' indica que este plugin tampoco se aplica a nivel de proyecto, sino que debe ser
    // aplicado en el archivo build.gradle.kts del módulo de la aplicación donde se usan los servicios de Google.
    id("com.google.gms.google-services") version "4.4.3" apply false

    id("org.jetbrains.dokka") version "2.0.0"

    // Podrías tener otros plugins declarados aquí, por ejemplo:
    // - Para Kotlin Serialization si lo usas a nivel de proyecto (aunque más común a nivel de módulo).
    // - Para Hilt o Koin para inyección de dependencias.
    // - Para Safe Args para navegación.
    // - etc.
}
subprojects {
    apply(plugin = "org.jetbrains.dokka")
}
// Configuración para la documentación AGREGADA de todos los módulos
tasks.dokkaHtmlMultiModule {
    outputDirectory.set(layout.buildDirectory.dir("docs/html"))// Carpeta de salida para la documentación global
    moduleName.set("Documentación del Proyecto ConectaTec") // Nombre para la página principal de la documentación
}

// Si quisieras usar otro formato como GFM (GitHub Flavored Markdown):
// tasks.dokkaGfmMultiModule {
//     outputDirectory.set(rootProject.buildDir.resolve("docs/gfm"))
// }


// Generalmente, este archivo también puede contener un bloque 'buildscript' (en Groovy)
// o su equivalente en Kotlin DSL para definir repositorios y dependencias
// necesarias para los propios plugins de Gradle, aunque con los version catalogs
// esto se maneja de forma más centralizada.

// También puede haber un bloque 'allprojects' o 'subprojects' para aplicar
// configuraciones comunes a todos los módulos (ej. repositorios Maven).
// Ejemplo (comentado, ya que no está en tu original):
/*
allprojects {
    repositories {
        google()
        mavenCentral()
        // Otros repositorios como jitpack.io, etc.
    }
}
*/

// Información de autoría para el proyecto en general (no es una práctica estándar de Gradle,
// pero se puede añadir como un comentario general si se desea para la documentación del proyecto).
/*
  Proyecto: ConectaTec (Asumiendo el nombre de tu app)

  Autores del proyecto:
  - Ortiz Gallegos Starenka Susana
  - Salgado Rojas Marelin Iral
  - Orozco Reyes Hiram
  - Ortiz Ceballos Jorge

  Versión del proyecto (según metadatos): 1.15
  Fecha de referencia (según metadatos): 22 Marzo 2025
*/