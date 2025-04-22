package com.example.conectatec // <-- CAMBIO AQUÍ: Sin el .util

/**
 * Objeto para almacenar constantes globales de la aplicación,
 * como las claves de SharedPreferences.
 */
object Constantes {
    // Nombre del archivo de SharedPreferences para la sesión
    const val PREFS_NAME = "AppSessionPrefs"

    // Clave para guardar el estado de inicio de sesión (Boolean)
    const val KEY_IS_LOGGED_IN = "isLoggedIn"

    // Clave para guardar el ID del usuario logueado (String)
    const val KEY_USER_ID = "userId"
}