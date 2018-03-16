package com.nsizintsev.doorbell.ui.main

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.LinearLayoutManager
import com.nsizintsev.doorbell.R
import com.nsizintsev.doorbell.common.entity.ChangeItemRequest
import com.nsizintsev.doorbell.common.entity.DoorbellCallViewEntity
import com.nsizintsev.doorbell.viewmodel.GetCallsViewModel

import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.content_main.*

class MainActivity : AppCompatActivity() {

    private lateinit var model: GetCallsViewModel

    private lateinit var callsAdapter: CallsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        model = ViewModelProviders.of(this).get(GetCallsViewModel::class.java)

        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        callsAdapter = CallsAdapter(layoutInflater)

        recyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        recyclerView.adapter = callsAdapter
        recyclerView.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))

        model.calls.observe(this, Observer<MutableList<DoorbellCallViewEntity>> { t ->
            callsAdapter.setItems(t)
        })

        model.getCallUpdates().observe(this, Observer<GetCallsViewModel.UpdateRequest> { t ->
            if (t != null) {
                when (t.action) {
                    ChangeItemRequest.ADDED -> callsAdapter.addItem(t.position, t.item)
                    ChangeItemRequest.MODIFIED -> callsAdapter.updateItem(t.position, t.item)
                    ChangeItemRequest.REMOVED -> callsAdapter.removeItemAt(t.position)
                }
            }
        })

    }

}
