package com.example.wierdesol

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class StatusAdapter(var statusIndicators: List<StatusIndicator>) :
    RecyclerView.Adapter<StatusAdapter.StatusViewHolder>() {

    class StatusViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nameTextView: TextView = itemView.findViewById(R.id.statusName)
        val valueTextView: TextView = itemView.findViewById(R.id.statusValue) // Changed from statusCheckbox
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StatusViewHolder {
        val itemView = LayoutInflater.from(parent.context).inflate(
            R.layout.status_item, parent, false
        )
        return StatusViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: StatusViewHolder, position: Int) {
        val currentStatus = statusIndicators[position]
        holder.nameTextView.text = currentStatus.name
        holder.valueTextView.text = currentStatus.rawValue // Display the raw value
    }

    override fun getItemCount() = statusIndicators.size
}