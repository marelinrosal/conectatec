package com.example.conectatec

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.messaging.FirebaseMessaging

class InicioActivity : AppCompatActivity() {

    private lateinit var navController: NavController
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var sharedPreferences: android.content.SharedPreferences
    private var handler: Handler? = null // Handler opcional
    private val TAG = "InicioActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_inicio)

        // Inicializar SharedPreferences
        sharedPreferences = getSharedPreferences(Constantes.PREFS_NAME, MODE_PRIVATE)

        // Configurar Toolbar
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "" // Eliminar "ConectaTec" del Toolbar

        // Configurar DrawerLayout
        drawerLayout = findViewById(R.id.drawer_layout)
        val navView: NavigationView = findViewById(R.id.nav_view)

        // Configurar ActionBarDrawerToggle
        val toggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar,
            R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        // Mostrar el ID del usuario en el encabezado
        val headerView = navView.getHeaderView(0)
        val userNameTextView = headerView.findViewById<TextView>(R.id.nav_header_user_name)
        val userId = sharedPreferences.getString(Constantes.KEY_USER_ID, "Usuario")
        userNameTextView.text = userId

        // Configurar listener para el NavigationView
        navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_logout -> {
                    logout()
                    true
                }
                else -> false
            }
        }

        /*override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_inicio)

            // Configura el NavController
            val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
            navController = navHostFragment.navController

            // Vincula la barra de navegación al NavController
            findViewById<BottomNavigationView>(R.id.bottom_navigation).setupWithNavController(navController)
        }*/
        // Configura el NavController
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        // Vincula la barra de navegación al NavController
        findViewById<BottomNavigationView>(R.id.bottom_navigation).setupWithNavController(navController)

        // Verificar si venimos de un pago exitoso y debemos navegar al walletFragment
        if (intent.getBooleanExtra("navigate_to_wallet", false)) {
            findViewById<BottomNavigationView>(R.id.bottom_navigation).selectedItemId = R.id.nav_wallet
        }

        // ===== INICIO: CÓDIGO NUEVO PARA NOTIFICACIONES =====

        // Verificar si venimos de una notificación
        handleNotificationIntent(intent)

        // Obtener el token FCM actual y guardarlo en SharedPreferences
        retrieveAndStoreFirebaseToken()

        // ===== FIN: CÓDIGO NUEVO PARA NOTIFICACIONES =====
    }

    // ===== INICIO: MÉTODOS NUEVOS PARA NOTIFICACIONES =====

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Manejar si la actividad ya estaba abierta y se recibió una nueva intención
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent) {
        val fromNotification = intent.getStringExtra("openFragment")

        if (fromNotification == "notificaciones") {
            Log.d(TAG, "Abriendo desde notificación, navegando a fragmento de notificaciones")
            // Aquí puedes usar NavController o soporte de fragmentos directo según tu implementación
            try {
                navigateToNotificacionesFragment()
            } catch (e: Exception) {
                Log.e(TAG, "Error al navegar al fragmento de notificaciones", e)
            }
        }
    }

    private fun navigateToNotificacionesFragment() {
        // Intenta usar NavController si tienes una ruta definida
        try {
            if (::navController.isInitialized && navController.graph.findNode(R.id.nav_notifications) != null) {
                navController.navigate(R.id.nav_notifications)
                return
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error usando NavController", e)
        }

        // Si no funciona con NavController, intentamos con transacción de fragmentos directa
        try {
            supportFragmentManager.beginTransaction()
                .replace(R.id.nav_host_fragment, NotificacionesFragment())
                .addToBackStack(null)
                .commit()
        } catch (e: Exception) {
            Log.e(TAG, "Error usando FragmentManager", e)

            // Última opción: intentar usar BottomNavigationView
            try {
                val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
                if (bottomNav.menu.findItem(R.id.nav_notifications) != null) {
                    bottomNav.selectedItemId = R.id.nav_notifications
                }
            } catch (e: Exception) {
                Log.e(TAG, "No se pudo navegar a notificaciones", e)
            }
        }
    }

    private fun retrieveAndStoreFirebaseToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w(TAG, "Fetching FCM registration token failed", task.exception)
                return@addOnCompleteListener
            }

            // Obtener el token
            val token = task.result

            // Guardar el token en SharedPreferences
            val fcmPrefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
            fcmPrefs.edit().putString("fcm_token", token).apply()

            Log.d(TAG, "FCM Token: $token")

            // Aquí podrías enviar el token a tu servidor si lo necesitas
            // sendRegistrationToServer(token)
        }
    }

    // Método para enviar el token al servidor (implementa según necesites)
    private fun sendRegistrationToServer(token: String) {
        // TODO: Implementar envío del token a tu servidor
        Log.d(TAG, "Token enviado al servidor: $token")
    }

    // ===== FIN: MÉTODOS NUEVOS PARA NOTIFICACIONES =====

    private fun logout() {
        // Limpiar Handler si está inicializado
        handler?.removeCallbacksAndMessages(null)

        // Limpiar SharedPreferences
        val editor = sharedPreferences.edit()
        editor.putBoolean(Constantes.KEY_IS_LOGGED_IN, false)
        editor.remove(Constantes.KEY_USER_ID)
        editor.apply()

        // Mostrar mensaje
        Toast.makeText(this, "Sesión cerrada", Toast.LENGTH_SHORT).show()

        // Redirigir a Login_activity
        val intent = Intent(this, Login_activity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
}