package com.nsizintsev.doorbell.ui.main

import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.bumptech.glide.Glide
import com.nsizintsev.doorbell.R
import com.nsizintsev.doorbell.common.entity.DoorbellCallViewEntity
import kotlinx.android.synthetic.main.list_item_call.view.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

/**
 * Created by nsizintsev on 3/15/2018.
 */

class CallsAdapter(private val layoutInflater: LayoutInflater) : RecyclerView.Adapter<CallsAdapter.CallViewHolder>() {

    private val items = ArrayList<DoorbellCallViewEntity>()

    private val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)

    fun setItems(newItems: List<DoorbellCallViewEntity>?) {
        items.clear()
        if (newItems != null) {
            items.addAll(newItems)
        }
        notifyDataSetChanged()
    }

    fun addItem(position: Int, item: DoorbellCallViewEntity) {
        items.add(position, item)
        notifyItemInserted(position)
    }

    fun updateItem(position: Int, item: DoorbellCallViewEntity) {
        items.set(position, item)
        notifyItemChanged(position)
    }

    fun removeItemAt(position: Int) {
        items.removeAt(position)
        notifyItemRemoved(position)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CallViewHolder {
        return CallViewHolder(layoutInflater.inflate(R.layout.list_item_call, parent, false))
    }

    override fun onBindViewHolder(holder: CallViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int {
        return items.size
    }

    inner class CallViewHolder(viewItem: View) : RecyclerView.ViewHolder(viewItem) {

        private lateinit var item: DoorbellCallViewEntity

        fun bind(item: DoorbellCallViewEntity) {
            this@CallViewHolder.item = item

            Glide.with(itemView)
                    .load(item.fileUri)
                    .into(itemView.imageView)

            itemView.date.text = timestamp.format(item.date)
        }

    }

}
