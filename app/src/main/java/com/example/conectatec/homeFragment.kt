package com.example.conectatec

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2

class homeFragment : Fragment() {
    private lateinit var viewPager: ViewPager2
    private lateinit var handler: Handler
    private var currentPage = 0

    private val imagenes = listOf(
        R.drawable.metro,
        R.drawable.prueba,

    )
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewPager = view.findViewById(R.id.imagenesSlide)
        viewPager.adapter = ViewPagerAdapter(imagenes)

        // Iniciar el auto-slide
        handler = Handler(Looper.getMainLooper())
        startAutoSlide()

        // Detener auto-slide cuando el usuario interactúa
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                currentPage = position
                restartAutoSlide()
            }
        })
    }

    private fun startAutoSlide() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                if (currentPage == imagenes.size - 1) {
                    currentPage = 0 // Regresar a la primera imagen
                } else {
                    currentPage++
                }
                viewPager.setCurrentItem(currentPage, true)
                handler.postDelayed(this, 3000) // Cambia cada 3 segundos
            }
        }, 3000)
    }

    private fun restartAutoSlide() {
        handler.removeCallbacksAndMessages(null) // Detiene el auto-slide temporalmente
        startAutoSlide() // Reinicia el auto-slide después de la interacción
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacksAndMessages(null) // Evita memory leaks
    }

}