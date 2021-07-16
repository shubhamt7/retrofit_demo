package com.business.merchant_payments.notificationsettings.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.business.merchant_payments.notificationsettings.model.OrderListItemModel


object OrderListViewModel : ViewModel() {

    private var _orderList: List<OrderListItemModel>? = null
    var orderList : List<OrderListItemModel>?
    get() = _orderList

    set(value){
        _orderList = value
    }
}