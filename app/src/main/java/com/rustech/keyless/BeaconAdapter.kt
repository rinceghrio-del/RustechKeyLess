package com.rustech.keyless

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

class BeaconAdapter : RecyclerView.Adapter<BeaconAdapter.VH>() {

    private val items = mutableListOf<IBeacon>()

    fun submit(list: List<IBeacon>) {
        items.clear()
        items.addAll(list.sortedByDescending { it.rssi })
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, position: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_beacon, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.tvBeaconTitle)
        private val subtitle: TextView = itemView.findViewById(R.id.tvBeaconSubtitle)
        private val rssiView: TextView = itemView.findViewById(R.id.tvBeaconRssi)

        fun bind(beacon: IBeacon) {
            title.text = "Major:${beacon.major}  Minor:${beacon.minor}"
            val dist = beacon.estimatedDistanceMeters()
            val distText = if (dist < 0) "—" else String.format(Locale.US, "%.1f m", dist)
            subtitle.text = "${beacon.uuid.take(8)}…  ·  $distText  ·  ${beacon.address}"
            rssiView.text = "${beacon.rssi} dBm"
        }
    }
}
