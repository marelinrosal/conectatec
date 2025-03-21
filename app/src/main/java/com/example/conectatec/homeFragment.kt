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
    private var currentPage = 1 // Inicia en 1 (segunda imagen: la "real" primera)

    private val imagenesOriginales = listOf(
        R.drawable.aviso4,
        R.drawable.aviso5,
        R.drawable.aviso1,
        R.drawable.aviso2,
        R.drawable.aviso6,
        R.drawable.aviso7,
    )

    private val imagenesCarrusel: List<Int>
        get() = listOf(imagenesOriginales.last()) + imagenesOriginales + listOf(imagenesOriginales.first())

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.home, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewPager = view.findViewById(R.id.imagenesSlide)
        viewPager.adapter = ViewPagerAdapter(imagenesCarrusel)
        viewPager.setCurrentItem(currentPage, false)

        handler = Handler(Looper.getMainLooper())
        startAutoSlide()

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                currentPage = position
                restartAutoSlide()
            }

            override fun onPageScrollStateChanged(state: Int) {
                super.onPageScrollStateChanged(state)
                if (state == ViewPager2.SCROLL_STATE_IDLE) {
                    if (currentPage == 0) {
                        currentPage = imagenesOriginales.size
                        viewPager.setCurrentItem(currentPage, false)
                    } else if (currentPage == imagenesCarrusel.size - 1) {
                        currentPage = 1
                        viewPager.setCurrentItem(currentPage, false)
                    }
                }
            }
        })
    }

    private fun startAutoSlide() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                currentPage++
                viewPager.setCurrentItem(currentPage, true)
                handler.postDelayed(this, 3000)
            }
        }, 3000)
    }

    private fun restartAutoSlide() {
        handler.removeCallbacksAndMessages(null)
        startAutoSlide()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacksAndMessages(null)
    }
}
