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
// import com.example.conectatec.util.Constants // <-- LÍNEA ANTERIOR COMENTADA/ELIMINADA
import com.example.conectatec.Constants // <-- CAMBIO AQUÍ: Importar desde el paquete raíz

class homeFragment : Fragment() {
    private lateinit var viewPager: ViewPager2
    private lateinit var handler: Handler
    private lateinit var sharedPreferences: SharedPreferences
    private var currentPage = 1

    private val imagenesOriginales = listOf(
        R.drawable.aviso4, R.drawable.aviso5, R.drawable.aviso1,
        R.drawable.aviso2, R.drawable.aviso6, R.drawable.aviso7,
    )
    private val imagenesCarrusel: List<Int>
        get() = if (imagenesOriginales.isNotEmpty()) {
            listOf(imagenesOriginales.last()) + imagenesOriginales + listOf(imagenesOriginales.first())
        } else {
            emptyList()
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        sharedPreferences = requireActivity().getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE) // Usa constante importada
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val loggedInUserId = getLoggedInUserId()
        if (loggedInUserId != null) {
            Log.d("SessionData", "Usuario logueado con ID: $loggedInUserId")
            Toast.makeText(requireContext(), "ID Usuario: $loggedInUserId", Toast.LENGTH_SHORT).show()
        } else {
            Log.w("SessionData", "ID de usuario no encontrado. Cerrando sesión.")
            logout()
            return
        }

        val lblCerrar = view.findViewById<TextView>(R.id.lblcerrar)
        lblCerrar.setOnClickListener {
            logout()
        }

        viewPager = view.findViewById(R.id.imagenesSlide)
        if (imagenesCarrusel.isNotEmpty()) {
            viewPager.adapter = ViewPagerAdapter(imagenesCarrusel)
            viewPager.setCurrentItem(currentPage, false)
            handler = Handler(Looper.getMainLooper())
            startAutoSlide()
            viewPager.registerOnPageChangeCallback(viewPagerCallback)
        } else {
            Log.w("HomeFragment", "Lista de imágenes para carrusel vacía.")
            viewPager.visibility = View.GONE
        }

        val btnPagos: Button = view.findViewById(R.id.BotonCompra)
        btnPagos.setOnClickListener {
            val intent = Intent(requireContext(), SistemaPagosActivity::class.java)
            startActivity(intent)
        }

        val grupo: RadioGroup = view.findViewById(R.id.grupoBotones)
        grupo.setOnCheckedChangeListener { rg, checkedId ->
            Handler(Looper.getMainLooper()).postDelayed({ rg.clearCheck() }, 200)
            when (checkedId) {
                R.id.Facebook -> openUrl("https://www.facebook.com/trenelinsurgente", "Facebook")
                R.id.X -> openUrl("https://x.com/TrenInsurgente", "X")
                R.id.tikTok -> openUrl("https://www.tiktok.com/@trenelinsurgente", "TikTok")
                R.id.instagram -> openUrl("https://www.instagram.com/trenelinsurgente/", "Instagram")
                R.id.youtube -> openUrl("https://www.youtube.com/@trenelinsurgente", "YouTube")
            }
        }
    }

    private val viewPagerCallback = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            super.onPageSelected(position)
            if (::handler.isInitialized) {
                restartAutoSlide()
            }
        }

        override fun onPageScrollStateChanged(state: Int) {
            super.onPageScrollStateChanged(state)
            if (state == ViewPager2.SCROLL_STATE_IDLE && imagenesCarrusel.isNotEmpty()) {
                val lastRealPage = imagenesCarrusel.size - 2
                val firstRealPage = 1
                when (viewPager.currentItem) {
                    0 -> {
                        currentPage = lastRealPage
                        viewPager.setCurrentItem(currentPage, false)
                    }
                    imagenesCarrusel.size - 1 -> {
                        currentPage = firstRealPage
                        viewPager.setCurrentItem(currentPage, false)
                    }
                    else -> {
                        currentPage = viewPager.currentItem
                    }
                }
            }
        }
    }

    private fun getLoggedInUserId(): String? {
        return sharedPreferences.getString(Constants.KEY_USER_ID, null) // Usa constante importada
    }

    private fun logout() {
        if (::handler.isInitialized) {
            handler.removeCallbacksAndMessages(null)
        }

        val editor = sharedPreferences.edit()
        editor.putBoolean(Constants.KEY_IS_LOGGED_IN, false) // Usa constante importada
        editor.remove(Constants.KEY_USER_ID)               // Usa constante importada
        editor.apply()

        Toast.makeText(requireContext(), "Sesión cerrada", Toast.LENGTH_SHORT).show()

        val intent = Intent(requireContext(), Login_activity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        requireActivity().finish()
    }

    private fun startAutoSlide() {
        if (!::handler.isInitialized || imagenesCarrusel.isEmpty()) return
        handler.postDelayed(slideRunnable, 3000)
    }

    private fun restartAutoSlide() {
        if (::handler.isInitialized) {
            handler.removeCallbacks(slideRunnable)
            handler.postDelayed(slideRunnable, 3000)
        }
    }

    private val slideRunnable = object : Runnable {
        override fun run() {
            if (view == null || viewPager.adapter == null || viewPager.adapter?.itemCount == 0) {
                handler.removeCallbacks(this)
                return
            }
            currentPage++
            viewPager.setCurrentItem(currentPage, true)
            if (view != null) { // Reprogramar solo si la vista existe
                handler.postDelayed(this, 3000)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (::handler.isInitialized) {
            handler.removeCallbacksAndMessages(null)
            // Solo desregistrar si el adapter no es nulo (evita crash si no se inicializó)
            if (viewPager.adapter != null) {
                viewPager.unregisterOnPageChangeCallback(viewPagerCallback)
            }
        }
    }

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