package com.example.conectatec

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import androidx.fragment.app.Fragment

class HorariosFragment : Fragment() {

    private lateinit var lunesViernesLayout: LinearLayout
    private lateinit var sabadoLayout: LinearLayout
    private lateinit var domingoLayout: LinearLayout
    private lateinit var buttonLunesViernes: Button
    private lateinit var buttonSabado: Button
    private lateinit var buttonDomingo: Button

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_horarios, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize UI components
        lunesViernesLayout = view.findViewById(R.id.layout_lunes_viernes)
        sabadoLayout = view.findViewById(R.id.layout_sabado)
        domingoLayout = view.findViewById(R.id.layout_domingo)
        buttonLunesViernes = view.findViewById(R.id.lunes_viernes)
        buttonSabado = view.findViewById(R.id.btn_sabado)
        buttonDomingo = view.findViewById(R.id.btn_domingo)

        // Set default state - Show only Lunes a Viernes tables
        showLunesViernes()

        // Set click listeners for buttons
        buttonLunesViernes.setOnClickListener {
            showLunesViernes()
        }

        buttonSabado.setOnClickListener {
            showSabado()
        }

        buttonDomingo.setOnClickListener {
            showDomingo()
        }
    }

    private fun showLunesViernes() {
        lunesViernesLayout.visibility = View.VISIBLE
        sabadoLayout.visibility = View.GONE
        domingoLayout.visibility = View.GONE

        // Update button states to indicate selection
        updateButtonStates(buttonLunesViernes)
    }

    private fun showSabado() {
        lunesViernesLayout.visibility = View.GONE
        sabadoLayout.visibility = View.VISIBLE
        domingoLayout.visibility = View.GONE

        // Update button states to indicate selection
        updateButtonStates(buttonSabado)
    }

    private fun showDomingo() {
        lunesViernesLayout.visibility = View.GONE
        sabadoLayout.visibility = View.GONE
        domingoLayout.visibility = View.VISIBLE

        // Update button states to indicate selection
        updateButtonStates(buttonDomingo)
    }

    private fun updateButtonStates(selectedButton: Button) {
        // Reset all buttons
        buttonLunesViernes.setBackgroundResource(android.R.drawable.btn_default)
        buttonSabado.setBackgroundResource(android.R.drawable.btn_default)
        buttonDomingo.setBackgroundResource(android.R.drawable.btn_default)

        // Reset text color
        buttonLunesViernes.setTextColor(resources.getColor(android.R.color.black))
        buttonSabado.setTextColor(resources.getColor(android.R.color.black))
        buttonDomingo.setTextColor(resources.getColor(android.R.color.black))

        // Highlight selected button
        selectedButton.setBackgroundResource(R.color.orange)
        selectedButton.setTextColor(resources.getColor(R.color.white))
    }
}