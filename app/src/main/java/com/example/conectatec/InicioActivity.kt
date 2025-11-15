package com.example.conectatec

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
// import android.os.Looper // No se usa directamente, Handler() sin Looper.getMainLooper() es válido si el contexto es UI
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.widget.Toolbar
// import androidx.fragment.app.Fragment // No se usa directamente en esta clase
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.messaging.FirebaseMessaging
import kotlin.jvm.java
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.os.Build

/**
 * Actividad principal después de que el usuario ha iniciado sesión.
 * <p>
 * Esta actividad actúa como el contenedor principal para la navegación de la aplicación,
 * utilizando un [DrawerLayout] para el menú lateral, un [BottomNavigationView] para la
 * navegación inferior, y un [NavHostFragment] para mostrar los diferentes fragmentos.
 * También maneja la lógica de cierre de sesión, la visualización del ID de usuario,
 * la recepción y manejo de notificaciones de Firebase Cloud Messaging (FCM),
 * y la obtención/almacenamiento del token FCM.
 * </p>
 *
 * @property navController El [NavController] responsable de gestionar la navegación entre fragmentos.
 * @property drawerLayout El [DrawerLayout] que contiene el menú de navegación lateral.
 * @property sharedPreferences Instancia de [android.content.SharedPreferences] para gestionar datos de sesión y el token FCM.
 * @property handler Un [Handler] opcional, aunque no se utiliza explícitamente en el código actual,
 *                   se declara y se limpia en `logout`. Podría ser para tareas futuras.
 * @property TAG Etiqueta para los logs de esta actividad, con valor "InicioActivity".
 *
 * @author Ortiz Gallegos Starenka Susana
 * @author Salgado Rojas Marelin Iral
 * @author Orozco Reyes Hiram
 * @author Ortiz Ceballos Jorge
 * @version 1.15
 * @since 22 Marzo 2025
 * @see Login_activity
 * @see Constantes
 * @see NotificacionesFragment
 * @see MyFirebaseMessagingService
 */
class InicioActivity : AppCompatActivity() {

    private lateinit var navController: NavController
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var sharedPreferences: android.content.SharedPreferences
    private var handler: Handler? = null // Handler opcional, se limpia en logout
    private val TAG = "InicioActivity" // Tag para logging
    // ===== NUEVO: Registrador para solicitar permiso de notificaciones =====
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.d(TAG, "Permiso POST_NOTIFICATIONS otorgado por el usuario")
            Toast.makeText(this, "Permiso para notificaciones otorgado", Toast.LENGTH_SHORT).show()
        } else {
            Log.d(TAG, "Permiso POST_NOTIFICATIONS denegado por el usuario")
            Toast.makeText(this, "Permiso para notificaciones denegado", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Se llama cuando la actividad está iniciando.
     * <p>
     * Realiza las siguientes tareas:
     * <ul>
     *   <li>Establece el layout de la actividad.</li>
     *   <li>Inicializa [SharedPreferences].</li>
     *   <li>Configura la [Toolbar] y el [ActionBarDrawerToggle] para el [DrawerLayout].</li>
     *   <li>Muestra el ID del usuario (obtenido de SharedPreferences) en el encabezado del [NavigationView].</li>
     *   <li>Configura el listener para los ítems del [NavigationView] (ej. Logout).</li>
     *   <li>Configura el [NavController] con el [NavHostFragment] y lo vincula al [BottomNavigationView].</li>
     *   <li>Verifica si la actividad fue iniciada con la intención de navegar a un fragmento específico
     *       (ej. "walletFragment" después de un pago, o "NotificacionesFragment" desde una notificación).</li>
     *   <li>Obtiene y almacena el token de registro de Firebase Cloud Messaging (FCM).</li>
     * </ul>
     * </p>
     * @param savedInstanceState Si la actividad se está re-inicializando después de haber sido
     *                           apagada previamente, este Bundle contiene los datos que más
     *                           recientemente suministró en [onSaveInstanceState].
     *                           De lo contrario es nulo.
     */

    // Lanzador para pedir el permiso de notificaciones
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Toast.makeText(this, "✅ Notificaciones activadas correctamente", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "⚠️ Las notificaciones están desactivadas", Toast.LENGTH_LONG).show()
            }
        }

    // Método que pide el permiso de notificaciones
    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    Log.d("Permisos", "Permiso de notificaciones ya concedido")
                }
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    Toast.makeText(this, "Activa las notificaciones para recibir alertas importantes", Toast.LENGTH_LONG).show()
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                else -> {
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_inicio)

        // Inicializar SharedPreferences para acceder a los datos de la aplicación.
        sharedPreferences = getSharedPreferences(Constantes.PREFS_NAME, MODE_PRIVATE)

        // Configurar la Toolbar como ActionBar de la actividad.
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "" // Elimina el título por defecto del Toolbar para un diseño más limpio.

        // Configurar el DrawerLayout y el NavigationView para el menú lateral.
        drawerLayout = findViewById(R.id.drawer_layout)
        val navView: NavigationView = findViewById(R.id.nav_view)

        // Configurar el ActionBarDrawerToggle para manejar la apertura y cierre del drawer.
        val toggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar,
            R.string.navigation_drawer_open, R.string.navigation_drawer_close // Strings para accesibilidad
        )
        toggle.drawerArrowDrawable.color = Color.WHITE
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState() // Sincroniza el estado del indicador del drawer (icono de hamburguesa).

        // Mostrar el ID del usuario en el encabezado del NavigationView.
        val headerView = navView.getHeaderView(0) // Obtiene la vista del encabezado del drawer.
        val userNameTextView = headerView.findViewById<TextView>(R.id.nav_header_user_name)
        val userId = sharedPreferences.getString(Constantes.KEY_USER_ID, "Usuario") // Obtiene ID o "Usuario" por defecto.
        userNameTextView.text = userId

        // Configurar el listener para los ítems de menú del NavigationView.
        navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_logout -> {
                    logout() // Llama al método para cerrar sesión.
                    true // Indica que el evento ha sido manejado.
                }
                R.id.nav_editar->{
                    val intent = Intent(this, Perfil_Activity::class.java)
                    startActivity(intent)
                    true
                }

                else -> false // Para otros ítems, no se maneja aquí.
            }
        }

        /* // Bloque de código comentado original:
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_inicio)

            // Configura el NavController
            val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
            navController = navHostFragment.navController

            // Vincula la barra de navegación al NavController
            findViewById<BottomNavigationView>(R.id.bottom_navigation).setupWithNavController(navController)
        }*/

        // Configura el NavController a partir del NavHostFragment definido en el layout.
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        // Vincula el BottomNavigationView con el NavController para la navegación inferior.
        findViewById<BottomNavigationView>(R.id.bottom_navigation).setupWithNavController(navController)

        // Verifica si la actividad fue iniciada con la intención de navegar al walletFragment.
        // Esto puede ocurrir después de un proceso de pago exitoso.
        if (intent.getBooleanExtra("navigate_to_wallet", false)) {
            findViewById<BottomNavigationView>(R.id.bottom_navigation).selectedItemId = R.id.nav_wallet // Asume que R.id.nav_wallet existe.
        }

        // ===== INICIO: CÓDIGO PARA MANEJO DE NOTIFICACIONES FCM =====

        // Verifica si la actividad fue iniciada o reanudada a través de una notificación.
        handleNotificationIntent(intent)
        // ===== NUEVO: Solicitar permiso de notificaciones ANTES de obtener el token =====
        requestNotificationPermission()
        // Obtiene el token de registro FCM actual y lo guarda en SharedPreferences.
        // Este token es necesario para que el dispositivo reciba notificaciones push.
        retrieveAndStoreFirebaseToken()
        FirebaseMessaging.getInstance().subscribeToTopic("general")
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d("FCM", "Suscrito exitosamente al tópico 'general'")
                } else {
                    Log.e("FCM", "Error al suscribirse al tópico", task.exception)
                }
            }
        // ===== FIN: CÓDIGO PARA MANEJO DE NOTIFICACIONES FCM =====


            askNotificationPermission()



        // ===== FIN: CÓDIGO PARA MANEJO DE NOTIFICACIONES FCM =====
    }

    // ===== INICIO: MÉTODOS PARA MANEJO DE NOTIFICACIONES FCM =====

    /**
     * Se llama cuando la actividad está siendo re-lanzada mientras ya está en la parte
     * superior de la pila de actividades.
     * Esto puede suceder si la actividad se inicia de nuevo (por ejemplo, desde una notificación)
     * mientras ya está abierta.
     *
     * @param intent El nuevo [Intent] que inició la actividad.
     */
    // ===== NUEVO: Método para solicitar permiso de notificaciones =====
    private fun requestNotificationPermission() {
        // Solo solicitar en Android 13 (API 33) o superior
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Verificar si el permiso ya fue concedido
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                Log.d(TAG, "Solicitando permiso POST_NOTIFICATIONS...")
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                Log.d(TAG, "Permiso POST_NOTIFICATIONS ya fue otorgado anteriormente")
            }
        } else {
            Log.d(TAG, "Android version menor a 13, no se requiere solicitar permiso en tiempo de ejecución")
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Actualiza el intent de la actividad con el nuevo intent recibido.
        setIntent(intent)
        // Procesa el intent para manejar posibles acciones de notificación.
        handleNotificationIntent(intent)
    }

    /**
     * Procesa el [Intent] de entrada para determinar si se debe navegar a un fragmento específico
     * debido a una notificación.
     * <p>
     * Busca un extra "openFragment" con el valor "notificaciones". Si se encuentra,
     * intenta navegar al [NotificacionesFragment].
     * </p>
     * @param intent El [Intent] que podría contener datos de una notificación.
     */
    private fun handleNotificationIntent(intent: Intent) {
        val fromNotification = intent.getStringExtra("openFragment")

        if (fromNotification == "notificaciones") {
            Log.d(TAG, "Abriendo desde notificación, navegando a fragmento de notificaciones.")
            try {
                navigateToNotificacionesFragment()
            } catch (e: Exception) {
                Log.e(TAG, "Error al intentar navegar al fragmento de notificaciones desde handleNotificationIntent.", e)
            }
        }
    }

    /**
     * Intenta navegar al fragmento de notificaciones.
     * <p>
     * Prueba varios métodos para lograr la navegación:
     * <ol>
     *   <li>Usando el [NavController] si está inicializado y la ruta `R.id.nav_notifications` existe en el grafo.</li>
     *   <li>Usando una transacción de [androidx.fragment.app.FragmentManager] para reemplazar el contenido del `nav_host_fragment`.</li>
     *   <li>Seleccionando el ítem correspondiente en el [BottomNavigationView] si existe.</li>
     * </ol>
     * Registra errores si alguno de los métodos falla.
     * </p>
     * @see R.id.nav_notifications
     * @see R.id.nav_host_fragment
     * @see NotificacionesFragment
     */
    private fun navigateToNotificacionesFragment() {
        // Intento 1: Usar NavController.
        try {
            if (::navController.isInitialized && navController.graph.findNode(R.id.nav_notifications) != null) {
                navController.navigate(R.id.nav_notifications)
                Log.i(TAG, "Navegación a NotificacionesFragment iniciada con NavController.")
                return
            } else {
                Log.w(TAG, "NavController no inicializado o R.id.nav_notifications no encontrado en el grafo.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al navegar a NotificacionesFragment usando NavController.", e)
        }

        // Intento 2: Usar FragmentManager directamente.
        try {
            supportFragmentManager.beginTransaction()
                .replace(R.id.nav_host_fragment, NotificacionesFragment()) // Asume que NotificacionesFragment() es el constructor correcto.
                .addToBackStack(null) // Permite volver al fragmento anterior.
                .commit()
            Log.i(TAG, "Navegación a NotificacionesFragment iniciada con FragmentManager.")
            return
        } catch (e: Exception) {
            Log.e(TAG, "Error al navegar a NotificacionesFragment usando FragmentManager.", e)
        }

        // Intento 3: Usar BottomNavigationView.
        try {
            val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
            if (bottomNav.menu.findItem(R.id.nav_notifications) != null) {
                bottomNav.selectedItemId = R.id.nav_notifications
                Log.i(TAG, "Navegación a NotificacionesFragment iniciada con BottomNavigationView.")
            } else {
                Log.w(TAG, "R.id.nav_notifications no encontrado en BottomNavigationView.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo navegar a NotificacionesFragment usando BottomNavigationView.", e)
            Toast.makeText(this, "Error al abrir notificaciones.", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Obtiene el token de registro de Firebase Cloud Messaging (FCM) y lo almacena
     * en [SharedPreferences].
     * <p>
     * Este token es único para la instancia de la aplicación en el dispositivo y se utiliza
     * para enviar notificaciones push dirigidas.
     * También registra el token en Logcat y prevé un placeholder para enviarlo a un servidor backend.
     * </p>
     * @see FirebaseMessaging
     * @see sendRegistrationToServer
     */
    private fun retrieveAndStoreFirebaseToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w(TAG, "Fetching FCM registration token failed", task.exception)
                return@addOnCompleteListener
            }

            // Obtiene el nuevo token FCM.
            val token = task.result

            // Guarda el token en SharedPreferences.
            // Se usa un archivo de preferencias diferente ("app_prefs") para el token FCM.
            // Considerar si debe ser el mismo que Constantes.PREFS_NAME o uno dedicado.
            val fcmPrefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
            fcmPrefs.edit().putString("fcm_token", token).apply()

            Log.d(TAG, "FCM Token: $token")

            // Opcional: Enviar este token a tu servidor para la gestión de notificaciones.
            // sendRegistrationToServer(token)
        }
    }

    /**
     * Placeholder para un método que enviaría el token de registro FCM al servidor backend.
     * <b>Debe ser implementado según las necesidades del backend.</b>
     *
     * @param token El token FCM a enviar.
     */
    private fun sendRegistrationToServer(token: String) {
        // TODO: Implementar la lógica para enviar el token FCM al servidor de la aplicación.
        // Ejemplo: Realizar una solicitud de red a tu API.
        Log.d(TAG, "Simulando envío de token al servidor: $token")
    }

    // ===== FIN: MÉTODOS PARA MANEJO DE NOTIFICACIONES FCM =====

    /**
     * Cierra la sesión del usuario actual.
     * <p>
     * Realiza las siguientes acciones:
     * <ul>
     *   <li>Limpia cualquier callback o mensaje pendiente del [handler] (si está inicializado).</li>
     *   <li>Elimina el estado de inicio de sesión ([Constantes.KEY_IS_LOGGED_IN]) y el ID de usuario
     *       ([Constantes.KEY_USER_ID]) de [SharedPreferences].</li>
     *   <li>Muestra un [Toast] informando que la sesión ha sido cerrada.</li>
     *   <li>Redirige al usuario a [Login_activity] y finaliza la [InicioActivity] actual,
     *       limpiando la pila de actividades para prevenir el retorno mediante el botón "Atrás".</li>
     * </ul>
     * </p>
     * @see Constantes
     * @see Login_activity
     */
    private fun logout() {
        // Limpia el handler si ha sido inicializado, para detener tareas en segundo plano.
        handler?.removeCallbacksAndMessages(null)

        // Limpia los datos de sesión de SharedPreferences.
        val editor = sharedPreferences.edit()
        editor.putBoolean(Constantes.KEY_IS_LOGGED_IN, false)
        editor.remove(Constantes.KEY_USER_ID)
        // Podrías considerar también limpiar el token FCM aquí si el usuario no debería recibir notificaciones
        // tras cerrar sesión, o si el token está ligado al usuario y no al dispositivo/app.
        // editor.remove("fcm_token") // Ejemplo si se guarda en el mismo SharedPreferences.
        editor.apply()

        Toast.makeText(this, "Sesión cerrada", Toast.LENGTH_SHORT).show()

        // Redirige a la actividad de inicio de sesión.
        val intent = Intent(this, Login_activity::class.java)
        // Estas flags aseguran que se cree una nueva tarea para LoginActivity y se limpie la pila anterior.
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish() // Finaliza InicioActivity para que el usuario no pueda volver a ella.
    }

    /**
     * Se llama cuando se presiona el botón "Atrás".
     * <p>
     * Si el [DrawerLayout] (menú lateral) está abierto, lo cierra.
     * De lo contrario, realiza la acción predeterminada del botón "Atrás" (generalmente,
     * navegar hacia atrás en la pila de fragmentos o cerrar la actividad si no hay más
     * entradas en la pila).
     * </p>
     */
    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START) // Cierra el drawer si está abierto.
        } else {
            super.onBackPressed() // Comportamiento por defecto.
        }
    }
}