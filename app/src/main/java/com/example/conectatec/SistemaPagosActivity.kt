package com.example.conectatec

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.*
import android.view.GestureDetector
import android.view.MenuItem
import android.view.MotionEvent
import android.webkit.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.stripe.android.PaymentConfiguration

class SistemaPagosActivity : AppCompatActivity() {
    private lateinit var btnPagar: Button
    private var urlSeleccionada: String? = null
    private val stripePublishableKey = "pk_test_TU_CLAVE_PUBLICA" // Reemplaza con tu clave real
    private lateinit var webView: WebView
    private val handler = Handler(Looper.getMainLooper()) // Handler para recargar WebView
    private var usuarioInteractuando = false // Variable para detectar interacción

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sistema_pagos)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar2)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Home"

        PaymentConfiguration.init(this, stripePublishableKey)

        btnPagar = findViewById(R.id.btnPagar)
        btnPagar.isEnabled = false

        val grupoInterurbano = findViewById<RadioGroup>(R.id.grupoInterurbano)
        val grupoUrbano = findViewById<RadioGroup>(R.id.grupoUrbano)

        val urlsPagos = mapOf(
            R.id.diaInterurbano to "https://buy.stripe.com/test_9AQeZ11m86jM5vacMO",
            R.id.semanaInterurbano to "https://buy.stripe.com/test_bIYg358OAeQif5KdQU",
            R.id.mesInterurbano to "https://buy.stripe.com/test_28o18b5CoaA2bTyeV0",
            R.id.viajeInterurbano to "https://buy.stripe.com/test_5kAg35d4Q0ZsbTy9AH",
            R.id.diaUrbano to "https://buy.stripe.com/test_fZe3gj2qceQi7DibIJ",
            R.id.semanaUrbano to "https://buy.stripe.com/test_00g9EHfcYdMe6ze4gj",
            R.id.mesUrbano to "https://buy.stripe.com/test_aEU7wzc0M37AaPuaEJ",
            R.id.viajeUrbano to "https://buy.stripe.com/test_28o7wzaWIeQi1eU5ks"
        )

        grupoInterurbano.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId != -1) {
                grupoUrbano.clearCheck()
                urlSeleccionada = urlsPagos[checkedId]
                btnPagar.isEnabled = true
            }
        }

        grupoUrbano.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId != -1) {
                grupoInterurbano.clearCheck()
                urlSeleccionada = urlsPagos[checkedId]
                btnPagar.isEnabled = true
            }
        }

        btnPagar.setOnClickListener {
            if (urlSeleccionada != null) {
                mostrarBottomSheet(urlSeleccionada!!)
            } else {
                Toast.makeText(this, "Selecciona una opción primero", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun mostrarBottomSheet(url: String) {
        val bottomSheetDialog = BottomSheetDialog(this)
        webView = WebView(this)

        // Limpiar caché para evitar errores
        webView.clearCache(true)
        webView.clearHistory()

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true

        var pagoExitoso = false // Para rastrear si se completó el pago

        // ✅ Detector de gestos para identificar cualquier interacción (toques, scroll, teclado)
        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean {
                usuarioInteractuando = true
                return super.onDown(e)
            }

            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean {
                usuarioInteractuando = true
                return super.onScroll(e1, e2, distanceX, distanceY)
            }

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                usuarioInteractuando = true
                return super.onSingleTapUp(e)
            }
        })


        webView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            false
        }


        webView.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                usuarioInteractuando = true
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)

                if (url != null) {
                    if (url.contains("pay", ignoreCase = true)) {
                        pagoExitoso = true

                        Toast.makeText(this@SistemaPagosActivity, "Pago exitoso. Redirigiendo al Home...", Toast.LENGTH_LONG).show()

                        bottomSheetDialog.dismiss()
                        redirigirAHome()
                    }
                }
            }
        }

        bottomSheetDialog.setOnDismissListener {
            if (!pagoExitoso) { // Si se cerró sin pago exitoso, mostrar error

                Toast.makeText(this@SistemaPagosActivity, "Transacción fallida. Intenta de nuevo.", Toast.LENGTH_LONG).show()
            }
        }

        webView.loadUrl(url)
        bottomSheetDialog.setContentView(webView)
        bottomSheetDialog.show()

        iniciarRecargaAutomatica(bottomSheetDialog)
    }



    private fun iniciarRecargaAutomatica(bottomSheetDialog: BottomSheetDialog) {
        handler.postDelayed(object : Runnable {
            override fun run() {
                if (bottomSheetDialog.isShowing) {
                    if (!usuarioInteractuando) { //  Solo recargar si el usuario NO está interactuando
                        println("Recargando WebView...")
                        webView.reload()
                    }
                    usuarioInteractuando = false //  Restablecer la interacción
                    handler.postDelayed(this, 7000)
                }
            }
        }, 7000)
    }

    private fun redirigirAHome() {
        val intent = Intent(this, InicioActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        finish()
    }



    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                redirigirAHome()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
