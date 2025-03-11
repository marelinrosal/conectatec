package com.example.conectatec

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions


class Mapa_activity : Fragment(), OnMapReadyCallback {
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
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        val location = LatLng(19.2534, -99.5721)
        mMap.addMarker(MarkerOptions().position(location).title("¡Aquí nos encontramos!"))
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(location, 15f))
    }
}


