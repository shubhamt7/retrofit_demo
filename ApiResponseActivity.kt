/*
Activity displaying the results in recyclerView
*/


package com.paytm.business.notificationsettings.activity

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.business.merchant_payments.model.primary.OrderList
import com.business.merchant_payments.notificationsettings.model.OrderListItemModel
import com.business.merchant_payments.notificationsettings.viewmodel.OrderListViewModel
import com.paytm.business.R
import com.paytm.business.databinding.ActivityApiResponseBinding
import com.paytm.business.notificationsettings.adapters.model.OrderListAdapter

class ApiResponseActivity : AppCompatActivity() {
    private lateinit var binding : ActivityApiResponseBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_api_response)

        var orderList = OrderListViewModel.orderList!!

        Log.d("API_RESULT", orderList.toString())

        val adapter = OrderListAdapter(this, orderList)
        val recyclerView = binding.orderlistRecyclerView
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(this)
    }
}