package com.example.conectatec

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioGroup
import androidx.fragment.app.Fragment


import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions


class RutasFragment : Fragment(), OnMapReadyCallback {
    private lateinit var mMap: GoogleMap

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.activity_mapa, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val mapFragment = childFragmentManager
            .findFragmentById(R.id.mapa) as? SupportMapFragment

        if (mapFragment == null) {
            val newMapFragment = SupportMapFragment.newInstance()
            childFragmentManager.beginTransaction()
                .replace(R.id.mapa, newMapFragment)
                .commit()
            newMapFragment.getMapAsync(this)
        } else {
            mapFragment.getMapAsync(this)
        }
        // Obtener el RadioGroup
        val grupo: RadioGroup = view.findViewById(R.id.Grupo)



        // Manejar los cambios de selección
        grupo.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.zinancantepec -> cambiarUbicacion(LatLng(19.2802083, -99.694791666667), " Estación: Zinancantepec")
                R.id.toluca -> cambiarUbicacion(LatLng(19.270283333333, -99.641791666667), "Estación: Toluca Centro")
                R.id.metepec-> cambiarUbicacion(LatLng(19.2775611, -99.573475), "Estación: Metepec")
                R.id.lerma-> cambiarUbicacion(LatLng(19.2784889, -99.514975), "Estación: Lerma")
                R.id.santaFe-> cambiarUbicacion(LatLng( 19.364475, -99.2682), "Estación: Santa Fe")
            }
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        cambiarUbicacion(LatLng( 19.2802083, -99.694791666667), "Estación: Zinancantepec") // Ubicación por defecto
    }

    private fun cambiarUbicacion(ubicacion: LatLng, titulo: String) {
        mMap.clear() // Limpia marcadores anteriores
        mMap.addMarker(MarkerOptions().position(ubicacion).title(titulo))
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(ubicacion, 15f))
    }
}


