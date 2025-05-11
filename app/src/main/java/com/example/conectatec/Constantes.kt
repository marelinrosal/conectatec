package com.example.conectatec

/**
 * Almacena constantes globales utilizadas en toda la aplicación.
 *
 * Este objeto singleton sirve como un repositorio centralizado para valores
 * constantes, principalmente claves para [android.content.SharedPreferences]
 * y otras configuraciones fijas, facilitando su gestión y evitando errores
 * por cadenas de texto "mágicas" dispersas en el código.
 *
 * @author Ortiz Gallegos Starenka Susana
 * @author Salgado Rojas Marelin Iral
 * @author Orozco Reyes Hiram
 * @author Ortiz Ceballos Jorge
 * @version 1.15
 * @since 22 Marzo 2025
 */
object Constantes {

    /**
     * Nombre del archivo de SharedPreferences utilizado para almacenar
     * la información de la sesión del usuario.
     * El valor es `AppSessionPrefs`.
     */
    const val PREFS_NAME = "AppSessionPrefs"

    /**
     * Clave utilizada en SharedPreferences para almacenar el estado
     * de inicio de sesión del usuario.
     * Se espera que el valor asociado sea un [Boolean].
     * El valor es `isLoggedIn`.
     *
     * @see PREFS_NAME
     */
    const val KEY_IS_LOGGED_IN = "isLoggedIn"

    /**
     * Clave utilizada en SharedPreferences para almacenar el identificador
     * único (ID) del usuario que ha iniciado sesión.
     * Se espera que el valor asociado sea un [String].
     * El valor es `userId`.
     *
     * @see PREFS_NAME
     */
    const val KEY_USER_ID = "userId"
}