package com.example.conectatec

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
// import com.example.conectatec.util.Constants // <-- Asumiendo que Constantes está en el mismo paquete o importado correctamente.

/**
 * Fragmento principal de la aplicación que muestra la pantalla de inicio.
 * <p>
 * Este fragmento incluye un carrusel de imágenes con auto-desplazamiento,
 * botones para acceder a redes sociales, un botón para el sistema de pagos y
 * la funcionalidad de cierre de sesión. También verifica el estado de la sesión
 * del usuario al crearse.
 * </p>
 * <p>
 * Utiliza [SharedPreferences] para gestionar el estado de la sesión del usuario,
 * leyendo y escribiendo datos a través del objeto [Constantes].
 * </p>
 *
 * @property viewPager El [ViewPager2] utilizado para el carrusel de imágenes.
 * @property handler El [Handler] utilizado para gestionar el auto-desplazamiento del carrusel.
 * @property sharedPreferences Instancia de [SharedPreferences] para la gestión de la sesión.
 * @property currentPage Índice de la página actual visible en el carrusel, ajustado para el bucle infinito.
 * @property imagenesOriginales Lista de recursos de imágenes drawable que se mostrarán originalmente en el carrusel.
 * @property imagenesCarrusel Lista de imágenes adaptada para el carrusel, con elementos duplicados al inicio y al final
 *                            para permitir un efecto de bucle infinito.
 *
 * @author Ortiz Gallegos Starenka Susana
 * @author Salgado Rojas Marelin Iral
 * @author Orozco Reyes Hiram
 * @author Ortiz Ceballos Jorge
 * @version 1.15
 * @since 22 Marzo 2025
 * @see Constantes
 * @see Login_activity
 * @see SistemaPagosActivity
 */
class homeFragment : Fragment() {
    private lateinit var viewPager: ViewPager2
    private lateinit var handler: Handler
    private lateinit var sharedPreferences: SharedPreferences
    private var currentPage = 1 // Inicia en 1 debido a la imagen duplicada al principio para el bucle

    /** Lista original de IDs de recursos drawable para el carrusel. */
    private val imagenesOriginales = listOf(
        R.drawable.aviso4, R.drawable.aviso5, R.drawable.aviso1,
        R.drawable.aviso2, R.drawable.aviso6, R.drawable.aviso7,
    )

    /**
     * Lista de imágenes preparada para el carrusel [ViewPager2].
     * <p>
     * Si [imagenesOriginales] no está vacía, se añade el último elemento original al principio
     * y el primer elemento original al final de la lista. Esto permite la ilusión de un
     * carrusel infinito. Si [imagenesOriginales] está vacía, devuelve una lista vacía.
     * </p>
     * Ejemplo: si original es [A, B, C], carrusel será [C, A, B, C, A].
     */
    private val imagenesCarrusel: List<Int>
        get() = if (imagenesOriginales.isNotEmpty()) {
            listOf(imagenesOriginales.last()) + imagenesOriginales + listOf(imagenesOriginales.first())
        } else {
            emptyList()
        }

    /**
     * Se llama para que el fragmento instancie su vista de interfaz de usuario.
     * Inicializa [sharedPreferences] y luego infla el layout del fragmento.
     *
     * @param inflater El [LayoutInflater] para inflar vistas.
     * @param container El [ViewGroup] padre al que se adjuntará la UI del fragmento.
     * @param savedInstanceState Estado previamente guardado, si existe.
     * @return La [View] raíz para la UI del fragmento.
     * @see Constantes.PREFS_NAME
     */
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        // Inicializa SharedPreferences para acceder a los datos de sesión.
        sharedPreferences = requireActivity().getSharedPreferences(Constantes.PREFS_NAME, Context.MODE_PRIVATE)
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    /**
     * Se llama inmediatamente después de que [onCreateView] ha retornado, pero antes de
     * que cualquier estado guardado haya sido restaurado en la vista.
     * <p>
     * Configura la UI:
     * - Verifica el estado de inicio de sesión. Si no hay ID de usuario, cierra la sesión.
     * - Configura el listener para el botón de cerrar sesión.
     * - Inicializa el carrusel de imágenes ([ViewPager2]) con su adaptador y auto-desplazamiento.
     * - Configura el listener para el botón de pagos.
     * - Configura los listeners para los botones de redes sociales.
     * </p>
     *
     * @param view La [View] devuelta por [onCreateView].
     * @param savedInstanceState Estado previamente guardado, si existe.
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val loggedInUserId = getLoggedInUserId()
        if (loggedInUserId != null) {
            Log.d("SessionData", "Usuario logueado con ID: $loggedInUserId")
            // Muestra un Toast con el ID del usuario, puede ser útil para depuración.
            Toast.makeText(requireContext(), "ID Usuario: $loggedInUserId", Toast.LENGTH_SHORT).show()
        } else {
            // Si no se encuentra el ID de usuario, se considera que la sesión no es válida.
            Log.w("SessionData", "ID de usuario no encontrado. Cerrando sesión.")
            logout()
            return // Evita continuar con la configuración si se cierra la sesión.
        }

        // Configuración del botón/texto para cerrar sesión.
        val lblCerrar = view.findViewById<TextView>(R.id.lblcerrar)
        lblCerrar.setOnClickListener {
            logout()
        }

        // Configuración del ViewPager2 para el carrusel de imágenes.
        viewPager = view.findViewById(R.id.imagenesSlide)
        if (imagenesCarrusel.isNotEmpty()) {
            viewPager.adapter = ViewPagerAdapter(imagenesCarrusel) // Asume que ViewPagerAdapter existe
            viewPager.setCurrentItem(currentPage, false) // Establece la página inicial sin animación
            handler = Handler(Looper.getMainLooper())
            startAutoSlide()
            viewPager.registerOnPageChangeCallback(viewPagerCallback)
        } else {
            Log.w("HomeFragment", "Lista de imágenes para carrusel vacía. Ocultando ViewPager.")
            viewPager.visibility = View.GONE // Oculta el ViewPager si no hay imágenes
        }

        // Configuración del botón para ir a la actividad de sistema de pagos.
        val btnPagos: Button = view.findViewById(R.id.BotonCompra)
        btnPagos.setOnClickListener {
            val intent = Intent(requireContext(), SistemaPagosActivity::class.java)
            startActivity(intent)
        }

        // Configuración del RadioGroup para los botones de redes sociales.
        val grupo: RadioGroup = view.findViewById(R.id.grupoBotones)
        grupo.setOnCheckedChangeListener { rg, checkedId ->
            // Desmarca la selección después de un breve retraso para simular un botón normal.
            Handler(Looper.getMainLooper()).postDelayed({ rg.clearCheck() }, 200)
            when (checkedId) {
                R.id.Facebook -> openUrl("https://www.facebook.com/trenelinsurgente", "Facebook")
                R.id.X -> openUrl("https://x.com/TrenInsurgente", "X (Twitter)")
                R.id.tikTok -> openUrl("https://www.tiktok.com/@trenelinsurgente", "TikTok")
                R.id.instagram -> openUrl("https://www.instagram.com/trenelinsurgente/", "Instagram")
                R.id.youtube -> openUrl("https://www.youtube.com/@trenelinsurgente", "YouTube")
            }
        }
    }

    /**
     * Callback para el [ViewPager2] que maneja la lógica del carrusel infinito y
     * reinicia el auto-desplazamiento cuando el usuario interactúa manualmente.
     */
    private val viewPagerCallback = object : ViewPager2.OnPageChangeCallback() {
        /**
         * Se llama cuando se selecciona una nueva página.
         * Reinicia el temporizador de auto-desplazamiento.
         * @param position La posición de la página recién seleccionada.
         */
        override fun onPageSelected(position: Int) {
            super.onPageSelected(position)
            if (::handler.isInitialized) { // Asegura que el handler esté inicializado
                restartAutoSlide()
            }
        }

        /**
         * Se llama cuando cambia el estado de desplazamiento.
         * Implementa la lógica para el efecto de carrusel infinito.
         * Cuando el usuario llega a las imágenes "falsas" (duplicadas al inicio/final),
         * salta silenciosamente a la imagen real correspondiente.
         * @param state El nuevo estado de desplazamiento.
         * @see ViewPager2.SCROLL_STATE_IDLE
         * @see ViewPager2.SCROLL_STATE_DRAGGING
         * @see ViewPager2.SCROLL_STATE_SETTLING
         */
        override fun onPageScrollStateChanged(state: Int) {
            super.onPageScrollStateChanged(state)
            // Solo actuar si el carrusel está inactivo y hay imágenes
            if (state == ViewPager2.SCROLL_STATE_IDLE && imagenesCarrusel.isNotEmpty()) {
                val lastRealPage = imagenesCarrusel.size - 2 // Índice de la última imagen "real"
                val firstRealPage = 1                       // Índice de la primera imagen "real"

                when (viewPager.currentItem) {
                    0 -> { // Si está en la primera imagen (clon de la última)
                        currentPage = lastRealPage
                        viewPager.setCurrentItem(currentPage, false) // Salta a la última imagen real
                    }
                    imagenesCarrusel.size - 1 -> { // Si está en la última imagen (clon de la primera)
                        currentPage = firstRealPage
                        viewPager.setCurrentItem(currentPage, false) // Salta a la primera imagen real
                    }
                    else -> { // Si está en una imagen real
                        currentPage = viewPager.currentItem
                    }
                }
            }
        }
    }

    /**
     * Obtiene el ID del usuario logueado desde [SharedPreferences].
     *
     * @return El ID del usuario como [String], o `null` si no se encuentra o no hay sesión.
     * @see Constantes.KEY_USER_ID
     */
    private fun getLoggedInUserId(): String? {
        return sharedPreferences.getString(Constantes.KEY_USER_ID, null)
    }

    /**
     * Cierra la sesión del usuario actual.
     * <p>
     * Limpia los datos de sesión de [SharedPreferences] (estado de login y ID de usuario),
     * detiene cualquier tarea pendiente del [handler] (como el auto-desplazamiento),
     * muestra un mensaje de "Sesión cerrada", y redirige al usuario a [Login_activity],
     * finalizando la actividad actual.
     * </p>
     * @see Constantes.KEY_IS_LOGGED_IN
     * @see Constantes.KEY_USER_ID
     * @see Login_activity
     */
    private fun logout() {
        // Detiene el auto-desplazamiento del carrusel si está activo.
        if (::handler.isInitialized) {
            handler.removeCallbacksAndMessages(null)
        }

        // Limpia los datos de sesión en SharedPreferences.
        val editor = sharedPreferences.edit()
        editor.putBoolean(Constantes.KEY_IS_LOGGED_IN, false)
        editor.remove(Constantes.KEY_USER_ID)
        editor.apply()

        Toast.makeText(requireContext(), "Sesión cerrada", Toast.LENGTH_SHORT).show()

        // Redirige a la pantalla de login y limpia la pila de actividades.
        val intent = Intent(requireContext(), Login_activity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        requireActivity().finish() // Finaliza la actividad actual (InicioActivity que contiene este fragmento)
    }

    /**
     * Inicia el auto-desplazamiento del carrusel [ViewPager2].
     * Publica un [slideRunnable] en el [handler] con un retraso.
     * No hace nada si el handler no está inicializado o si no hay imágenes.
     */
    private fun startAutoSlide() {
        if (!::handler.isInitialized || imagenesCarrusel.isEmpty()) return
        handler.postDelayed(slideRunnable, 3000) // 3000 ms = 3 segundos
    }

    /**
     * Reinicia el auto-desplazamiento del carrusel.
     * Elimina cualquier [slideRunnable] pendiente y publica uno nuevo.
     * Útil cuando el usuario interactúa manualmente con el carrusel.
     */
    private fun restartAutoSlide() {
        if (::handler.isInitialized) {
            handler.removeCallbacks(slideRunnable)
            handler.postDelayed(slideRunnable, 3000)
        }
    }

    /**
     * [Runnable] que se ejecuta periódicamente para cambiar la página del [ViewPager2],
     * creando el efecto de auto-desplazamiento.
     * Si la vista del fragmento ya no existe o el adaptador es nulo/vacío, se detiene.
     */
    private val slideRunnable = object : Runnable {
        override fun run() {
            // Comprobación de seguridad para evitar NullPointerExceptions si el fragmento se destruye
            // o el adaptador del ViewPager no está listo.
            if (view == null || viewPager.adapter == null || viewPager.adapter?.itemCount == 0) {
                if(::handler.isInitialized) handler.removeCallbacks(this) // Detener si no es seguro continuar
                return
            }

            currentPage++ // Avanza a la siguiente página teórica
            viewPager.setCurrentItem(currentPage, true) // Desplaza con animación

            // Reprograma este runnable solo si la vista del fragmento aún existe.
            // Esto previene que el handler intente ejecutar tareas después de que onDestroyView()
            // haya sido llamado, lo cual podría causar crashes o comportamiento inesperado.
            if (view != null && ::handler.isInitialized) {
                handler.postDelayed(this, 3000)
            }
        }
    }

    /**
     * Se llama cuando la vista asociada con el fragmento está siendo destruida.
     * Limpia los recursos para evitar fugas de memoria, como detener el [handler]
     * y desregistrar el [viewPagerCallback].
     */
    override fun onDestroyView() {
        super.onDestroyView()
        // Detiene todas las tareas pendientes del handler para evitar fugas y operaciones en vistas destruidas.
        if (::handler.isInitialized) {
            handler.removeCallbacksAndMessages(null)
            // Desregistra el callback del ViewPager solo si el adapter no es nulo.
            // Esto evita un posible crash si el ViewPager o su adapter no se inicializaron correctamente.
            if (viewPager.adapter != null) {
                viewPager.unregisterOnPageChangeCallback(viewPagerCallback)
            }
        }
    }

    /**
     * Intenta abrir una URL en un navegador web o la aplicación correspondiente.
     * Muestra un [Toast] y registra un error si no se puede encontrar una actividad
     * para manejar el [Intent].
     *
     * @param url La URL a abrir (debe ser una URL completa, ej: "https://www.example.com").
     * @param appName Nombre de la aplicación o sitio web para mostrar en mensajes de error.
     */
    private fun openUrl(url: String, appName: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(requireContext(), "No se pudo abrir $appName.", Toast.LENGTH_LONG).show()
            Log.e("OpenUrlError", "Error al abrir $url: ${e.message}")
        }
    }
}