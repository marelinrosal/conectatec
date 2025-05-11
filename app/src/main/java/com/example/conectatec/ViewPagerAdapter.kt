package com.example.conectatec

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView

/**
 * Adaptador para un [RecyclerView] (o [androidx.viewpager2.widget.ViewPager2])
 * diseñado para mostrar una lista de imágenes.
 * <p>
 * Cada ítem en el adaptador infla un layout `R.layout.item_pager` que se espera
 * contenga un [ImageView] con el ID `R.id.imageView`. La imagen correspondiente
 * de la lista `imagenes` se carga en este [ImageView].
 * </p>
 *
 * @property imagenes Una lista de identificadores de recursos drawable ([Int])
 *                    que representan las imágenes a mostrar en el carrusel o lista.
 *
 * @author Ortiz Gallegos Starenka Susana
 * @author Salgado Rojas Marelin Iral
 * @author Orozco Reyes Hiram
 * @author Ortiz Ceballos Jorge
 * @version 1.15
 * @since 22 Marzo 2025
 * @see R.layout.item_pager
 * @see R.id.imageView
 */
class ViewPagerAdapter(private val imagenes: List<Int>) :
    RecyclerView.Adapter<ViewPagerAdapter.ViewHolder>() {

    /**
     * ViewHolder que contiene la vista de un solo ítem del carrusel/lista.
     * <p>
     * Mantiene una referencia al [ImageView] dentro del layout del ítem
     * para poder establecer la imagen correspondiente.
     * </p>
     *
     * @param itemView La vista raíz del layout de un ítem (inflado desde `R.layout.item_pager`).
     * @property imageView El [ImageView] dentro del `itemView` donde se mostrará la imagen.
     */
    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // Referencia al ImageView dentro del layout del ítem (item_pager.xml).
        val imageView: ImageView = itemView.findViewById(R.id.imageView)
    }

    /**
     * Se llama cuando RecyclerView necesita un nuevo [ViewHolder] del tipo dado para representar un ítem.
     * <p>
     * Infla el layout `R.layout.item_pager` y crea una instancia de [ViewHolder] con él.
     * </p>
     *
     * @param parent El [ViewGroup] al cual se añadirá la nueva vista después de ser vinculada
     *               a una posición del adaptador.
     * @param viewType El tipo de vista de la nueva vista (no se usa en este adaptador simple).
     * @return Un nuevo [ViewHolder] que contiene una vista del tipo de vista dado.
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        // Infla el layout XML (R.layout.item_pager) para cada ítem del ViewPager/RecyclerView.
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pager, parent, false)
        // Crea y devuelve una nueva instancia de ViewHolder.
        return ViewHolder(view)
    }

    /**
     * Se llama por RecyclerView para mostrar los datos en la posición especificada.
     * <p>
     * Este método actualiza el contenido del `itemView` del [ViewHolder] para reflejar
     * el ítem en la posición dada. Establece el recurso de imagen en el [ImageView]
     * del ViewHolder y ajusta su `scaleType` a `FIT_XY`.
     * </p>
     *
     * @param holder El [ViewHolder] que debe ser actualizado para representar el contenido del
     *               ítem en la posición dada en el conjunto de datos.
     * @param position La posición del ítem dentro del conjunto de datos del adaptador.
     */
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        // Obtiene el ID del recurso drawable de la imagen en la posición actual.
        val imagenResId = imagenes[position]
        // Establece la imagen en el ImageView del ViewHolder.
        holder.imageView.setImageResource(imagenResId)
        // Establece el tipo de escala de la imagen para que se ajuste a las dimensiones del ImageView.
        // FIT_XY puede distorsionar la imagen si las proporciones no coinciden.
        // Considerar usar FIT_CENTER o CENTER_CROP si se prefiere mantener la proporción.
        holder.imageView.scaleType = ImageView.ScaleType.FIT_XY
    }

    /**
     * Devuelve el número total de ítems en el conjunto de datos que tiene el adaptador.
     *
     * @return El número total de imágenes en la lista `imagenes`.
     */
    override fun getItemCount(): Int {
        return imagenes.size
    }
}